package org.fourz.RVNKQuests.quest;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.event.QuestCompleteEvent;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.IQuestRepository;
import org.fourz.RVNKQuests.data.dto.QuestDTO;
import org.fourz.RVNKQuests.service.IQuestService;
import org.fourz.rvnkcore.util.log.LogManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.fourz.RVNKQuests.util.PlayerAwareListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Core manager for quest registration, state management, and event handling.
 *
 * <p>This class manages the full quest lifecycle:</p>
 * <ol>
 *   <li>Registration and initialization of quest instances</li>
 *   <li>Dynamic event listener registration based on quest state</li>
 *   <li>Per-player listener management</li>
 *   <li>Scheduled task management for quest-related activities</li>
 *   <li>Cleanup and state validation</li>
 * </ol>
 *
 * <p>The manager uses a state-based event listener model where each quest
 * provides different listeners based on its current state. For per-player
 * quests, listeners are registered once but check player-specific state
 * within their event handlers.</p>
 */
public class QuestManager implements IQuestService {
    private final RVNKQuests plugin;
    private final LogManager logger;
    private final Map<String, Quest> quests = new HashMap<>();
    private final Map<Quest, List<Listener>> activeListeners = new HashMap<>();
    private final Map<String, Integer> scheduledTasks = new HashMap<>();

    // Track which players are actively engaged with each quest (for listener optimization)
    private final Map<String, Set<UUID>> activePlayersByQuest = new ConcurrentHashMap<>();

    // Idempotency guard: tracks in-flight completion requests (playerId:questId keys)
    // Prevents double-reward from concurrent events firing before the first write completes.
    private final Set<String> completionsInProgress = ConcurrentHashMap.newKeySet();

    public QuestManager(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }

    /**
     * Updates the debug level for this manager
     * @param level New log level
     */
    public void updateDebugLevel(Level level) {
        logger.setLogLevel(level);
        logger.debug("QuestManager log level updated to: " + level.getName());
    }

    public void registerQuest(Quest quest) {
        if (quest == null) {
            logger.warning("Attempted to register null quest");
            return;
        }

        String questId = quest.getId();
        if (questId == null || questId.isEmpty()) {
            logger.warning("Attempted to register quest with null or empty ID");
            return;
        }

        if (quests.containsKey(questId)) {
            logger.warning("Quest already registered with ID: " + questId);
            return;
        }

        logger.debug("Registering quest: " + questId);
        quests.put(questId, quest);
        activePlayersByQuest.put(questId, ConcurrentHashMap.newKeySet());

        try {
            quest.initialize();
            logger.debug("Quest initialized: " + questId);
        } catch (Exception e) {
            logger.error("Failed to initialize quest: " + questId, e);
        }

        try {
            updateQuestListeners(quest);
            logger.debug("Quest registered and listeners initialized: " + questId);
        } catch (Exception e) {
            logger.error("Failed to register listeners for quest: " + questId, e);
        }
    }

    @Override
    public Optional<Quest> getQuest(String questId) {
        Quest quest = quests.get(questId);
        logger.debug("Quest lookup for ID '" + questId + "': " + (quest != null ? "found" : "not found"));
        return Optional.ofNullable(quest);
    }

    public void initializeQuests() {
        logger.debug("Beginning quest initialization");

        try {
            // Load quests asynchronously — repository.findAll() is dispatched to a pool thread
            // and registrations are scheduled back on the main thread. Quests are available
            // before the first player tick because onEnable returns before the world opens.
            loadQuestsFromRepository();
        } catch (Exception e) {
            logger.error("Error during quest initialization", e);
        }
    }

