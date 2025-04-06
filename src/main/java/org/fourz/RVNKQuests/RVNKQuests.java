package org.fourz.RVNKQuests;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.RVNKQuests.command.CommandManager;
import org.fourz.RVNKQuests.config.ConfigManager;
import org.fourz.RVNKQuests.quest.QuestManager;
import org.fourz.RVNKQuests.util.Debug;
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
    private Debug debugger;
    private ConfigManager configManager;
    private QuestManager questManager;
    private CommandManager commandManager;
    private LoreDatabase loreDatabase;
    
    @Override
    public void onEnable() {
        // Initialize the debugger first with default INFO level
        debugger = Debug.createDebugger(this, "RVNKQuests", Level.INFO);
        debugger.info("Initializing RVNKQuests plugin");
        
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
                debugger.info("Lore database initialized");
            } else {
                debugger.info("Lore database disabled in config");
            }
            
            // Register quests
            questManager.initializeQuests();
            
            debugger.info("RVNKQuests plugin enabled successfully");
        } catch (Exception e) {
            debugger.error("Failed to initialize RVNKQuests plugin", e);
        }
    }
    
    @Override
    public void onDisable() {
        debugger.info("Disabling RVNKQuests plugin");
        
        try {
            if (questManager != null) {
                questManager.cleanupQuests();
            }
            
            if (loreDatabase != null) {
                loreDatabase.close();
            }
            
            debugger.info("RVNKQuests plugin disabled successfully");
        } catch (Exception e) {
            debugger.error("Error during plugin shutdown", e);
        }
    }
    
    public Debug getDebugger() {
        return debugger;
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
        debugger.info("Updating global log level to: " + level.getName());
        debugger.setLogLevel(level);
        
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
