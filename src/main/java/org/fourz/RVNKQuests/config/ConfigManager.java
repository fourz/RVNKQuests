package org.fourz.RVNKQuests.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.rvnkcore.config.dto.DatabaseSettingsDTO;
import org.fourz.rvnkcore.config.dto.MySQLSettingsDTO;
import org.fourz.rvnkcore.config.dto.SQLiteSettingsDTO;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages plugin configuration with support for quest-specific settings.
 * 
 * This class provides centralized access to all configuration data, including:
 * - Plugin-wide settings (logging, features)
 * - Quest-specific settings (enabled status, custom properties)
 * - Dynamic configuration reloading
 */
public class ConfigManager {
    private final RVNKQuests plugin;
    private final LogManager logger;
    private FileConfiguration config;
    private File configFile;
    private Map<String, Boolean> questEnableStatus = new HashMap<>();
    private DatabaseSettingsDTO databaseSettings;
    
    // Constants for configuration keys
    public static final String KEY_LORE_DATABASE_ENABLED = "lore_database.enabled";
    public static final String KEY_LORE_DATABASE_TYPE = "lore_database.type";
    public static final String KEY_QUESTS_PREFIX = "quests.";
    public static final String KEY_ENABLE_SUFFIX = ".enable";
    
    /**
     * Creates a new configuration manager and loads initial settings
     * @param plugin The plugin instance
     */
    public ConfigManager(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        setupConfig();
        loadQuestEnableStatus();
    }

