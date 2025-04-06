package org.fourz.RVNKQuests.quest;

/**
 * Represents the possible states of a quest during its lifecycle.
 * 
 * The quest state progression typically follows this sequence:
 * 1. NOT_STARTED - Initial state, quest exists but player hasn't discovered it
 * 2. TRIGGER_FOUND - Player has discovered the quest trigger but hasn't started
 * 3. QUEST_ACTIVE - Player has accepted the quest and is working on objectives
 * 4. OBJECTIVE_FOUND - Player has located the primary objective but hasn't completed it
 * 5. COMPLETED - Player has successfully completed all quest requirements
 * 
 * Different listeners and behaviors are activated based on each state.
 */
public enum QuestState {
    /**
     * Quest exists but player hasn't discovered it yet
     */
    NOT_STARTED,
    
    /**
     * Player has discovered the quest trigger (book, NPC, etc.)
     */
    TRIGGER_FOUND,
    
    /**
     * Quest is actively being pursued by the player
     */
    QUEST_ACTIVE,
    
    /**
     * Player has located the primary objective
     */
    OBJECTIVE_FOUND,
    
    /**
     * Quest has been successfully completed
     */
    COMPLETED
}
