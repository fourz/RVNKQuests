package org.fourz.RVNKQuests.quest;

import org.bukkit.entity.Player;    
import org.bukkit.Location;
import org.fourz.RVNKQuests.RVNKQuests;
import org.bukkit.event.Listener;
import java.util.List;

/**
 * Core interface defining the structure and lifecycle of quests.
 * 
 * Quests in this system are state-based with dynamic event handling:
 * - Each quest has a unique identifier and display name
 * - Quests progress through defined states (see QuestState enum)
 * - Different event listeners are active based on the quest's current state
 * - Quests manage their own resources and cleanup
 * 
 * Implementations should maintain their state and handle the progression
 * logic between different states based on in-game events.
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
     * Returns the current state of the quest
     * @return Current quest state
     */
    QuestState getCurrentState();
    
    /**
     * Updates the quest's state and triggers any necessary changes
     * @param newState The new state to advance to
     */
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
     * Creates and returns a list of listeners appropriate for the current quest state.
     * This is the core of the state-based event handling system - each state has
     * its own set of event listeners that are registered when the quest enters that state.
     * 
     * @param state The current state of the quest
     * @return List of listeners for the given state
     */
    List<Listener> createListenersForState(QuestState state);
}
