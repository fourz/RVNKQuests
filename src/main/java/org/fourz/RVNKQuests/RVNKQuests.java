package org.fourz.RVNKQuests;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.RVNKQuests.quest.QuestManager;
import org.fourz.RVNKQuests.util.Debug;
import org.fourz.RVNKQuests.config.ConfigManager;
import org.fourz.RVNKQuests.command.CommandManager;
import org.fourz.RVNKQuests.util.EnvironmentEffects;
import org.fourz.RVNKQuests.lore.LoreDatabase;
import java.util.logging.Level;

public class RVNKQuests extends JavaPlugin {
    private QuestManager questManager;
    private Debug debugger;
    private ConfigManager configManager;
    private CommandManager commandManager;
    private LoreDatabase loreDatabase;
    
    @Override
    public void onEnable() {
        // Initialize ConfigManager first
        configManager = new ConfigManager(this);
        
        // Initialize the plugin debugger with the configured log level
        Level logLevel = configManager.getLogLevel();
        debugger = new Debug(this, "RVNKQuests", logLevel) {};
        debugger.info("Initializing RVNKQuests... (Log level: " + logLevel.getName() + ")");
        
        try {
            initializeManagers();
            debugger.info("RVNKQuests has been enabled!");
        } catch (Exception e) {
            debugger.error("Failed to initialize plugin", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void initializeManagers() {
        EnvironmentEffects.init(this);
        questManager = new QuestManager(this);
        questManager.initializeQuests();
        commandManager = new CommandManager(this);
        
        // Initialize lore database if enabled in config
        if (configManager.getConfig().getBoolean("lore_database.enabled", false)) {
            debugger.info("Initializing lore database...");
            try {
                loreDatabase = new LoreDatabase(this);
                loreDatabase.initialize();
            } catch (Exception e) {
                debugger.error("Failed to initialize lore database", e);
                loreDatabase = null;
            }
        }
    }

    @Override
    public void onDisable() {
        if (debugger == null) {
            getLogger().warning("Debugger was null during shutdown");
            return;
        }

        debugger.info("RVNKQuests is shutting down...");
        
        try {
            cleanupManagers();
        } catch (Exception e) {
            debugger.error("Failed to cleanup managers", e);
        } finally {
            debugger.info("RVNKQuests has been disabled!");
            debugger = null;
        }
    }

    private void cleanupManagers() {
        if (questManager != null) {
            questManager.cleanupQuests();
            questManager = null;
        }

        if (commandManager != null) {
            commandManager = null;
        }

        if (configManager != null) {
            configManager = null;
        }
        
        if (loreDatabase != null) {
            loreDatabase.shutdown();
            loreDatabase = null;
        }
    }

    /**
     * Update the log level of all debuggers in the plugin
     * Used when log level changes via command or config reload
     * @param newLevel The new log level to set
     */
    public void updateGlobalLogLevel(Level newLevel) {
        if (debugger != null) {
            debugger.setLogLevel(newLevel);
            debugger.info("Global log level updated to: " + newLevel.getName());
        }
        
        // Update log level in all managers that have debuggers
        if (questManager != null) {
            questManager.updateDebugLevel(newLevel);
        }
        
        if (loreDatabase != null) {
            loreDatabase.updateDebugLevel(newLevel);
        }
        
        if (commandManager != null) {
            commandManager.updateDebugLevel(newLevel);
        }
    }

    public QuestManager getQuestManager() {
        return questManager;
    }

    public Debug getDebugger() {
        return debugger;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public CommandManager getCommandManager() {
        return commandManager;
    }
    
    /**
     * Checks if the lore database is available
     * @return true if lore database is enabled and initialized
     */
    public boolean hasLoreDatabase() {
        return loreDatabase != null;
    }
    
    /**
     * Gets the lore database instance
     * @return the lore database or null if not enabled
     */
    public LoreDatabase getLoreDatabase() {
        return loreDatabase;
    }
}
