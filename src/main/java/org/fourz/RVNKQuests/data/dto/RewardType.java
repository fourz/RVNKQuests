package org.fourz.RVNKQuests.data.dto;

/**
 * Enumeration of supported quest reward types.
 * 
 * <p>Rewards can be given upon quest completion or at
 * specific milestones during the quest.</p>
 */
public enum RewardType {
    /**
     * Item reward - gives player item(s).
     * Value: Item serialization string or ID
     */
    ITEM,
    
    /**
     * Experience points reward.
     * Value: Amount of XP
     */
    EXPERIENCE,
    
    /**
     * Currency/token reward (via TokenEconomy).
     * Value: Amount of currency
     */
    CURRENCY,
    
    /**
     * Permission grant (via LuckPerms).
     * Value: Permission node
     */
    PERMISSION,
    
    /**
     * Command execution reward.
     * Value: Command to execute (with placeholders)
     */
    COMMAND,
    
    /**
     * Title/achievement display.
     * Value: Title configuration
     */
    TITLE,
    
    /**
     * Unlocks another quest.
     * Value: Quest ID to unlock
     */
    QUEST_UNLOCK,
    
    /**
     * Lore entry reward (via RVNKLore).
     * Value: Lore entry ID
     */
    LORE,
    
    /**
     * RNG lore item reward — rolls a weighted-random item from an RVNKLore pool.
     * Metadata: pool_id (required), rarity_tier (optional filter)
     */
    RNG_ITEM,

    /**
     * Custom reward with plugin-defined behavior.
     * Value: Custom identifier
     */
    CUSTOM
}
