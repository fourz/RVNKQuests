package org.fourz.RVNKQuests.quest;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.config.ConfigManager;
import org.fourz.RVNKQuests.event.QuestCompleteEvent;
import org.fourz.RVNKQuests.service.IJournalService;
import org.fourz.RVNKQuests.service.INotificationService;
import org.fourz.RVNKQuests.service.IQuestProgressService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;


/**
 * Base abstract class for quest implementations with per-player state tracking.
 *
 * <p>Provides common functionality and enforces structure for all quest implementations.
 * Quest state is now tracked PER-PLAYER and persisted via the QuestProgressService.</p>
 *
 * <p>State lookups use a local in-memory cache to avoid blocking the main thread.
 * The cache is eagerly updated on state transitions and lazily populated on first
 * access via an async DB query. Event handlers (especially PlayerMoveEvent) will
 * see NOT_STARTED for at most a few ticks until the async load completes.</p>
 */
public abstract class AbstractQuest implements Quest {
    protected final RVNKQuests plugin;
    protected final String questId;
    protected final String name;
    protected final LogManager logger;

    private final IQuestProgressService progressService;
    private final IJournalService journalService;
    private final INotificationService notifService;
    private final QuestManager questManager;
    private final ConfigManager configManager;

    /**
     * Local state cache — avoids blocking main thread on PlayerMoveEvent handlers.
     * Eagerly updated by advanceStateForPlayer(); lazily populated on first read.
     */
    private final Map<UUID, QuestState> stateCache = new ConcurrentHashMap<>();

    /**
     * Local path choice cache — mirrors stateCache pattern for branching quests.
     * Eagerly updated by setPathChoice(); lazily populated on first read.
     * Value is empty string when explicitly loaded but no path set (vs null = not yet loaded).
     */
    private final Map<UUID, String> pathChoiceCache = new ConcurrentHashMap<>();

