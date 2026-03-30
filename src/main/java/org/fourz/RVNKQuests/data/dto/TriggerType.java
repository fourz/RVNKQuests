package org.fourz.RVNKQuests.data.dto;

/**
 * Enumeration of supported quest trigger types.
 * Triggers define how a quest is discovered or started by a player.
 */
public enum TriggerType {
    /** Spawn a mob when player enters proximity. Config: entity_type, custom_name, world, radius */
    PROXIMITY_MOB_SPAWN,

    /** Interact with a structure or block. Config: block_type, world, location */
    STRUCTURE_INTERACT,

    /** Discover an item (book, artifact). Config: item_type, world, location */
    ITEM_DISCOVERY,

    /** Enter proximity of an entity. Config: entity_type, world, radius */
    ENTITY_PROXIMITY,

    /** Enter proximity of a fixed location. Config: world, x, y, z, radius */
    LOCATION_PROXIMITY,

    /** World event triggers quest. Config: event_type, world */
    WORLD_EVENT,

    /** Manual command trigger. Config: command */
    COMMAND,

    /** Custom trigger with plugin-defined behavior. Config: custom handler class */
    CUSTOM
}
