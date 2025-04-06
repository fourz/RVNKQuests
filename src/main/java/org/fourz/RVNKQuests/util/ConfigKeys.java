package org.fourz.RVNKQuests.util;

/**
 * Centralized repository of configuration key constants.
 * 
 * This utility class consolidates all configuration keys used throughout the plugin,
 * which provides several benefits:
 * - Prevents typos in config key strings
 * - Makes refactoring config structure easier
 * - Provides a single reference for all available settings
 * - Enables IDE auto-completion for config keys
 * 
 * Keys are organized by their functional area and follow a hierarchical naming pattern.
 */
public final class ConfigKeys {
    // General plugin settings
    public static final String GENERAL_PREFIX = "general.";
    public static final String GENERAL_LOG_LEVEL = GENERAL_PREFIX + "logLevel"; 
    public static final String GENERAL_DEBUG_MODE = GENERAL_PREFIX + "debugMode";
    
    // Quest system settings
    public static final String QUESTS_PREFIX = "quests.";
    public static final String QUESTS_ENABLE_SUFFIX = ".enable";
    public static final String QUESTS_ENABLED = QUESTS_PREFIX + "enabled";
    public static final String QUESTS_CLEAR_ON_DEATH = QUESTS_PREFIX + "clearOnDeath";
    
    // Lore database settings
    public static final String LORE_PREFIX = "lore_database.";
    public static final String LORE_ENABLED = LORE_PREFIX + "enabled";
    public static final String LORE_TYPE = LORE_PREFIX + "type";
    public static final String LORE_DATABASE_PATH = LORE_PREFIX + "path";
    
    // Reward settings
    public static final String REWARDS_PREFIX = "rewards.";
    public static final String REWARDS_SCALE_WITH_DIFFICULTY = REWARDS_PREFIX + "scaleWithDifficulty";
    
    /**
     * Constructs a quest-specific configuration key
     * 
     * @param questId The quest identifier
     * @param key The setting key for this quest
     * @return The full configuration key
     */
    public static String forQuest(String questId, String key) {
        return QUESTS_PREFIX + questId + "." + key;
    }
    
    /**
     * Returns the enable key for a specific quest
     * 
     * @param questId The quest identifier
     * @return The configuration key for enabling/disabling this quest
     */
    public static String questEnableKey(String questId) {
        return QUESTS_PREFIX + questId + QUESTS_ENABLE_SUFFIX;
    }
    
    // Private constructor to prevent instantiation
    private ConfigKeys() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }
}
