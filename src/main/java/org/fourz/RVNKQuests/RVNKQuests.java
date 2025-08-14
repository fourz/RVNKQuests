package org.fourz.RVNKQuests;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.RVNKQuests.command.CommandManager;
import org.fourz.RVNKQuests.config.ConfigManager;
import org.fourz.RVNKQuests.quest.QuestManager;
import org.fourz.RVNKQuests.util.log.LogManager;
import org.fourz.RVNKQuests.util.log.FZLogger;
import org.fourz.RVNKQuests.lore.LoreDatabase;

import java.util.logging.Level;

/**
 * Main plugin class for RVNKQuests, a dynamic narrative quest system for Bukkit/Spigot servers.
 * 
 * The plugin architecture follows a manager-based approach:
 * - ConfigManager handles all configuration access
 * - QuestManager manages quest registration, state tracking and event handling
 * - CommandManager handles player commands and subcommands
 * - LoreDatabase (optional) stores narrative content for quests
 * 
 * Each manager is independent but can be accessed through this main class,
 * providing a clean API for extensions or add-ons.
 */
public class RVNKQuests extends JavaPlugin {
    // New logging system
    private FZLogger logger;
    
    private ConfigManager configManager;
    private QuestManager questManager;
    private CommandManager commandManager;
    private LoreDatabase loreDatabase;
    
    @Override
    public void onEnable() {
        // Initialize both new and legacy loggers
        logger = LogManager.getInstance(this, getClass());
        
        logger.info("Initializing RVNKQuests plugin");
        
        try {
            // Load configuration
            configManager = new ConfigManager(this);
            
            // Update log level from config
            updateGlobalLogLevel(configManager.getLogLevel());
            
            // Initialize managers in correct dependency order
            questManager = new QuestManager(this);
            commandManager = new CommandManager(this);
            
            // Initialize lore database if enabled
            if (configManager.isLoreDatabaseEnabled()) {
                loreDatabase = new LoreDatabase(this);
                logger.info("Lore database initialized");
            } else {
                logger.info("Lore database disabled in config");
            }
            
            // Register quests
            questManager.initializeQuests();
            
            logger.info("RVNKQuests plugin enabled successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize RVNKQuests plugin", e);
        }
    }
    
    @Override
    public void onDisable() {
        logger.info("Disabling RVNKQuests plugin");
        
        try {
            if (questManager != null) {
                questManager.cleanupQuests();
            }
            
            if (loreDatabase != null) {
                loreDatabase.close();
            }
            
            logger.info("RVNKQuests plugin disabled successfully");
        } catch (Exception e) {
            logger.error("Error during plugin shutdown", e);
        }
        
        // Clean up loggers on shutdown
        LogManager.clearLoggers(this);
    }
    
    /**
     * Gets the FZLogger instance for this plugin.
     * @return The FZLogger instance
     */
    public FZLogger getFZLogger() {
        return logger;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public QuestManager getQuestManager() {
        return questManager;
    }
    
    /**
     * Gets the lore database instance
     * @return The lore database or null if not enabled
     */
    public LoreDatabase getLoreDatabase() {
        return loreDatabase;
    }
    
    /**
     * Checks if this plugin has an active lore database
     * @return true if the lore database is enabled and initialized
     */
    public boolean hasLoreDatabase() {
        return loreDatabase != null;
    }
    
    /**
     * Updates the log level across all plugin components.
     * This ensures consistent logging behavior throughout the plugin.
     * 
     * @param level The new logging level to apply
     */
    public void updateGlobalLogLevel(Level level) {
        logger.info("Updating global log level to: " + level.getName());
        logger.setLogLevel(level);
        
        // Update log level for all managers
        if (configManager != null) {
            configManager.updateDebugLevel(level);
        }
        
        if (questManager != null) {
            questManager.updateDebugLevel(level);
        }
        
        if (commandManager != null) {
            commandManager.updateDebugLevel(level);
        }
        
        if (loreDatabase != null) {
            loreDatabase.updateDebugLevel(level);
        }
    }
}
