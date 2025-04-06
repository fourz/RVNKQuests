package org.fourz.RVNKQuests.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.util.Debug;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class ConfigManager {
    private final RVNKQuests plugin;
    private final Debug debug;
    private FileConfiguration config;
    private File configFile;
    private Map<String, Boolean> questEnableStatus = new HashMap<>();
    private Map<String, YamlConfiguration> yamlFiles = new HashMap<>();
    private Map<String, File> yamlFilePaths = new HashMap<>();
    
    // Constants for configuration keys
    public static final String KEY_LORE_DATABASE_ENABLED = "lore_database.enabled";
    public static final String KEY_LORE_DATABASE_TYPE = "lore_database.type";
    public static final String KEY_QUESTS_PREFIX = "quests.";
    public static final String KEY_ENABLE_SUFFIX = ".enable";
    
    public ConfigManager(RVNKQuests plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "ConfigManager", Level.INFO);
        setupConfig();
        loadQuestEnableStatus();
    }

    /**
     * Sets up the configuration file, creating it if it doesn't exist
     */
    private void setupConfig() {
        if (configFile == null) {
            // First try to locate in plugin folder
            configFile = new File(plugin.getDataFolder(), "config.yml");
        }

        if (!configFile.exists()) {
            // Try alternate location
            File alternateConfig = new File("config", "config.yml");
            if (alternateConfig.exists()) {
                configFile = alternateConfig;
                debug.info("Using alternate config location: " + alternateConfig.getAbsolutePath());
            } else {
                // Create default config
                plugin.getDataFolder().mkdirs();
                saveDefaultConfig();
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        debug.info("Configuration loaded from: " + configFile.getAbsolutePath());
        
        // Check for and load alternate config files
        loadAlternateConfigs();
    }
    
    /**
     * Loads alternate configuration files from the config directory
     */
    private void loadAlternateConfigs() {
        yamlFiles.clear();
        yamlFilePaths.clear();
        
        // Look for alternate config in /plugins/RVNKQuests/config/ directory
        File configDir = new File(plugin.getDataFolder(), "config");
        if (configDir.exists() && configDir.isDirectory()) {
            File[] files = configDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    YamlConfiguration yamlConfig = YamlConfiguration.loadConfiguration(file);
                    String fileName = file.getName();
                    yamlFiles.put(fileName, yamlConfig);
                    yamlFilePaths.put(fileName, file);
                    debug.info("Loaded additional config file: " + fileName);
                }
            }
        }
        
        // Also check for alternate configs in /config/ directory
        File serverConfigDir = new File("config");
        if (serverConfigDir.exists() && serverConfigDir.isDirectory()) {
            File rvnkDir = new File(serverConfigDir, "RVNKQuests");
            if (rvnkDir.exists() && rvnkDir.isDirectory()) {
                File[] files = rvnkDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
                if (files != null) {
                    for (File file : files) {
                        YamlConfiguration yamlConfig = YamlConfiguration.loadConfiguration(file);
                        String fileName = file.getName();
                        yamlFiles.put(fileName, yamlConfig);
                        yamlFilePaths.put(fileName, file);
                        debug.info("Loaded additional config file from server config: " + fileName);
                    }
                }
            }
        }
    }

    /**
     * Saves the default configuration file from resources
     */
    private void saveDefaultConfig() {
        debug.info("Creating default configuration file");
        try {
            // Ensure directory exists
            plugin.getDataFolder().mkdirs();
            
            // Copy from resources
            try (InputStream in = plugin.getResource("config.yml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    debug.info("Default configuration file created");
                } else {
                    debug.warning("Default config.yml not found in resources");
                }
            }
        } catch (IOException e) {
            debug.error("Could not create default configuration file", e);
        }
    }

    /**
     * Reloads the configuration from disk
     */
    public void reloadConfig() {
        debug.debug("Reloading configuration");
        config = YamlConfiguration.loadConfiguration(configFile);
        loadAlternateConfigs();
        loadQuestEnableStatus();
        debug.debug("Configuration reloaded");
    }

    /**
     * Saves the current configuration to disk
     * @return true if the configuration was saved successfully
     */
    public boolean saveConfig() {
        debug.debug("Saving configuration to disk");
        try {
            // Save main config
            config.save(configFile);
            debug.debug("Main configuration saved to: " + configFile.getAbsolutePath());
            
            // Save all additional YAML files
            for (String fileName : yamlFiles.keySet()) {
                File file = yamlFilePaths.get(fileName);
                if (file != null) {
                    YamlConfiguration yamlConfig = yamlFiles.get(fileName);
                    yamlConfig.save(file);
                    debug.debug("Additional config saved: " + fileName);
                }
            }
            
            return true;
        } catch (IOException e) {
            debug.error("Failed to save configuration", e);
            return false;
        }
    }
    
    /**
     * Sets a value in the configuration
     * @param path The path to set
     * @param value The value to set
     */
    public void setConfigValue(String path, Object value) {
        debug.debug("Setting config value: " + path);
        config.set(path, value);
    }
    
    /**
     * Sets a quest-specific configuration value
     * @param questId The quest identifier
     * @param key The configuration key (without quest prefix)
     * @param value The value to set
     */
    public void setQuestConfigValue(String questId, String key, Object value) {
        String fullPath = KEY_QUESTS_PREFIX + questId + "." + key;
        setConfigValue(fullPath, value);
    }
    
    /**
     * Sets the enabled status for a quest
     * @param questId The quest identifier
     * @param enabled Whether the quest should be enabled
     */
    public void setQuestEnabled(String questId, boolean enabled) {
        String fullPath = KEY_QUESTS_PREFIX + questId + KEY_ENABLE_SUFFIX;
        setConfigValue(fullPath, enabled);
        questEnableStatus.put(questId, enabled);
        debug.debug("Quest " + questId + " set to " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Sets a value in a specific YAML file
     * @param fileName The name of the YAML file
     * @param path The path to set
     * @param value The value to set
     * @return true if the file exists and was modified
     */
    public boolean setYamlValue(String fileName, String path, Object value) {
        YamlConfiguration yaml = yamlFiles.get(fileName);
        if (yaml == null) {
            debug.warning("Attempted to set value in non-existent YAML file: " + fileName);
            return false;
        }
        
        yaml.set(path, value);
        debug.debug("Set value in YAML file " + fileName + ": " + path);
        return true;
    }
    
    /**
     * Creates a new YAML configuration file
     * @param fileName The name of the file to create
     * @return true if the file was created successfully
     */
    public boolean createYamlFile(String fileName) {
        if (yamlFiles.containsKey(fileName)) {
            debug.warning("YAML file already exists: " + fileName);
            return false;
        }
        
        // Ensure plugin config directory exists
        File configDir = new File(plugin.getDataFolder(), "config");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        
        // Create the file
        File file = new File(configDir, fileName);
        YamlConfiguration yaml = new YamlConfiguration();
        
        try {
            yaml.save(file);
            yamlFiles.put(fileName, yaml);
            yamlFilePaths.put(fileName, file);
            debug.info("Created new YAML file: " + fileName);
            return true;
        } catch (IOException e) {
            debug.error("Failed to create YAML file: " + fileName, e);
            return false;
        }
    }
    
    /**
     * Saves a specific YAML configuration file
     * @param fileName The name of the file to save
     * @return true if the file was saved successfully
     */
    public boolean saveYamlFile(String fileName) {
        YamlConfiguration yaml = yamlFiles.get(fileName);
        File file = yamlFilePaths.get(fileName);
        
        if (yaml == null || file == null) {
            debug.warning("Cannot save non-existent YAML file: " + fileName);
            return false;
        }
        
        try {
            yaml.save(file);
            debug.debug("Saved YAML file: " + fileName);
            return true;
        } catch (IOException e) {
            debug.error("Failed to save YAML file: " + fileName, e);
            return false;
        }
    }
    
    /**
     * Returns the current configuration
     * @return Current FileConfiguration
     */
    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }
    
    /**
     * Gets a YAML file from the loaded alternates
     * @param fileName The name of the YAML file
     * @return The YAML configuration or null if not found
     */
    public YamlConfiguration getYamlFile(String fileName) {
        return yamlFiles.get(fileName);
    }
    
    /**
     * Checks if a YAML file is loaded
     * @param fileName The name of the YAML file
     * @return true if the file is loaded
     */
    public boolean hasYamlFile(String fileName) {
        return yamlFiles.containsKey(fileName);
    }
    
    /**
     * Gets the names of all loaded YAML files
     * @return Array of file names
     */
    public String[] getYamlFileNames() {
        return yamlFiles.keySet().toArray(new String[0]);
    }
    
    /**
     * Checks if the lore database is enabled in configuration
     * @return true if the lore database should be enabled
     */
    public boolean isLoreDatabaseEnabled() {
        boolean defaultValue = true;
        
        // First check in main config
        if (config.contains(KEY_LORE_DATABASE_ENABLED)) {
            return config.getBoolean(KEY_LORE_DATABASE_ENABLED);
        }
        
        // Check in alternate config files
        for (YamlConfiguration yaml : yamlFiles.values()) {
            if (yaml.contains(KEY_LORE_DATABASE_ENABLED)) {
                return yaml.getBoolean(KEY_LORE_DATABASE_ENABLED);
            }
        }
        
        return defaultValue;
    }
    
    /**
     * Gets the type of lore database to use
     * @return The lore database type (e.g., "yaml", "sqlite")
     */
    public String getLoreDatabaseType() {
        String defaultValue = "yaml";
        
        // First check in main config
        if (config.contains(KEY_LORE_DATABASE_TYPE)) {
            return config.getString(KEY_LORE_DATABASE_TYPE, defaultValue);
        }
        
        // Check in alternate config files
        for (YamlConfiguration yaml : yamlFiles.values()) {
            if (yaml.contains(KEY_LORE_DATABASE_TYPE)) {
                return yaml.getString(KEY_LORE_DATABASE_TYPE, defaultValue);
            }
        }
        
        return defaultValue;
    }

    /**
     * Gets the log level from configuration
     * @return The configured log level
     */
    public Level getLogLevel() {
        String logLevelStr = getConfig().getString("general.logLevel", "INFO").toUpperCase();
        
        switch (logLevelStr) {
            case "DEBUG":
            case "FINE":
                return Level.FINE;
            case "INFO":
                return Level.INFO;
            case "WARNING":
                return Level.WARNING;
            case "SEVERE":
                return Level.SEVERE;
            case "OFF":
                return Level.OFF;
            default:
                debug.warning("Unknown log level in config: " + logLevelStr + ", defaulting to INFO");
                return Level.INFO;
        }
    }
    
    /**
     * Updates the debug level for this manager
     * @param level New log level
     */
    public void updateDebugLevel(Level level) {
        debug.setLogLevel(level);
        debug.debug("ConfigManager log level updated to: " + level.getName());
    }
    
    /**
     * Loads the enable/disable status for all quests from the configuration
     */
    private void loadQuestEnableStatus() {
        questEnableStatus.clear();
        
        // Check for quests section in standard config
        if (config.isConfigurationSection("quests")) {
            for (String questId : config.getConfigurationSection("quests").getKeys(false)) {
                boolean enabled = config.getBoolean(KEY_QUESTS_PREFIX + questId + KEY_ENABLE_SUFFIX, true);
                questEnableStatus.put(questId, enabled);
                debug.debug("Quest " + questId + " is " + (enabled ? "enabled" : "disabled"));
            }
        }
        
        // Check for quest config in alternate YAML files
        for (YamlConfiguration yaml : yamlFiles.values()) {
            if (yaml.isConfigurationSection("quests")) {
                for (String questId : yaml.getConfigurationSection("quests").getKeys(false)) {
                    boolean enabled = yaml.getBoolean(KEY_QUESTS_PREFIX + questId + KEY_ENABLE_SUFFIX, true);
                    questEnableStatus.put(questId, enabled);
                    debug.debug("Alternate config: Quest " + questId + " is " + (enabled ? "enabled" : "disabled"));
                }
            }
        }
    }
    
    /**
     * Gets a configuration value from any loaded configuration file
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The value or default if not found
     */
    public Object getConfigValue(String path, Object defaultValue) {
        // First check main config
        if (config.contains(path)) {
            return config.get(path, defaultValue);
        }
        
        // Then check alternate configs
        for (YamlConfiguration yaml : yamlFiles.values()) {
            if (yaml.contains(path)) {
                return yaml.get(path, defaultValue);
            }
        }
        
        return defaultValue;
    }
    
    /**
     * Gets a string from any loaded configuration file
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The string value or default if not found
     */
    public String getConfigString(String path, String defaultValue) {
        Object value = getConfigValue(path, defaultValue);
        return value != null ? value.toString() : defaultValue;
    }
    
    /**
     * Gets a boolean from any loaded configuration file
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The boolean value or default if not found
     */
    public boolean getConfigBoolean(String path, boolean defaultValue) {
        Object value = getConfigValue(path, defaultValue);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
    
    /**
     * Gets an integer from any loaded configuration file
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The integer value or default if not found
     */
    public int getConfigInt(String path, int defaultValue) {
        Object value = getConfigValue(path, defaultValue);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    /**
     * Gets a double from any loaded configuration file
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The double value or default if not found
     */
    public double getConfigDouble(String path, double defaultValue) {
        Object value = getConfigValue(path, defaultValue);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }
    
    /**
     * Checks if a quest is enabled in the configuration
     * @param questId The quest identifier
     * @return true if the quest is enabled or not configured (default is enabled)
     */
    public boolean isQuestEnabled(String questId) {
        return questEnableStatus.getOrDefault(questId, true);
    }
    
    /**
     * Gets a map of all quest IDs and their enabled status
     * @return Map of quest IDs to their enabled status
     */
    public Map<String, Boolean> getQuestEnableStatus() {
        return new HashMap<>(questEnableStatus);
    }
    
    /**
     * Gets a quest-specific configuration value
     * @param questId The quest identifier
     * @param key The configuration key (without quest prefix)
     * @param defaultValue The default value if not found
     * @return The configuration value or default if not found
     */
    public Object getQuestConfigValue(String questId, String key, Object defaultValue) {
        String fullPath = KEY_QUESTS_PREFIX + questId + "." + key;
        return getConfigValue(fullPath, defaultValue);
    }
    
    /**
     * Gets a quest-specific string value
     * @param questId The quest identifier
     * @param key The configuration key (without quest prefix)
     * @param defaultValue The default value if not found
     * @return The string value or default if not found
     */
    public String getQuestConfigString(String questId, String key, String defaultValue) {
        String fullPath = KEY_QUESTS_PREFIX + questId + "." + key;
        return getConfigString(fullPath, defaultValue);
    }
    
    /**
     * Gets a quest-specific double value
     * @param questId The quest identifier
     * @param key The configuration key (without quest prefix)
     * @param defaultValue The default value if not found
     * @return The double value or default if not found
     */
    public double getQuestConfigDouble(String questId, String key, double defaultValue) {
        String fullPath = KEY_QUESTS_PREFIX + questId + "." + key;
        return getConfigDouble(fullPath, defaultValue);
    }
    
}
