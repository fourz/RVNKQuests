package org.fourz.RVNKQuests.lore;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.util.Debug;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
    
/**
 * Manages lore and discovered locations for the server
 */
public class LoreDatabase {
    private final RVNKQuests plugin;
    private final Debug debug;
    private Connection connection;
    private String databaseType;
    private boolean initialized = false;
    
    // SQL statements for discoveries
    private static final String CREATE_DISCOVERIES_TABLE = 
        "CREATE TABLE IF NOT EXISTS discoveries (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "discovery_type TEXT NOT NULL, " +
        "world TEXT NOT NULL, " +
        "x INTEGER NOT NULL, " +
        "y INTEGER NOT NULL, " +
        "z INTEGER NOT NULL, " +
        "description TEXT, " +
        "discovery_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
    
    private static final String INSERT_DISCOVERY = 
        "INSERT INTO discoveries (discovery_type, world, x, y, z, description) " +
        "VALUES (?, ?, ?, ?, ?, ?)";
    
    // YAML file for flat-file storage
    private File loreFile;
    private FileConfiguration loreConfig;
    
    public LoreDatabase(RVNKQuests plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "LoreDatabase", Level.FINE);
    }
    
    /**
     * Initialize the lore database
     */
    public void initialize() {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        databaseType = config.getString("lore_database.type", "yaml");
        
        try {
            if ("sqlite".equalsIgnoreCase(databaseType)) {
                initializeSQLite();
            } else {
                // Default to YAML
                initializeYaml();
            }
            initialized = true;
            debug.info("Lore database initialized using " + databaseType);
        } catch (Exception e) {
            debug.error("Failed to initialize lore database", e);
            throw new RuntimeException("Failed to initialize lore database", e);
        }
    }
    
    private void initializeSQLite() throws SQLException {
        File dataFolder = plugin.getDataFolder();
        File dbFile = new File(dataFolder, "lore.db");
        
        // Ensure the data folder exists
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        // Connect to the database
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        debug.debug("Connecting to SQLite database at: " + url);
        
        connection = DriverManager.getConnection(url);
        
        // Create the discoveries table if it doesn't exist
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(CREATE_DISCOVERIES_TABLE);
        }
    }
    
    private void initializeYaml() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        loreFile = new File(dataFolder, "lore.yml");
        
        // Create the file if it doesn't exist
        if (!loreFile.exists()) {
            try {
                loreFile.createNewFile();
            } catch (IOException e) {
                debug.error("Could not create lore.yml", e);
                throw new RuntimeException("Could not create lore.yml", e);
            }
        }
        
        loreConfig = YamlConfiguration.loadConfiguration(loreFile);
        
        // Ensure the discoveries section exists
        if (!loreConfig.contains("discoveries")) {
            loreConfig.createSection("discoveries");
            saveYamlConfig();
        }
    }
    
    /**
     * Shut down the lore database
     */
    public void shutdown() {
        debug.debug("Shutting down lore database");
        
        if ("sqlite".equalsIgnoreCase(databaseType) && connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                debug.error("Error closing database connection", e);
            }
        } else if (loreConfig != null) {
            saveYamlConfig();
        }
        
        initialized = false;
    }
    
    private void saveYamlConfig() {
        try {
            loreConfig.save(loreFile);
        } catch (IOException e) {
            debug.error("Could not save lore.yml", e);
        }
    }
    
    /**
     * Record a discovery in the lore database
     * 
     * @param discoveryType The type of discovery (e.g., "ancient_ruin", "nether_portal")
     * @param world The world where the discovery was made
     * @param x The x coordinate
     * @param y The y coordinate
     * @param z The z coordinate
     * @param description A description of the discovery
     * @return true if the discovery was recorded successfully
     */
    public boolean recordDiscovery(String discoveryType, String world, int x, int y, int z, String description) {
        if (!initialized) {
            debug.warning("Attempted to record discovery when lore database not initialized");
            return false;
        }
        
        debug.debug(String.format("Recording discovery: %s at %s (%d, %d, %d): %s", 
            discoveryType, world, x, y, z, description));
        
        try {
            if ("sqlite".equalsIgnoreCase(databaseType)) {
                return recordDiscoverySQLite(discoveryType, world, x, y, z, description);
            } else {
                return recordDiscoveryYaml(discoveryType, world, x, y, z, description);
            }
        } catch (Exception e) {
            debug.error("Error recording discovery", e);
            return false;
        }
    }
    
    private boolean recordDiscoverySQLite(String discoveryType, String world, int x, int y, int z, String description) 
            throws SQLException {
        try (PreparedStatement pstmt = connection.prepareStatement(INSERT_DISCOVERY)) {
            pstmt.setString(1, discoveryType);
            pstmt.setString(2, world);
            pstmt.setInt(3, x);
            pstmt.setInt(4, y);
            pstmt.setInt(5, z);
            pstmt.setString(6, description);
            
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        }
    }
    
    private boolean recordDiscoveryYaml(String discoveryType, String world, int x, int y, int z, String description) {
        // Get the discoveries section
        int nextId = loreConfig.getInt("next_discovery_id", 1);
        String discoveryId = "discovery_" + nextId;
        
        // Create a new discovery entry
        loreConfig.set("discoveries." + discoveryId + ".type", discoveryType);
        loreConfig.set("discoveries." + discoveryId + ".world", world);
        loreConfig.set("discoveries." + discoveryId + ".x", x);
        loreConfig.set("discoveries." + discoveryId + ".y", y);
        loreConfig.set("discoveries." + discoveryId + ".z", z);
        loreConfig.set("discoveries." + discoveryId + ".description", description);
        loreConfig.set("discoveries." + discoveryId + ".time", System.currentTimeMillis());
        
        // Increment the next ID
        loreConfig.set("next_discovery_id", nextId + 1);
        
        // Save the config
        saveYamlConfig();
        
        return true;
    }
    
    /**
     * Get all discoveries of a specific type
     * 
     * @param discoveryType The type of discovery to retrieve
     * @return A list of LoreDiscovery objects
     */
    public List<LoreDiscovery> getDiscoveriesByType(String discoveryType) {
        List<LoreDiscovery> discoveries = new ArrayList<>();
        
        if (!initialized) {
            debug.warning("Attempted to get discoveries when lore database not initialized");
            return discoveries;
        }
        
        try {
            if ("sqlite".equalsIgnoreCase(databaseType)) {
                return getDiscoveriesByTypeSQLite(discoveryType);
            } else {
                return getDiscoveriesByTypeYaml(discoveryType);
            }
        } catch (Exception e) {
            debug.error("Error getting discoveries", e);
            return discoveries;
        }
    }
    
    private List<LoreDiscovery> getDiscoveriesByTypeSQLite(String discoveryType) throws SQLException {
        List<LoreDiscovery> discoveries = new ArrayList<>();
        
        String query = "SELECT * FROM discoveries WHERE discovery_type = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, discoveryType);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    LoreDiscovery discovery = new LoreDiscovery(
                        rs.getInt("id"),
                        rs.getString("discovery_type"),
                        rs.getString("world"),
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z"),
                        rs.getString("description"),
                        rs.getLong("discovery_time")
                    );
                    discoveries.add(discovery);
                }
            }
        }
        
        return discoveries;
    }
    
    private List<LoreDiscovery> getDiscoveriesByTypeYaml(String discoveryType) {
        List<LoreDiscovery> discoveries = new ArrayList<>();
        
        if (loreConfig.contains("discoveries")) {
            for (String key : loreConfig.getConfigurationSection("discoveries").getKeys(false)) {
                String type = loreConfig.getString("discoveries." + key + ".type");
                
                if (type != null && type.equals(discoveryType)) {
                    LoreDiscovery discovery = new LoreDiscovery(
                        Integer.parseInt(key.replace("discovery_", "")),
                        type,
                        loreConfig.getString("discoveries." + key + ".world"),
                        loreConfig.getInt("discoveries." + key + ".x"),
                        loreConfig.getInt("discoveries." + key + ".y"),
                        loreConfig.getInt("discoveries." + key + ".z"),
                        loreConfig.getString("discoveries." + key + ".description"),
                        loreConfig.getLong("discoveries." + key + ".time")
                    );
                    discoveries.add(discovery);
                }
            }
        }
        
        return discoveries;
    }
    
    /**
     * Updates the debug level for the database
     * @param level New log level
     */
    public void updateDebugLevel(Level level) {
        if (debug != null) {
            debug.setLogLevel(level);
            debug.debug("LoreDatabase log level updated to: " + level.getName());
        }
    }

    /**
     * Check if the database is initialized
     * @return true if initialized, false otherwise
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                debug.error("Error closing database connection", e);
            }
        }
        if (loreConfig != null) {
            saveYamlConfig();
        }
    }    
}
