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
     * Stores the state that was active immediately before a pause, so resume can restore it.
     * Cleared on player quit. If absent at resume time, defaults to QUEST_ACTIVE.
     */
    private final Map<UUID, QuestState> pausedStateCache = new ConcurrentHashMap<>();

    /**
     * Local path choice cache — mirrors stateCache pattern for branching quests.
     * Eagerly updated by setPathChoice(); lazily populated on first read.
     * Value is empty string when explicitly loaded but no path set (vs null = not yet loaded).
     */
    private final Map<UUID, String> pathChoiceCache = new ConcurrentHashMap<>();

    /**
     * Per-player serialization chain for state mutations (#1853).
     *
     * <p>Every state change appends itself to the player's chain, so the
     * read-decide-write sequence in {@link #applyStateChange} can never interleave
     * with another change to the same player's copy of this quest. Without this,
     * two components firing in the same tick (a co-located LOCATION_PROXIMITY
     * trigger and a REACH objective, typically on arrival by teleport or portal)
     * both read the same stale snapshot and both write — last write wins, and the
     * persisted state is non-deterministic.</p>
     *
     * <p>Entries self-remove once
     * the chain drains.</p>
     */
    private final Map<UUID, CompletableFuture<Void>> writeChains = new ConcurrentHashMap<>();

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
        pausedStateCache.remove(playerUuid);
        // writeChains is deliberately NOT cleared here — a chain may still be in flight,
        // and dropping it would let a rejoin start a second chain racing the first.
        // Entries self-remove once they drain.
    }

    @Override
    @Deprecated
    public QuestState getCurrentState() {
        logger.debug("getCurrentState() called - use getStateForPlayer() instead");
        return QuestState.NOT_STARTED;
    }

    @Override
    public CompletableFuture<Void> advanceStateForPlayer(UUID playerUuid, QuestState newState) {
        return enqueueStateChange(playerUuid, newState, true);
    }

    @Override
    public CompletableFuture<Void> setStateForPlayer(UUID playerUuid, QuestState newState) {
        return enqueueStateChange(playerUuid, newState, false);
    }

    /**
     * Appends a state change to the player's serialization chain (#1853).
     *
     * <p>{@code compute()} is atomic per key, so only one thread ever links the next
     * change onto the chain. Each link waits for the previous one to settle before its
     * own read-decide-write runs, which is what makes the outcome deterministic when
     * several components fire in the same tick.</p>
     *
     * @param monotonic when true the change is rejected if it would move the quest
     *                  backwards along the linear progression — see
     *                  {@link #isForwardProgress(QuestState, QuestState)}
     */
    private CompletableFuture<Void> enqueueStateChange(UUID playerUuid, QuestState newState, boolean monotonic) {
        if (progressService == null) {
            logger.warning("QuestProgressService not available - cannot advance state");
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> result = new CompletableFuture<>();

        CompletableFuture<Void> tail = writeChains.compute(playerUuid, (uuid, prior) -> {
            CompletableFuture<Void> base = (prior != null) ? prior : CompletableFuture.completedFuture(null);
            // handle() first so a failed predecessor cannot stall every later change.
            // *Async so applyStateChange never runs while compute() holds the bin lock.
            base.handle((v, ex) -> (Void) null)
                .thenComposeAsync(ignored -> applyStateChange(uuid, newState, monotonic))
                .whenComplete((v, ex) -> {
                    if (ex != null) {
                        result.completeExceptionally(ex);
                    } else {
                        result.complete(null);
                    }
                });
            // The next link waits on this one regardless of outcome.
            return result.handle((v, ex) -> (Void) null);
        });

        // Drop the entry once the chain drains, but only if nothing has been appended since.
        tail.whenComplete((v, ex) -> writeChains.remove(playerUuid, tail));

        return result;
    }

    /**
     * Performs one state change. Runs inside the player's write chain, so the state read
     * here is still current when the write below lands — no other change can slip between.
     */
    private CompletableFuture<Void> applyStateChange(UUID playerUuid, QuestState newState, boolean monotonic) {
        return getStateForPlayer(playerUuid)
            .thenCompose(currentState -> {
                // No-op: re-firing side effects (rewards, broadcast, QuestCompleteEvent)
                // for a state the player already holds would double-deliver.
                if (currentState == newState) {
                    stateCache.put(playerUuid, currentState);
                    return CompletableFuture.<Void>completedFuture(null);
                }

                // Reject regressions from automatic (component-driven) advances. Explicit
                // admin operations use setStateForPlayer() and skip this.
                if (monotonic && !isForwardProgress(currentState, newState)) {
                    logger.debug("Quest " + questId + " advance " + currentState + " -> " + newState
                        + " for " + playerUuid + " ignored — not forward progress (#1853)");
                    stateCache.put(playerUuid, currentState);
                    return CompletableFuture.<Void>completedFuture(null);
                }

                // Gate trigger-driven activation (NOT_STARTED -> TRIGGER_FOUND) on the quest's
                // prerequisites.
                //
                // #1884: this must apply to component-driven advances ONLY. It previously ran for
                // every caller, including setStateForPlayer() — the explicit admin path behind
                // `/quest state` and `/quest debug setstate`, the latter of which announces
                // "bypassing normal state transitions". Setting a mid-chain state on a quest whose
                // prereq was unmet therefore did nothing at all, while the command still printed
                // success, because the no-op below completes the future normally. It read as the
                // state "reverting" moments later; in fact it was never applied. That cost two full
                // false-negative test runs during the #1765 QA session and very nearly produced a
                // bogus ENCOUNTER bug report.
                //
                // `monotonic` already distinguishes the two callers — true for advanceStateForPlayer
                // (components), false for setStateForPlayer (admin) — so it is the honest gate, and
                // it makes the documented "bypasses validation" behaviour real.
                if (monotonic && currentState == QuestState.NOT_STARTED && newState == QuestState.TRIGGER_FOUND) {
                    return arePrerequisitesMet(playerUuid).thenCompose(met -> {
                        if (!met) {
                            logger.debug("Quest " + questId + " trigger blocked for " + playerUuid
                                + " — prerequisites not met");
                            stateCache.put(playerUuid, currentState);
                            return CompletableFuture.<Void>completedFuture(null);
                        }
                        return performAdvance(playerUuid, currentState, newState);
                    });
                }
                return performAdvance(playerUuid, currentState, newState);
            });
    }

    /**
     * Position of a state on the linear quest progression, or {@code -1} for states that
     * sit outside it ({@code ABANDONED}, {@code PAUSED}).
     */
    private static int progressRank(QuestState state) {
        return switch (state) {
            case NOT_STARTED -> 0;
            case TRIGGER_FOUND -> 1;
            case QUEST_ACTIVE -> 2;
            case OBJECTIVE_FOUND -> 3;
            case COMPLETED -> 4;
            case ABANDONED, PAUSED -> -1;
        };
    }

    /**
     * Whether an automatic advance may be applied.
     *
     * <p>Only the linear progression is ordered. Transitions involving {@code PAUSED} or
     * {@code ABANDONED} are lifecycle operations rather than progress, so they are left
     * unguarded and behave exactly as they did before #1853.</p>
     */
    private static boolean isForwardProgress(QuestState from, QuestState to) {
        int fromRank = progressRank(from);
        int toRank = progressRank(to);
        if (fromRank < 0 || toRank < 0) {
            return true;
        }
        return toRank > fromRank;
    }

    /**
     * Quest IDs that must be COMPLETED before this quest's trigger may activate.
     * Base quests have none; {@code DataDrivenQuest} overrides to expose its definition's
     * {@code prerequisites}.
     */
    protected java.util.List<String> getPrerequisiteQuestIds() {
        return java.util.List.of();
    }

    /**
     * Checks that every prerequisite quest is COMPLETED for the player. Empty/no
     * prerequisites resolve to {@code true}.
     */
    private CompletableFuture<Boolean> arePrerequisitesMet(UUID playerUuid) {
        java.util.List<String> prereqs = getPrerequisiteQuestIds();
        if (prereqs == null || prereqs.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }
        CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(true);
        for (String prereqId : prereqs) {
            chain = chain.thenCompose(ok -> {
                if (!ok) {
                    return CompletableFuture.completedFuture(false);
                }
                return progressService.getQuestState(playerUuid, prereqId)
                    .thenApply(state -> state == QuestState.COMPLETED);
            });
        }
        return chain;
    }

    private CompletableFuture<Void> performAdvance(UUID playerUuid, QuestState currentState, QuestState newState) {
                logger.debug("Advancing state for " + playerUuid + " from " + currentState + " to " + newState);
                // Update the local cache now that the change is committed to, so event handlers
                // firing before the DB write lands already see the new state. Deliberately AFTER
                // the gates above — an unconditional pre-write was half of #1853.
                stateCache.put(playerUuid, newState);
                return progressService.updateQuestState(playerUuid, questId, newState)
                    .thenAccept(progress -> {
                        // All Bukkit operations (listener registration, event dispatch, player messages,
                        // item delivery) must run on the main thread. The DB write above completes on
                        // an async pool thread, so dispatch back to main before any Bukkit API calls.
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            // Record journal entry for the state transition
                            recordStateTransitionJournal(playerUuid, currentState, newState);

                            // registerEvents/unregisterAll require main thread for all state transitions
                            questManager.updateQuestListenersForPlayer(this, playerUuid);

                            // Fire all completion side-effects regardless of how COMPLETED is reached
                            // (trigger component, admin command, or direct complete() call)
                            if (newState == QuestState.COMPLETED) {
                                org.bukkit.entity.Player onlinePlayer = plugin.getServer().getPlayer(playerUuid);
                                if (onlinePlayer != null) {
                                    onComplete(onlinePlayer);

                                    if (notifService != null) {
                                        notifService.notifyQuestComplete(onlinePlayer, name);
                                    } else {
                                        onlinePlayer.sendMessage("§a[Quest Completed] §f" + name);
                                    }

                                    if (configManager.getConfig().getBoolean("quests.announce_completion", true)) {
                                        plugin.getServer().broadcastMessage(
                                            "§6" + onlinePlayer.getName() + " §ehas completed the quest §6" + name + "§e!"
                                        );
                                    }

                                    Bukkit.getPluginManager().callEvent(new QuestCompleteEvent(onlinePlayer, questId, name));
                                }
                            }
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
            case PAUSED -> {} // no journal action for pause/resume
        }
    }

    @Override
    @Deprecated
    public void advanceState(QuestState newState) {
        logger.debug("advanceState() called without player - use advanceStateForPlayer() instead");
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

                // Advance to COMPLETED — onComplete(), notifications, and QuestCompleteEvent
                // all fire inside advanceStateForPlayer() when state=COMPLETED, so they are
                // triggered consistently whether completion comes from here or from a trigger component.
                return advanceStateForPlayer(playerUuid, QuestState.COMPLETED)
                    .thenApply(v -> true);
            });
    }

    @Override
    public CompletableFuture<Boolean> pauseForPlayer(UUID playerUuid) {
        return getStateForPlayer(playerUuid).thenCompose(currentState -> {
            if (currentState != QuestState.QUEST_ACTIVE && currentState != QuestState.OBJECTIVE_FOUND) {
                logger.debug("Cannot pause quest " + questId + " for " + playerUuid + ": state is " + currentState);
                return CompletableFuture.completedFuture(false);
            }
            pausedStateCache.put(playerUuid, currentState);
            return setStateForPlayer(playerUuid, QuestState.PAUSED).thenApply(v -> true);
        });
    }

    @Override
    public CompletableFuture<Boolean> resumeForPlayer(UUID playerUuid) {
        return getStateForPlayer(playerUuid).thenCompose(currentState -> {
            if (currentState != QuestState.PAUSED) {
                logger.debug("Cannot resume quest " + questId + " for " + playerUuid + ": state is " + currentState);
                return CompletableFuture.completedFuture(false);
            }
            QuestState restoreState = pausedStateCache.getOrDefault(playerUuid, QuestState.QUEST_ACTIVE);
            pausedStateCache.remove(playerUuid);
            logger.debug("Resuming quest " + questId + " for " + playerUuid + " → " + restoreState);
            return setStateForPlayer(playerUuid, restoreState).thenApply(v -> true);
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
