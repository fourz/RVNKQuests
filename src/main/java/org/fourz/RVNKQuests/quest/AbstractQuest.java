package org.fourz.RVNKQuests.quest;

import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.service.IQuestProgressService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;


/**
 * Base abstract class for quest implementations with per-player state tracking.
 *
 * <p>Provides common functionality and enforces structure for all quest implementations.
 * Quest state is now tracked PER-PLAYER and persisted via the QuestProgressService.</p>
 *
 * <p>Migration note: The global {@code state} field has been removed. Use
 * {@link #getStateForPlayer(Player)} or {@link #getStateForPlayer(UUID)} instead.</p>
 */
public abstract class AbstractQuest implements Quest {
    protected final RVNKQuests plugin;
    protected final String questId;
    protected final String name;
    protected final LogManager logger;

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
        IQuestProgressService service = plugin.getQuestProgressService();
        if (service == null) {
            logger.warning("QuestProgressService not available - returning NOT_STARTED");
            return CompletableFuture.completedFuture(QuestState.NOT_STARTED);
        }
        return service.getQuestState(playerUuid, questId);
    }

    @Override
    public QuestState getStateForPlayer(Player player) {
        if (player == null) {
            return QuestState.NOT_STARTED;
        }
        try {
            // Synchronous call for convenience - use async version when possible
            return getStateForPlayer(player.getUniqueId()).join();
        } catch (Exception e) {
            logger.warning("Failed to get state for player " + player.getName() + ": " + e.getMessage());
            return QuestState.NOT_STARTED;
        }
    }

    @Override
    @Deprecated
    public QuestState getCurrentState() {
        // Deprecated - per-player state is the new standard
        // Return NOT_STARTED to indicate no global state
        logger.debug("getCurrentState() called - use getStateForPlayer() instead");
        return QuestState.NOT_STARTED;
    }

    @Override
    public CompletableFuture<Void> advanceStateForPlayer(UUID playerUuid, QuestState newState) {
        IQuestProgressService service = plugin.getQuestProgressService();
        if (service == null) {
            logger.warning("QuestProgressService not available - cannot advance state");
            return CompletableFuture.completedFuture(null);
        }

        return getStateForPlayer(playerUuid)
            .thenCompose(currentState -> {
                logger.debug("Advancing state for " + playerUuid + " from " + currentState + " to " + newState);
                return service.updateQuestState(playerUuid, questId, newState);
            })
            .thenAccept(progress -> {
                // Update listeners if needed
                plugin.getQuestManager().updateQuestListenersForPlayer(this, playerUuid);
            });
    }

    @Override
    @Deprecated
    public void advanceState(QuestState newState) {
        // Deprecated - use advanceStateForPlayer instead
        logger.warning("advanceState() called without player - this is deprecated");
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
                            player.sendMessage("\u00a7a[Quest Started] \u00a7f" + name);
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
                            player.sendMessage("\u00a7a[Quest Completed] \u00a7f" + name);

                            // Announce completion to all players if configured
                            if (plugin.getConfigManager().getConfig().getBoolean("quests.announce_completion", true)) {
                                plugin.getServer().broadcastMessage(
                                    "\u00a76" + player.getName() + " \u00a7ehas completed the quest \u00a76" + name + "\u00a7e!"
                                );
                            }
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

        IQuestProgressService service = plugin.getQuestProgressService();
        if (service == null) {
            return CompletableFuture.completedFuture(null);
        }

        logger.debug("Setting path choice for " + player.getName() + ": " + pathChoice);
        return service.setPathChoice(player.getUniqueId(), questId, pathChoice)
            .thenAccept(progress -> {});
    }

    /**
     * Gets the path choice for a player on this quest.
     *
     * @param player The player
     * @return CompletableFuture with the path choice, or null if not set
     */
    public CompletableFuture<String> getPathChoice(Player player) {
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }

        IQuestProgressService service = plugin.getQuestProgressService();
        if (service == null) {
            return CompletableFuture.completedFuture(null);
        }

        return service.getProgress(player.getUniqueId(), questId)
            .thenApply(opt -> opt.map(progress -> progress.pathChoice()).orElse(null));
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
