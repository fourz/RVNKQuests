package org.fourz.RVNKQuests;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.RVNKQuests.quest.QuestManager;
import org.fourz.RVNKQuests.util.Debug;
import org.fourz.RVNKQuests.config.ConfigManager;
import java.util.logging.Level;

public class RVNKQuests extends JavaPlugin {
    private QuestManager questManager;
    private Debug debugger;
    private ConfigManager configManager;
    
    @Override
    public void onEnable() {
        // Initialize ConfigManager first
        debugger = new Debug(this, "RVNKQuests", Level.INFO) {};
        configManager = new ConfigManager(this);
                
        debugger.setLogLevel(Debug.getLevel(configManager.getLogLevel()));        
        debugger.info("Initializing RVNKQuests...");
        questManager = new QuestManager(this);
        questManager.initializeQuests();
        debugger.info("RVNKQuests has been enabled!");
    }

    @Override
    public void onDisable() {
        if (questManager != null) {
            questManager.cleanupQuests();
        }
        debugger.info("RVNKQuests has been disabled!");
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
}
