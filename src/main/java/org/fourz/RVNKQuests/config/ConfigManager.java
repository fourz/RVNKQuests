package org.fourz.RVNKQuests.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.fourz.RVNKQuests.RVNKQuests;
import java.util.logging.Level;

public class ConfigManager {
    private final RVNKQuests plugin;
    private FileConfiguration config;

    public ConfigManager(RVNKQuests plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    // Called after Debug is initialized
    public void initDebugLogging() {
        plugin.getDebugger().debug("Configuration system initialized");
    }

    public Level getLogLevel() {
        String level = config.getString("general.logLevel", "INFO");
        if (level.equalsIgnoreCase("DEBUG")) {
            return Level.FINE;
        }
        return Level.parse(level.toUpperCase());
    }

    public String getStorageType() {
        String type = config.getString("storage.type", "sqlite");
        return type;
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();
    }
}
