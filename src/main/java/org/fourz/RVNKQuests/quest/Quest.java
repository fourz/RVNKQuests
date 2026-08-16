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
     * Advances the quest's state for a specific player.
     *
     * <p>This is the path for automatic, component-driven progress (triggers and
     * objectives). Changes are serialized per player and may only move the quest
     * <em>forward</em> along the linear progression, so two components firing in the
     * same tick cannot clobber each other (#1853). Use
     * {@link #setStateForPlayer(UUID, QuestState)} for admin operations that must be
     * able to set any state.</p>
     *
     * @param playerUuid The player's UUID
     * @param newState The new state to advance to
     * @return CompletableFuture that completes when state is updated
     */
    CompletableFuture<Void> advanceStateForPlayer(UUID playerUuid, QuestState newState);

    /**
     * Sets the quest's state for a specific player, skipping the forward-progress guard.
     *
     * <p>For explicit operations — admin state overrides, reset, abandon, pause and
     * resume — which legitimately move a quest backwards.</p>
     *
     * @implSpec The default implementation <b>delegates to
     * {@link #advanceStateForPlayer(UUID, QuestState)} and therefore does NOT skip any guard</b>.
     * It exists only so that adding this method did not break existing implementors; it is a
     * compatibility shim, not working behaviour. Any implementation that enforces a
     * forward-progress rule <b>must override this method</b> to be correct —
     * {@code AbstractQuest} does, routing both through one serialized path with the guard
     * disabled. Relying on the default will silently apply the guard and make a reset or an
     * admin state override a no-op.
     *
     * @param playerUuid The player's UUID
     * @param newState The state to set
     * @return CompletableFuture that completes when state is updated
     */
    default CompletableFuture<Void> setStateForPlayer(UUID playerUuid, QuestState newState) {
        return advanceStateForPlayer(playerUuid, newState);
    }

    /**
     * Prerequisite quest IDs that are not COMPLETED for this player, in declaration order.
     * Empty means nothing is blocking.
     *
     * <p>Used by {@code /quest state} to refuse a mid-chain state and name the blocker (#1884).
     * {@code /quest debug setstate} deliberately does not consult it — that command advertises
     * "bypasses normal state transitions" and now means it.</p>
     *
     * <p>The default reports no blockers, which is correct for a quest type that has no
     * prerequisite concept: it can only make the command more permissive, never wrongly refuse.
     * {@code AbstractQuest} overrides it against the quest's declared {@code prerequisites}.</p>
     *
     * @param playerUuid The player's UUID
     * @return unmet prerequisite quest IDs; empty if all satisfied or none declared
     */
    default CompletableFuture<java.util.List<String>> getUnmetPrerequisites(UUID playerUuid) {
        return CompletableFuture.completedFuture(java.util.List.of());
    }

    /**
     * Updates the quest's state and triggers any necessary changes.
     * @deprecated Use {@link #advanceStateForPlayer(UUID, QuestState)} for per-player state.
     * @param newState The new state to advance to
     */
    @Deprecated
    void advanceState(QuestState newState);

    /**
     * Pauses the quest for a player. Only valid from QUEST_ACTIVE or OBJECTIVE_FOUND.
     * Advances state to PAUSED and snapshots the current state for later resume.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture completing with true if paused, false if state is ineligible
     */
    CompletableFuture<Boolean> pauseForPlayer(UUID playerUuid);

    /**
     * Resumes a paused quest. Only valid from PAUSED state.
     * Restores the pre-pause state (QUEST_ACTIVE or OBJECTIVE_FOUND); defaults to
     * QUEST_ACTIVE if the snapshot was lost (e.g. server restarted while paused).
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture completing with true if resumed, false if not PAUSED
     */
    CompletableFuture<Boolean> resumeForPlayer(UUID playerUuid);

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
