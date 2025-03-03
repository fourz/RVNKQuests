package org.fourz.RVNKQuests;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.RVNKQuests.quest.QuestManager;
import org.fourz.RVNKQuests.util.Debug;
import org.fourz.RVNKQuests.config.ConfigManager;
import org.fourz.RVNKQuests.command.CommandManager;
import org.fourz.RVNKQuests.util.EnvironmentEffects;
import java.util.logging.Level;

public class RVNKQuests extends JavaPlugin {
    private QuestManager questManager;
    private Debug debugger;
    private ConfigManager configManager;
    private CommandManager commandManager;
    
    @Override
    public void onEnable() {
        // Initialize ConfigManager first
        configManager = new ConfigManager(this);
        debugger = new Debug(this, "RVNKQuests", configManager.getLogLevel()) {};
        debugger.info("Initializing RVNKQuests...");
        
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
}
