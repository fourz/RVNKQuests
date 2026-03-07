package org.fourz.RVNKQuests.data.dto;

/**
 * Enumeration of supported quest objective types.
 * 
 * <p>Each type defines a category of action the player must perform
 * to complete an objective.</p>
 */
public enum ObjectiveType {
    /**
     * Kill a certain number of entities.
     * Target: Entity type (e.g., "ZOMBIE", "PIGLIN")
     */
    KILL,
    
    /**
     * Collect a certain number of items.
     * Target: Item type (e.g., "DIAMOND", "GOLD_INGOT")
     */
    COLLECT,
    
    /**
     * Reach a specific location.
     * Target: Location identifier or coordinates
     */
    REACH,
    
    /**
     * Talk to an NPC or entity.
     * Target: NPC identifier or entity UUID
     */
    TALK_TO,
    
    /**
     * Interact with a block or object.
     * Target: Block type or interaction ID
     */
    INTERACT,
    
    /**
     * Craft a certain number of items.
     * Target: Item type to craft
     */
    CRAFT,
    
    /**
     * Mine/break a certain number of blocks.
     * Target: Block type
     */
    MINE,
    
    /**
     * Place a certain number of blocks.
     * Target: Block type
     */
    PLACE,
    
    /**
     * Use an item a certain number of times.
     * Target: Item type
     */
    USE_ITEM,
    
    /**
     * Deliver items to an NPC or location.
     * Target: Delivery point identifier
     */
    DELIVER,
    
    /**
     * Explore or discover a location.
     * Target: Discovery point identifier
     */
    DISCOVER,
    
    /**
     * Custom objective with plugin-defined behavior.
     * Target: Custom identifier
     */
    CUSTOM
}
