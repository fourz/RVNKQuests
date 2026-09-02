package org.fourz.RVNKQuests.quest;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
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

    /**
     * Worlds this plugin currently holds against RVNKWorlds' inactivity sweep (#1883), lowercased.
     *
     * <p>Kept so a reload can release what is no longer declared. Without it a world dropped from
     * {@code required_worlds} stays pinned in memory until the next restart, which quietly turns
     * the hold mechanism into a leak.</p>
     */
    private final Set<String> heldWorlds = ConcurrentHashMap.newKeySet();

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
            logger.debug("No quest repository available - skipping data-driven quest loading");
            return;
        }

        repository.findAll()
            .thenAccept(definitions -> {
                // Back on the main thread so Bukkit listener registration is thread-safe
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        int loaded = 0;
                        // Only quests that actually register can be played, so only these are worth
                        // preloading worlds for or reporting drift on. Scanning every definition in
                        // the repository warned about disabled quests, which are unplayable for a
                        // different reason entirely.
                        List<QuestDTO> registered = new ArrayList<>();

                        for (QuestDTO definition : definitions) {
                            if (quests.containsKey(definition.questId())) {
                                logger.debug("Skipping quest " + definition.questId() + " - already registered");
                                continue;
                            }

                            if (!plugin.getConfigManager().isQuestEnabled(definition.questId())) {
                                logger.debug("Skipping disabled quest: " + definition.questId());
                                continue;
                            }

                            DataDrivenQuest quest = new DataDrivenQuest(plugin, definition);
                            registerQuest(quest);
                            registered.add(definition);
                            loaded++;
                        }

                        logger.info("Loaded " + loaded + " data-driven quest(s) from repository" +
                            (repository.isInFallbackMode() ? " (YAML fallback)" : " (database)"));

                        activateDeclaredWorlds(registered);

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
                if (state == QuestState.NOT_STARTED || state == QuestState.COMPLETED
                        || state == QuestState.PAUSED || state == QuestState.ABANDONED) {
                    activePlayers.remove(playerUuid);
                } else {
                    activePlayers.add(playerUuid);
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

        // Quest party cleanup (#1982) — leave-on-quit, same rules as /quest party leave
        if (plugin.getQuestPartyService() != null) {
            plugin.getQuestPartyService().handleQuit(playerUuid);
        }

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
        return quest.setStateForPlayer(playerId, newState);
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
                    .thenApply(v -> true);
                    // QuestCompleteEvent is NOT fired here (#1984). advanceStateForPlayer ->
                    // AbstractQuest.performAdvance already fires it for every path that reaches
                    // COMPLETED - trigger component, admin command, or this method - and firing
                    // again here gave listeners TWO events per completion on this path while a
                    // trigger-driven completion gave one. Inconsistent is worse than merely
                    // doubled: ChainProgressListener could double-advance a chain and RVNKLore's
                    // QuestDiscoveryListener could double-grant a discovery, but only sometimes.
                    // AbstractQuest is the single firing point; keep it that way.
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
                return quest.setStateForPlayer(playerId, QuestState.NOT_STARTED)
                    .thenApply(v -> true);
            });
    }

    @Override
    public CompletableFuture<Boolean> resetQuest(UUID playerId, String questId) {
        Quest quest = quests.get(questId);
        if (quest == null) {
            return CompletableFuture.completedFuture(false);
        }

        return plugin.getQuestProgressService().resetQuestProgress(playerId, questId)
            .thenApply(success -> {
                if (success && quest instanceof AbstractQuest aq) {
                    aq.evictStateForPlayer(playerId);
                }
                return success;
            });
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

    /**
     * Activates every world declared via {@code required_worlds} across the loaded quests (#1877).
     *
     * <p>Runs at load/reload rather than lazily on quest start, because lazy cannot fix the case
     * this exists for: a start trigger inside an unloaded world is unreachable, so no player can
     * ever trigger the activation. The Tales From A Hat Chapter 1 chain (#1767) sat dormant on Event
     * exactly that way — {@code alphac} left {@code IMPORTED} after every restart while
     * {@code quest validate} reported the chain {@code [VALID]}.</p>
     *
     * <p>Declared worlds only. Activating everything a quest merely references would defeat the
     * opt-in and pull every old world on Event into memory at boot.</p>
     *
     * <p>Undeclared references are logged, not activated — that gap is the actionable authoring
     * error, and {@code /quest debug preflight} reports it per quest.</p>
     *
     * @param definitions The quest definitions just loaded
     */
    private void activateDeclaredWorlds(java.util.Collection<QuestDTO> definitions) {
        org.fourz.RVNKQuests.integration.WorldActivationService worlds = plugin.getWorldActivation();
        if (worlds == null) return;

        if (!plugin.getConfigManager().isWorldPreloadEnabled()) {
            // Release before returning. An operator who turns the feature off and reloads expects
            // this plugin to stop pinning worlds; returning first would leave every existing hold
            // in place until restart, with no surface that mentions preload to explain why.
            // (This early return skipping the cleanup step is the same shape as the #1883 bug.)
            releaseUndeclaredWorlds(worlds, java.util.Set.of());
            logger.debug("World preload disabled (quests.preload-required-worlds=false)");
            return;
        }

        java.util.Set<String> wanted = new java.util.LinkedHashSet<>();
        // world -> quests referencing it without declaring it. Grouped by world, because the
        // actionable unit is "this world needs declaring", not one line per quest: the first cut
        // printed 35 quest-scoped entries as a single unreadable line.
        java.util.Map<String, java.util.List<String>> undeclaredByWorld = new java.util.TreeMap<>();

        for (QuestDTO definition : definitions) {
            java.util.Map<String, Object> metadata = definition.metadata();
            wanted.addAll(QuestWorldRequirements.toActivate(metadata));

            for (String missing : QuestWorldRequirements.undeclared(metadata)) {
                undeclaredByWorld
                    .computeIfAbsent(missing, k -> new java.util.ArrayList<>())
                    .add(definition.questId());
            }
        }

        if (!undeclaredByWorld.isEmpty()) {
            // Split by current state: an already-loaded world is a tidiness issue, an unloaded one
            // means those quests are unplayable right now. Only the latter deserves a warning.
            java.util.List<String> unplayable = new java.util.ArrayList<>();
            java.util.List<String> cosmetic = new java.util.ArrayList<>();

            for (java.util.Map.Entry<String, java.util.List<String>> e : undeclaredByWorld.entrySet()) {
                String summary = e.getKey() + " (" + e.getValue().size() + " quest(s))";
                if (worlds.isActive(e.getKey())) {
                    cosmetic.add(summary);
                } else {
                    unplayable.add(summary);
                }
            }

            if (!unplayable.isEmpty()) {
                logger.warning("Quests target worlds that are NOT loaded and NOT declared in "
                    + "required_worlds - those quests are unplayable until the world is loaded: "
                    + String.join(", ", unplayable)
                    + ". Use '/quest debug preflight <quest>' for the per-quest detail.");
            }
            if (!cosmetic.isEmpty()) {
                logger.debug("Quests reference undeclared worlds that happen to be loaded: "
                    + String.join(", ", cosmetic));
            }
        }

        releaseUndeclaredWorlds(worlds, wanted);

        if (wanted.isEmpty()) return;

        // No `if (isActive) continue` here, deliberately. Activating is only half the job: the world
        // must also be HELD, or RVNKWorlds' inactivity sweep reclaims it and writes it back to
        // IMPORTED while the quest is mid-session (#1883). Skipping already-active worlds skipped
        // the hold with them — and an already-active world is the COMMON case, because RVNKWorlds
        // auto-loads previously-active worlds at boot. That is exactly how alphac was swept out from
        // under two players running tfah_ch1_journey on 2026-08-02, despite the quest declaring it.
        // ensureActive() short-circuits on a loaded world itself, so this costs nothing extra.
        for (String worldName : wanted) {
            worlds.ensureActive(worldName).thenAccept(ok -> {
                if (ok) {
                    heldWorlds.add(worldName.toLowerCase(java.util.Locale.ROOT));
                } else {
                    logger.warning("Declared quest world '" + worldName + "' could not be activated");
                }
            });
        }
        logger.info("Quest world preload: " + wanted.size() + " declared world(s) checked and held");
    }

    /**
     * Drops holds on worlds no quest declares any more (#1883).
     *
     * <p>A hold outlives the declaration that justified it, so without this a world removed from
     * {@code required_worlds} — or belonging to a quest that was deleted — stays pinned against
     * cleanup for the rest of the server's uptime. Reload is the only moment the declared set can
     * change, so it is the only moment this needs to run.</p>
     *
     * @param worlds The activation bridge
     * @param wanted Worlds declared by the definitions just loaded
     */
    private void releaseUndeclaredWorlds(
            org.fourz.RVNKQuests.integration.WorldActivationService worlds,
            java.util.Set<String> wanted) {

        if (heldWorlds.isEmpty()) return;

        java.util.Set<String> stillWanted = new java.util.HashSet<>();
        for (String name : wanted) {
            stillWanted.add(name.toLowerCase(java.util.Locale.ROOT));
        }

        java.util.List<String> released = new java.util.ArrayList<>();
        for (java.util.Iterator<String> it = heldWorlds.iterator(); it.hasNext(); ) {
            String held = it.next();
            if (stillWanted.contains(held)) continue;
            worlds.release(held);
            it.remove();
            released.add(held);
        }

        if (!released.isEmpty()) {
            logger.info("Released quest world hold on " + released.size()
                + " world(s) no longer declared: " + String.join(", ", released));
        }
    }

    @Override
    public void shutdown() {
        releaseAllWorldHolds();
        cleanupQuests();
    }

    /**
     * Drops every world hold this plugin placed (#1883).
     *
     * <p>A full server stop does not need this — RVNKWorlds' registry is in-memory and dies with
     * it. A PlugMan reload of RVNKQuests alone does: RVNKWorlds keeps running, and the holds placed
     * by the old classloader would pin those worlds with nothing left alive to release them.</p>
     */
    private void releaseAllWorldHolds() {
        if (heldWorlds.isEmpty()) return;

        org.fourz.RVNKQuests.integration.WorldActivationService worlds = plugin.getWorldActivation();
        if (worlds == null) {
            heldWorlds.clear();
            return;
        }

        int count = heldWorlds.size();
        for (String held : heldWorlds) {
            worlds.release(held);
        }
        heldWorlds.clear();
        logger.info("Released " + count + " quest world hold(s) on shutdown");
    }
}