    /**
     * Loads quest definitions from the IQuestRepository and registers them
     * as DataDrivenQuest instances.
     *
     * <p>The repository call is fully async — no blocking on the main thread.
     * Registration is dispatched back to the main thread via {@code runTask}
     * so Bukkit event handlers are registered safely.</p>
     */
    public void loadQuestsFromRepository() {
        IQuestRepository repository = plugin.getQuestRepository();
        if (repository == null) {
            logger.debug("No quest repository available — skipping data-driven quest loading");
            return;
        }

        repository.findAll()
            .thenAccept(definitions -> {
                // Back on the main thread so Bukkit listener registration is thread-safe
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        int loaded = 0;

                        for (QuestDTO definition : definitions) {
                            if (quests.containsKey(definition.questId())) {
                                logger.debug("Skipping quest " + definition.questId() + " — already registered");
                                continue;
                            }

                            if (!plugin.getConfigManager().isQuestEnabled(definition.questId())) {
                                logger.debug("Skipping disabled quest: " + definition.questId());
                                continue;
                            }

                            DataDrivenQuest quest = new DataDrivenQuest(plugin, definition);
                            registerQuest(quest);
                            loaded++;
                        }

                        logger.info("Loaded " + loaded + " data-driven quest(s) from repository" +
                            (repository.isInFallbackMode() ? " (YAML fallback)" : " (database)"));

                    } catch (Exception e) {
                        logger.error("Failed to register quests from repository", e);
                    }
                });
            })
            .exceptionally(ex -> {
                logger.warning("Failed to load quests from repository: " + ex.getMessage());
                return null;
            });
    }

    /**
     * Hot-reloads a single quest definition from the repository.
     * Unregisters the existing quest and re-registers with updated definition.
     *
     * @param questId The quest ID to reload
     * @return CompletableFuture that completes with true if reload succeeded
     */
    public CompletableFuture<Boolean> reloadQuest(String questId) {
        IQuestRepository repository = plugin.getQuestRepository();
        if (repository == null) {
            return CompletableFuture.completedFuture(false);
        }

        return repository.findById(questId).thenApply(optDef -> {
            if (optDef.isEmpty()) {
                logger.warning("Quest not found in repository for reload: " + questId);
                return false;
            }

            // Unregister existing quest (skip if not yet registered — e.g. first import)
            if (quests.containsKey(questId)) {
                unregisterQuest(questId);
            }

            // Re-register with updated definition
            DataDrivenQuest quest = new DataDrivenQuest(plugin, optDef.get());
            registerQuest(quest);

            logger.info("Hot-reloaded quest: " + questId);
            return true;
        });
    }

    public void cleanupQuests() {
        logger.debug("Starting quest cleanup process");

        // Cancel all scheduled tasks
        logger.debug("Cancelling " + scheduledTasks.size() + " scheduled tasks");
        for (String taskId : new ArrayList<>(scheduledTasks.keySet())) {
            cancelTask(taskId);
        }
        scheduledTasks.clear();

        // Unregister all listeners first
        activeListeners.forEach((quest, listeners) -> {
            logger.debug("Unregistering " + listeners.size() + " listeners for quest: " + quest.getId());
            listeners.forEach(HandlerList::unregisterAll);
        });
        activeListeners.clear();

        // Clear active players tracking
        activePlayersByQuest.clear();

        // Clean up quests
        logger.debug("Cleaning up " + quests.size() + " quests");
        quests.values().forEach(quest -> {
            logger.debug("Cleaning up quest: " + quest.getId());
            quest.cleanup();
        });
        quests.clear();
        logger.debug("Quest cleanup complete");
    }

    /**
     * Fully resets the quest system by cleaning up all existing quests
     * and reinitializing them. This simulates a plugin restart.
     *
     * <p>Note: In-memory caches are cleared but persisted data remains.
     * Players will have their progress loaded from storage on next join.</p>
     */
    public void resetQuests() {
        logger.debug("Resetting all quests");

        // First clean up all existing quests
        cleanupQuests();

        // Then reinitialize quests
        initializeQuests();

        logger.debug("Quest reset complete");
    }

    public void registerQuestListeners(Quest quest, Listener... listeners) {
        logger.debug("Registering " + listeners.length + " listeners for quest: " + quest.getId());
        for (Listener listener : listeners) {
            logger.debug("Registering listener: " + listener.getClass().getSimpleName());
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }
    }

    public void unregisterQuestListeners(Listener... listeners) {
        logger.debug("Unregistering " + listeners.length + " listeners");
        for (Listener listener : listeners) {
            logger.debug("Unregistering listener: " + listener.getClass().getSimpleName());
            HandlerList.unregisterAll(listener);
        }
    }

    /**
     * Updates the event listeners for a quest.
     *
     * <p>For per-player quests, this registers listeners that handle events
     * for all players. Individual event handlers should check player-specific
     * state using {@link Quest#getStateForPlayer(Player)}.</p>
     *
     * @param quest The quest to update listeners for
     */
    public void updateQuestListeners(Quest quest) {
        if (quest == null) {
            logger.warning("Attempted to update listeners for null quest");
            return;
        }

        // For per-player quests, we register listeners for the most "advanced" state
        // that any active player might be in. Individual handlers check per-player state.
        // For simplicity, we register listeners for all possible states initially.
        logger.debug("Updating listeners for quest: " + quest.getId());

        // Clean up existing listeners for this quest
        if (activeListeners.containsKey(quest)) {
            List<Listener> oldListeners = activeListeners.get(quest);
            logger.debug("Removing " + oldListeners.size() + " existing listeners");
            unregisterQuestListeners(oldListeners.toArray(new Listener[0]));
            oldListeners.clear();
        }

        // Register listeners for all states that have active listeners
        List<Listener> allListeners = new ArrayList<>();
        for (QuestState state : QuestState.values()) {
            try {
                List<Listener> stateListeners = quest.createListenersForState(state);
                if (stateListeners != null && !stateListeners.isEmpty()) {
                    allListeners.addAll(stateListeners);
                }
            } catch (Exception e) {
                logger.error("Error creating listeners for quest " + quest.getId() + " state " + state, e);
            }
        }

        // Register all listeners
        logger.debug("Registering " + allListeners.size() + " listeners for quest: " + quest.getId());
        for (Listener listener : allListeners) {
            if (listener == null) {
                logger.warning("Null listener in list for quest: " + quest.getId());
                continue;
            }

            try {
                logger.debug("Registering listener: " + listener.getClass().getSimpleName());
                plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            } catch (Exception e) {
                logger.error("Failed to register listener: " + listener.getClass().getSimpleName(), e);
            }
        }

        activeListeners.put(quest, allListeners);
        logger.debug("Listener update complete for quest: " + quest.getId());
    }

    /**
     * Update listeners for a specific player's state change.
     * Called when a player's quest state changes.
     *
     * @param quest The quest that changed
     * @param playerUuid The player whose state changed
     */
    public void updateQuestListenersForPlayer(Quest quest, UUID playerUuid) {
        // For now, this is a no-op since we register all listeners.
        // In the future, this could be used to optimize listener registration
        // based on which states have active players.
        logger.debug("Player " + playerUuid + " state changed for quest: " + quest.getId());

        // Track active players
        Set<UUID> activePlayers = activePlayersByQuest.get(quest.getId());
        if (activePlayers != null) {
            quest.getStateForPlayer(playerUuid).thenAccept(state -> {
                if (state != QuestState.NOT_STARTED && state != QuestState.COMPLETED) {
                    activePlayers.add(playerUuid);
                } else if (state == QuestState.COMPLETED) {
                    activePlayers.remove(playerUuid);
                }
            });
        }
    }

    /**
     * Called when a player joins - track them for quest listener management.
     *
     * @param player The player who joined
     */
    public void onPlayerJoin(Player player) {
        logger.debug("Player joined: " + player.getName());
        // Progress loading is handled by PlayerJoinQuitListener
        // Preload state caches so move-based handlers don't block
        UUID uuid = player.getUniqueId();
        for (Quest q : quests.values()) {
            if (q instanceof AbstractQuest aq) {
                aq.preloadStateForPlayer(uuid);
            }
        }
    }

    /**
     * Called when a player quits - remove tracking.
     *
     * @param player The player who quit
     */
    public void onPlayerQuit(Player player) {
        logger.debug("Player quit: " + player.getName());
        UUID playerUuid = player.getUniqueId();

        // Remove from active player tracking
        for (Set<UUID> activePlayers : activePlayersByQuest.values()) {
            activePlayers.remove(playerUuid);
        }

        // Evict state caches to prevent memory leaks
        for (Quest q : quests.values()) {
            if (q instanceof AbstractQuest aq) {
                aq.evictStateForPlayer(playerUuid);
            }
        }

        // Notify listeners that hold per-player state
        activeListeners.values().forEach(listeners ->
            listeners.stream()
                .filter(l -> l instanceof PlayerAwareListener)
                .map(l -> (PlayerAwareListener) l)
                .forEach(l -> l.clearPlayerData(playerUuid))
        );

        // Progress saving is handled by PlayerJoinQuitListener
    }

    /**
     * Schedules a repeating task with the Bukkit scheduler
     *
     * @param taskId Unique identifier for the task
     * @param task The runnable task to execute
     * @param interval The interval in ticks between executions
     * @return The task ID from Bukkit scheduler
     */
    public int scheduleRepeatingTask(String taskId, Runnable task, long interval) {
        logger.debug("Scheduling repeating task: " + taskId + " (interval: " + interval + " ticks)");
        int taskNumber = plugin.getServer().getScheduler()
            .scheduleSyncRepeatingTask(plugin, task, 0L, interval);

        if (taskNumber != -1) {
            scheduledTasks.put(taskId, taskNumber);
            logger.debug("Task scheduled successfully: " + taskId + " (task#: " + taskNumber + ")");
        } else {
            logger.warning("Failed to schedule task: " + taskId);
        }

        return taskNumber;
    }

    /**
     * Cancels a scheduled task by its ID
     *
     * @param taskId The ID of the task to cancel
     */
    public void cancelTask(String taskId) {
        Integer taskNumber = scheduledTasks.remove(taskId);
        if (taskNumber != null) {
            logger.debug("Cancelling task: " + taskId + " (task#: " + taskNumber + ")");
            plugin.getServer().getScheduler().cancelTask(taskNumber);
        }
    }

    /**
     * Gets the IDs of all registered quests
     *
     * @return A list of quest IDs
     */
    public List<String> getQuestIds() {
        return new ArrayList<>(quests.keySet());
    }

    /**
     * Gets all registered quests
     *
     * @return A list of quests
     */
    public List<Quest> getAllQuests() {
        return new ArrayList<>(quests.values());
    }

    /**
     * Gets the set of players actively engaged with a quest.
     *
     * @param questId The quest ID
     * @return Set of active player UUIDs, or empty set if quest not found
     */
    public Set<UUID> getActivePlayersForQuest(String questId) {
        Set<UUID> players = activePlayersByQuest.get(questId);
        return players != null ? Set.copyOf(players) : Set.of();
    }

    /**
     * Validates all registered quests to catch configuration issues
     * @return true if all quests are valid
     */
    public boolean validateQuests() {
        logger.debug("Validating all registered quests...");
        boolean allValid = true;

        for (Quest quest : quests.values()) {
            try {
                // Basic validation
                if (quest.getId() == null || quest.getId().isEmpty()) {
                    logger.warning("Quest has null or empty ID");
                    allValid = false;
                }

                if (quest.getName() == null || quest.getName().isEmpty()) {
                    logger.warning("Quest has null or empty name: " + quest.getId());
                    allValid = false;
                }

                // Check listener creation for each state
                for (QuestState state : QuestState.values()) {
                    List<Listener> listeners = quest.createListenersForState(state);
                    if (listeners == null) {
                        logger.warning(quest.getId() + " returned null listeners for state: " + state);
                        allValid = false;
                    }
                }

                logger.debug("Validated quest: " + quest.getId());
            } catch (Exception e) {
                logger.error("Exception during validation of quest: " + quest.getId(), e);
                allValid = false;
            }
        }

        logger.debug("Quest validation complete. All valid: " + allValid);
        return allValid;
    }

    // ==================== IQuestService Implementation ====================

    @Override
    public boolean unregisterQuest(String questId) {
        if (questId == null || questId.isEmpty()) {
            logger.warning("Attempted to unregister quest with null or empty ID");
            return false;
        }

        Quest quest = quests.remove(questId);
        if (quest == null) {
            logger.warning("Quest not found for unregistration: " + questId);
            return false;
        }

        // Clean up listeners
        if (activeListeners.containsKey(quest)) {
            List<Listener> listeners = activeListeners.remove(quest);
            listeners.forEach(HandlerList::unregisterAll);
        }

        // Clean up active players tracking
        activePlayersByQuest.remove(questId);

        // Clean up the quest
        quest.cleanup();

        logger.info("Unregistered quest: " + questId);
        return true;
    }

    @Override
    public List<Quest> getActiveQuests() {
        return quests.values().stream()
            .filter(quest -> !getActivePlayersForQuest(quest.getId()).isEmpty())
            .collect(Collectors.toList());
    }

    @Override
    public int getQuestCount() {
        return quests.size();
    }

    @Override
    public CompletableFuture<QuestState> getPlayerQuestState(UUID playerId, String questId) {
        Quest quest = quests.get(questId);
        if (quest == null) {
            return CompletableFuture.completedFuture(QuestState.NOT_STARTED);
        }
        return quest.getStateForPlayer(playerId);
    }

    @Override
    public CompletableFuture<Void> updatePlayerQuestState(UUID playerId, String questId, QuestState newState) {
        Quest quest = quests.get(questId);
        if (quest == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Quest not found: " + questId));
        }
        return quest.advanceStateForPlayer(playerId, newState);
    }

    @Override
    public CompletableFuture<Boolean> startQuest(UUID playerId, String questId) {
        Quest quest = quests.get(questId);
        if (quest == null) {
            return CompletableFuture.completedFuture(false);
        }

        return quest.getStateForPlayer(playerId)
            .thenCompose(currentState -> {
                if (currentState != QuestState.NOT_STARTED) {
                    logger.debug("Player " + playerId + " cannot start quest " + questId + " - already in state: " + currentState);
                    return CompletableFuture.completedFuture(false);
                }
                return quest.advanceStateForPlayer(playerId, QuestState.QUEST_ACTIVE)
                    .thenApply(v -> true);
            });
    }

    @Override
    public CompletableFuture<Boolean> completeQuest(UUID playerId, String questId) {
        Quest quest = quests.get(questId);
        if (quest == null) {
            return CompletableFuture.completedFuture(false);
        }

        // Idempotency guard: if a completion for this player+quest is already in-flight,
        // drop the duplicate (e.g. two rapid event firings before the first DB write lands).
        String inFlightKey = playerId.toString() + ":" + questId;
        if (!completionsInProgress.add(inFlightKey)) {
            logger.debug("Duplicate completion ignored for " + playerId + " on quest " + questId);
            return CompletableFuture.completedFuture(false);
        }

        return quest.getStateForPlayer(playerId)
            .thenCompose(currentState -> {
                if (currentState == QuestState.COMPLETED) {
                    return CompletableFuture.completedFuture(false);
                }
                return quest.advanceStateForPlayer(playerId, QuestState.COMPLETED)
                    .thenApply(v -> {
                        // Fire QuestCompleteEvent so chain listeners and other
                        // systems are notified (including force-complete)
                        Player player = Bukkit.getPlayer(playerId);
                        if (player != null) {
                            Bukkit.getScheduler().runTask(plugin, () ->
                                Bukkit.getPluginManager().callEvent(
                                    new QuestCompleteEvent(player, questId, quest.getName())));
                        }
                        return true;
                    });
            })
            .whenComplete((result, ex) -> completionsInProgress.remove(inFlightKey));
    }

    @Override
    public CompletableFuture<Boolean> abandonQuest(UUID playerId, String questId) {
        Quest quest = quests.get(questId);
        if (quest == null) {
            return CompletableFuture.completedFuture(false);
        }

        return quest.getStateForPlayer(playerId)
            .thenCompose(currentState -> {
                if (currentState == QuestState.NOT_STARTED || currentState == QuestState.COMPLETED) {
                    return CompletableFuture.completedFuture(false);
                }
                return quest.advanceStateForPlayer(playerId, QuestState.NOT_STARTED)
                    .thenApply(v -> true);
            });
    }

    @Override
    public CompletableFuture<Boolean> resetQuest(UUID playerId, String questId) {
        Quest quest = quests.get(questId);
        if (quest == null) {
            return CompletableFuture.completedFuture(false);
        }

        return plugin.getQuestProgressService().resetQuestProgress(playerId, questId);
    }

    @Override
    public CompletableFuture<List<String>> getPlayerActiveQuests(UUID playerId) {
        List<CompletableFuture<String>> futures = quests.keySet().stream()
            .map(questId -> getPlayerQuestState(playerId, questId)
                .thenApply(state -> state == QuestState.QUEST_ACTIVE ? questId : null))
            .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .filter(id -> id != null)
                .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<List<String>> getPlayerCompletedQuests(UUID playerId) {
        List<CompletableFuture<String>> futures = quests.keySet().stream()
            .map(questId -> getPlayerQuestState(playerId, questId)
                .thenApply(state -> state == QuestState.COMPLETED ? questId : null))
            .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .filter(id -> id != null)
                .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<Boolean> canStartQuest(UUID playerId, String questId) {
        Quest quest = quests.get(questId);
        if (quest == null) {
            return CompletableFuture.completedFuture(false);
        }

        return quest.getStateForPlayer(playerId)
            .thenApply(state -> state == QuestState.NOT_STARTED);
    }


    @Override
    public boolean isInFallbackMode() {
        return plugin.getQuestProgressService().isInFallbackMode();
    }

    @Override
    public void reloadQuests() {
        logger.info("Reloading all quests");
        resetQuests();
    }

    @Override
    public void shutdown() {
        cleanupQuests();
    }
}