    /**
     * Sets up the configuration file, creating it if it doesn't exist
     */
    private void setupConfig() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "config.yml");
        }

        if (!configFile.exists()) {
            plugin.getDataFolder().mkdirs();
            saveDefaultConfig();
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        logger.info("Configuration loaded from: " + configFile.getAbsolutePath());

        String dbType = config.getString("database.type", "sqlite");
        if (!"yaml".equalsIgnoreCase(dbType)) {
            try {
                this.databaseSettings = createDatabaseSettings();
            } catch (IllegalArgumentException e) {
                logger.error("Invalid database configuration: " + e.getMessage());
            }
        }
    }

    private DatabaseSettingsDTO createDatabaseSettings() {
        String storageType = config.getString("database.type", "sqlite");
        DatabaseSettingsDTO.DatabaseType type = "mysql".equalsIgnoreCase(storageType)
                ? DatabaseSettingsDTO.DatabaseType.MYSQL
                : DatabaseSettingsDTO.DatabaseType.SQLITE;

        MySQLSettingsDTO mysqlSettings = null;
        if (type == DatabaseSettingsDTO.DatabaseType.MYSQL) {
            mysqlSettings = new MySQLSettingsDTO(
                    config.getString("database.mysql.host", "localhost"),
                    config.getInt("database.mysql.port", 3306),
                    config.getString("database.mysql.database", "minecraft"),
                    config.getString("database.mysql.username", "root"),
                    config.getString("database.mysql.password", ""),
                    config.getBoolean("database.mysql.useSSL", false),
                    config.getString("database.mysql.tablePrefix", "")
            );
        }

        SQLiteSettingsDTO sqliteSettings = null;
        if (type == DatabaseSettingsDTO.DatabaseType.SQLITE) {
            String dbFile = config.getString("database.sqlite.file", "data/quests.db");
            String filePath = new File(plugin.getDataFolder(), dbFile).getAbsolutePath();
            sqliteSettings = new SQLiteSettingsDTO(
                    filePath,
                    config.getString("database.sqlite.tablePrefix", "")
            );
        }

        DatabaseSettingsDTO dto = new DatabaseSettingsDTO(type, mysqlSettings, sqliteSettings);
        dto.validate();
        return dto;
    }

    public DatabaseSettingsDTO getDatabaseSettings() {
        if (databaseSettings == null) {
            databaseSettings = createDatabaseSettings();
        }
        return databaseSettings;
    }

    /**
     * Saves the default configuration file from resources
     */
    private void saveDefaultConfig() {
        logger.info("Creating default configuration file");
        try {
            plugin.getDataFolder().mkdirs();
            try (InputStream in = plugin.getResource("config.yml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Default configuration file created");
                } else {
                    logger.warning("Default config.yml not found in resources");
                }
            }
        } catch (IOException e) {
            logger.error("Could not create default configuration file", e);
        }
    }

    /**
     * Reloads the configuration from disk
     */
    public void reloadConfig() {
        logger.debug("Reloading configuration");
        config = YamlConfiguration.loadConfiguration(configFile);
        loadQuestEnableStatus();
        String dbType = config.getString("database.type", "sqlite");
        if (!"yaml".equalsIgnoreCase(dbType)) {
            try {
                this.databaseSettings = createDatabaseSettings();
            } catch (IllegalArgumentException e) {
                logger.error("Invalid database configuration after reload: " + e.getMessage());
            }
        } else {
            this.databaseSettings = null;
        }
        logger.debug("Configuration reloaded");
    }

    /**
     * Saves the current configuration to disk
     * @return true if the configuration was saved successfully
     */
    public boolean saveConfig() {
        logger.debug("Saving configuration to disk");
        try {
            config.save(configFile);
            logger.debug("Configuration saved to: " + configFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            logger.error("Failed to save configuration", e);
            return false;
        }
    }
    
    /**
     * Sets a value in the configuration
     * @param path The path to set
     * @param value The value to set
     */
    public void setConfigValue(String path, Object value) {
        logger.debug("Setting config value: " + path + "");
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
        logger.debug("Quest " + questId + " set to " + (enabled ? "enabled" : "disabled"));
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
     * Checks if the lore database is enabled in configuration
     * @return true if the lore database should be enabled
     */
    public boolean isLoreDatabaseEnabled() {
        return config.getBoolean(KEY_LORE_DATABASE_ENABLED, true);
    }
    
    /**
     * Gets the type of lore database to use
     * @return The lore database type (e.g., "yaml", "sqlite")
     */
    public String getLoreDatabaseType() {
        return config.getString(KEY_LORE_DATABASE_TYPE, "yaml");
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
            case "WARN":
            case "WARNING":
                return Level.WARNING;
            case "ERROR":
            case "SEVERE":
                return Level.SEVERE;
            case "OFF":
                return Level.OFF;
            case "ALL":
                return Level.ALL;
            default:
                logger.warning("Unknown log level in config: " + logLevelStr + ", defaulting to INFO");
                return Level.INFO;
        }
    }
    
    /**
     * Updates the debug level for this manager
     * @param level New log level
     */
    public void updateDebugLevel(Level level) {
        logger.setLogLevel(level);
        logger.debug("ConfigManager log level updated to: " + level.getName());
    }
    
    /**
     * Loads the enable/disable status for all quests from the configuration.
     * The status is cached for performance when checking quest status frequently.
     */
    private void loadQuestEnableStatus() {
        questEnableStatus.clear();
        
        if (config.isConfigurationSection("quests")) {
            for (String questId : config.getConfigurationSection("quests").getKeys(false)) {
                boolean enabled = config.getBoolean(KEY_QUESTS_PREFIX + questId + KEY_ENABLE_SUFFIX, true);
                questEnableStatus.put(questId, enabled);
                logger.debug("Quest " + questId + " is " + (enabled ? "enabled" : "disabled"));
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
        return config.get(path, defaultValue);
    }
    
    /**
     * Gets a string from any loaded configuration file
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The string value or default if not found
     */
    public String getConfigString(String path, String defaultValue) {
        return config.getString(path, defaultValue);
    }
    
    /**
     * Gets a boolean from any loaded configuration file
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The boolean value or default if not found
     */
    public boolean getConfigBoolean(String path, boolean defaultValue) {
        return config.getBoolean(path, defaultValue);
    }
    
    /**
     * Gets an integer from any loaded configuration file
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The integer value or default if not found
     */
    public int getConfigInt(String path, int defaultValue) {
        return config.getInt(path, defaultValue);
    }
    
    /**
     * Gets a double from any loaded configuration file
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The double value or default if not found
     */
    public double getConfigDouble(String path, double defaultValue) {
        return config.getDouble(path, defaultValue);
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
