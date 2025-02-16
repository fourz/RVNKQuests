package org.fourz.RVNKQuests.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.fourz.RVNKQuests.RVNKQuests;

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
        plugin.getDebugger().debug("Configuration loaded successfully");
    }

    public String getLogLevel() {
        String level = config.getString("general.logLevel", "INFO");
        plugin.getDebugger().debug("Retrieved log level: " + level);
        return level;
    }

    public String getStorageType() {
        String type = config.getString("storage.type", "sqlite");
        plugin.getDebugger().debug("Retrieved storage type: " + type);
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