    /**
     * Creates a new quest with the specified ID and name.
     *
     * @param plugin The main plugin instance
     * @param questId Unique identifier for this quest
     * @param name Display name of this quest shown to players
     */
    public AbstractQuest(RVNKQuests plugin, String questId, String name) {
        this.plugin = plugin;
        this.questId = questId;
        this.name = name;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.progressService = plugin.getQuestProgressService();
        this.journalService = plugin.getJournalService();
        this.notifService = plugin.getNotificationService();
        this.questManager = plugin.getQuestManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public String getId() {
        return questId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public CompletableFuture<QuestState> getStateForPlayer(UUID playerUuid) {
        if (progressService == null) {
            logger.warning("QuestProgressService not available - returning NOT_STARTED");
            return CompletableFuture.completedFuture(QuestState.NOT_STARTED);
        }
        return progressService.getQuestState(playerUuid, questId);
    }

    @Override
    public QuestState getStateForPlayer(Player player) {
        if (player == null) {
            return QuestState.NOT_STARTED;
        }

        UUID uuid = player.getUniqueId();
        QuestState cached = stateCache.get(uuid);
        if (cached != null) {
            return cached;
        }

        // Not cached yet — kick off async load and return NOT_STARTED for now.
        // The cache will be populated within a few ticks; move-based handlers
        // will re-check on the next event and pick up the loaded state.
        getStateForPlayer(uuid).thenAccept(state -> stateCache.put(uuid, state));
        return QuestState.NOT_STARTED;
    }

    @Override
    public boolean isStateCached(Player player) {
        return player != null && stateCache.containsKey(player.getUniqueId());
    }

    /**
     * Preloads the state and path choice cache for a player from the database (async).
     * Call on player join to ensure move-based handlers have immediate state access.
     */
    public void preloadStateForPlayer(UUID playerUuid) {
        getStateForPlayer(playerUuid).thenAccept(state -> stateCache.put(playerUuid, state));
        // Preload path choice for branching quests
        getPathChoice(playerUuid).thenAccept(path ->
            pathChoiceCache.put(playerUuid, path != null ? path : ""));
    }

    /**
     * Evicts a player's cached state and path choice. Call on player quit to prevent memory leaks.
     */
    public void evictStateForPlayer(UUID playerUuid) {
        stateCache.remove(playerUuid);
        pathChoiceCache.remove(playerUuid);
    }

    @Override
    @Deprecated
    public QuestState getCurrentState() {
        logger.debug("getCurrentState() called - use getStateForPlayer() instead");
        return QuestState.NOT_STARTED;
    }

    @Override
    public CompletableFuture<Void> advanceStateForPlayer(UUID playerUuid, QuestState newState) {
        if (progressService == null) {
            logger.warning("QuestProgressService not available - cannot advance state");
            return CompletableFuture.completedFuture(null);
        }

        // Eagerly update local cache so event handlers see the new state immediately
        QuestState previousCached = stateCache.put(playerUuid, newState);

        return getStateForPlayer(playerUuid)
            .thenCompose(currentState -> {
                logger.debug("Advancing state for " + playerUuid + " from " + currentState + " to " + newState);
                return progressService.updateQuestState(playerUuid, questId, newState)
                    .thenAccept(progress -> {
                        // Record journal entry for the state transition
                        recordStateTransitionJournal(playerUuid, currentState, newState);

                        // Update listeners if needed
                        questManager.updateQuestListenersForPlayer(this, playerUuid);
                    });
            });
    }

    /**
     * Records a journal entry based on the quest state transition.
     * This is the single recording point for ALL state changes — generic objectives,
     * triggers, admin commands, and QuestManager methods all flow through here.
     */
    private void recordStateTransitionJournal(UUID playerUuid, QuestState fromState, QuestState toState) {
        if (journalService == null || !journalService.isAvailable()) return;

        switch (toState) {
            case QUEST_ACTIVE -> journalService.recordQuestStart(playerUuid, questId);
            case COMPLETED -> journalService.recordQuestComplete(playerUuid, questId);
            case NOT_STARTED -> {
                // Only record abandon if transitioning FROM an active state
                if (fromState != QuestState.NOT_STARTED) {
                    journalService.recordQuestAbandon(playerUuid, questId);
                }
            }
            case ABANDONED -> journalService.recordQuestAbandon(playerUuid, questId);
            case TRIGGER_FOUND -> journalService.recordObjectiveComplete(playerUuid, questId, "state:trigger_found");
            case OBJECTIVE_FOUND -> journalService.recordObjectiveComplete(playerUuid, questId, "state:objective_found");
        }
    }

    @Override
    @Deprecated
    public void advanceState(QuestState newState) {
        logger.warning("advanceState() called without player - use advanceStateForPlayer() instead");
    }

    @Override
    public boolean isCompleted(Player player) {
        if (player == null) {
            return false;
        }
        return getStateForPlayer(player) == QuestState.COMPLETED;
    }

    @Override
    public RVNKQuests getPlugin() {
        return plugin;
    }

    /**
     * Starts the quest for the given player.
     * Handles common start logic and delegates specific behavior to onStart().
     *
     * @param player The player starting the quest
     * @return CompletableFuture that completes with true if quest was started
     */
    public CompletableFuture<Boolean> start(Player player) {
        if (player == null) {
            logger.warning("Cannot start quest: Player is null");
            return CompletableFuture.completedFuture(false);
        }

        UUID playerUuid = player.getUniqueId();

        return getStateForPlayer(playerUuid)
            .thenCompose(currentState -> {
                if (currentState != QuestState.NOT_STARTED && currentState != QuestState.TRIGGER_FOUND) {
                    logger.debug("Cannot start quest for " + player.getName() +
                        ": Already started (current state: " + currentState + ")");
                    return CompletableFuture.completedFuture(false);
                }

                logger.debug("Starting quest for player: " + player.getName());
                boolean success = onStart(player);

                if (success) {
                    return advanceStateForPlayer(playerUuid, QuestState.QUEST_ACTIVE)
                        .thenApply(v -> {
                            if (notifService != null) {
                                notifService.notifyQuestStart(player, name, null);
                            } else {
                                player.sendMessage("§a[Quest Started] §f" + name);
                            }
                            return true;
                        });
                }
                return CompletableFuture.completedFuture(false);
            });
    }

    /**
     * Completes the quest for the given player.
     * Handles common completion logic and delegates specific behavior to onComplete().
     *
     * @param player The player completing the quest
     * @return CompletableFuture that completes with true if quest was completed
     */
    public CompletableFuture<Boolean> complete(Player player) {
        if (player == null) {
            logger.warning("Cannot complete quest: Player is null");
            return CompletableFuture.completedFuture(false);
        }

        UUID playerUuid = player.getUniqueId();

        return getStateForPlayer(playerUuid)
            .thenCompose(currentState -> {
                if (currentState == QuestState.COMPLETED) {
                    logger.debug("Cannot complete quest for " + player.getName() + ": Already completed");
                    return CompletableFuture.completedFuture(false);
                }

                logger.debug("Completing quest for player: " + player.getName());
                boolean success = onComplete(player);

                if (success) {
                    return advanceStateForPlayer(playerUuid, QuestState.COMPLETED)
                        .thenApply(v -> {
                            // Per-player notification: routed through NotificationService (preference-gated)
                            if (notifService != null) {
                                notifService.notifyQuestComplete(player, name);
                            } else {
                                player.sendMessage("§a[Quest Completed] §f" + name);
                            }

                            // Server-wide broadcast: config-gated only (not a personal preference)
                            if (configManager.getConfig().getBoolean("quests.announce_completion", true)) {
                                plugin.getServer().broadcastMessage(
                                    "§6" + player.getName() + " §ehas completed the quest §6" + name + "§e!"
                                );
                            }

                            // Fire event for cross-plugin integration (e.g., RVNKLore discovery triggers)
                            Bukkit.getPluginManager().callEvent(new QuestCompleteEvent(player, questId, name));

                            return true;
                        });
                }
                return CompletableFuture.completedFuture(false);
            });
    }

    /**
     * Sets the path choice for a player on this quest.
     *
     * @param player The player
     * @param pathChoice The chosen path identifier
     * @return CompletableFuture that completes when path is set
     */
    public CompletableFuture<Void> setPathChoice(Player player, String pathChoice) {
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }

        // Eagerly update cache so event handlers see the new path immediately
        pathChoiceCache.put(player.getUniqueId(), pathChoice != null ? pathChoice : "");

        if (progressService == null) {
            return CompletableFuture.completedFuture(null);
        }

        logger.debug("Setting path choice for " + player.getName() + ": " + pathChoice);
        return progressService.setPathChoice(player.getUniqueId(), questId, pathChoice)
            .thenAccept(progress -> {});
    }

    /**
     * Gets the path choice for a player on this quest (async, from DB).
     *
     * @param player The player
     * @return CompletableFuture with the path choice, or null if not set
     */
    public CompletableFuture<String> getPathChoice(Player player) {
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }
        return getPathChoice(player.getUniqueId());
    }

