package org.fourz.RVNKQuests.quest;

import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.fourz.RVNKQuests.RVNKQuests;
import org.bukkit.event.Listener;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Core interface defining the structure and lifecycle of quests.
 *
 * <p>Quests in this system are state-based with dynamic event handling:</p>
 * <ul>
 *   <li>Each quest has a unique identifier and display name</li>
 *   <li>Quest progress is tracked PER-PLAYER (not global)</li>
 *   <li>Different event listeners are active based on quest state</li>
 *   <li>Quests manage their own resources and cleanup</li>
 * </ul>
 *
 * <p>State is persisted via the QuestProgressService and survives server restarts.</p>
 */
public interface Quest {
    /**
     * Returns the unique identifier for this quest
     * Used in configuration and database references
     */
    String getId();

    /**
     * Returns the display name of the quest
     * Used in player-facing messages and UI
     */
    String getName();

    /**
     * Called when the quest is first loaded
     * Should initialize resources and state
     */
    void initialize();

    /**
     * Called when the quest is being unloaded
     * Should clean up all resources to prevent memory leaks
     */
    void cleanup();

    /**
     * Checks if the quest is completed for a specific player
     * @param player The player to check
     * @return true if the player has completed this quest
     */
    boolean isCompleted(Player player);

    /**
     * Gets the current state of the quest for a specific player.
     * This is the preferred method for per-player state tracking.
     *
     * @param playerUuid The player's UUID
     * @return The player's current quest state (async)
     */
    CompletableFuture<QuestState> getStateForPlayer(UUID playerUuid);

    /**
     * Gets the current state of the quest for a specific player (sync convenience method).
     * Returns NOT_STARTED if state cannot be determined.
     *
     * @param player The player
     * @return The player's current quest state
     */
    QuestState getStateForPlayer(Player player);

    /**
     * Checks whether the state cache has been populated for a player.
     * Use before acting on NOT_STARTED from {@link #getStateForPlayer(Player)} —
     * an uncached NOT_STARTED is a default, not a confirmed database state.
     *
     * @param player The player
     * @return true if the player's state has been loaded from the database
     */
    boolean isStateCached(Player player);

    /**
     * Returns the current state of the quest.
     * @deprecated Use {@link #getStateForPlayer(UUID)} for per-player state.
     *             This method exists for backwards compatibility and returns
     *             NOT_STARTED by default.
     * @return Current quest state (NOT_STARTED for per-player quests)
     */
    @Deprecated
    QuestState getCurrentState();

    /**
     * Updates the quest's state for a specific player.
     *
     * @param playerUuid The player's UUID
     * @param newState The new state to advance to
     * @return CompletableFuture that completes when state is updated
     */
    CompletableFuture<Void> advanceStateForPlayer(UUID playerUuid, QuestState newState);

    /**
     * Updates the quest's state and triggers any necessary changes.
     * @deprecated Use {@link #advanceStateForPlayer(UUID, QuestState)} for per-player state.
     * @param newState The new state to advance to
     */
    @Deprecated
    void advanceState(QuestState newState);

    /**
     * Returns the starting location for this quest
     * @return The location where this quest begins, or null if not applicable
     */
    Location getStartLocation();

    /**
     * Returns the name of the starting trigger for this quest
     * @return A descriptive name of the starting trigger (e.g. "Prophecy Lectern", "Lost Piglin")
     */
    String getStartTrigger();

    /**
     * Gets the plugin instance
     * @return The plugin instance
     */
    RVNKQuests getPlugin();

    /**
     * Creates and returns a list of listeners appropriate for a given quest state.
     * This is the core of the state-based event handling system - each state has
     * its own set of event listeners that are registered when the quest enters that state.
     *
     * <p>For per-player quests, listeners should check player-specific state
     * using {@link #getStateForPlayer(Player)} within their event handlers.</p>
     *
     * @param state The quest state to create listeners for
     * @return List of listeners for the given state
     */
    List<Listener> createListenersForState(QuestState state);
}