    /**
     * Gets the path choice for a player on this quest (async, from DB).
     *
     * @param playerUuid The player UUID
     * @return CompletableFuture with the path choice, or null if not set
     */
    public CompletableFuture<String> getPathChoice(UUID playerUuid) {
        if (progressService == null) {
            return CompletableFuture.completedFuture(null);
        }

        return progressService.getProgress(playerUuid, questId)
            .thenApply(opt -> opt.map(progress -> progress.pathChoice()).orElse(null));
    }

    /**
     * Gets the cached path choice for a player (non-blocking, main-thread safe).
     * Returns null if not yet loaded from DB. Used by objective handlers.
     *
     * @param player The player
     * @return The path choice, or null if not set or not yet loaded
     */
    public String getPathChoiceCached(Player player) {
        if (player == null) return null;
        String cached = pathChoiceCache.get(player.getUniqueId());
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        // Not cached yet — kick off async load
        getPathChoice(player.getUniqueId()).thenAccept(path ->
            pathChoiceCache.put(player.getUniqueId(), path != null ? path : ""));
        return null;
    }

    /**
     * Called when a quest is started for a player.
     * Implement quest-specific start logic in this method.
     *
     * @param player The player starting the quest
     * @return true if start was successful, false otherwise
     */
    protected abstract boolean onStart(Player player);

    /**
     * Called when a quest is completed by a player.
     * Implement quest-specific completion logic in this method.
     *
     * @param player The player completing the quest
     * @return true if completion was successful, false otherwise
     */
    protected abstract boolean onComplete(Player player);

    /**
     * Updates the quest progress for a player.
     * Subclasses should implement this to handle progress updates.
     *
     * @param player The player whose quest progress is being updated
     * @return true if the quest was successfully updated
     */
    public abstract boolean update(Player player);
}
