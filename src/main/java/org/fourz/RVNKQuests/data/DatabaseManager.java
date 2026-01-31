package org.fourz.RVNKQuests.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Database connection manager with HikariCP connection pooling.
 *
 * <p>Supports both MySQL and SQLite backends with automatic schema initialization.
 * Uses HikariCP for efficient connection pooling.</p>
 */
public class DatabaseManager {

    /**
     * Supported database types.
     */
    public enum DatabaseType {
        MYSQL,
        SQLITE,
        YAML  // Fallback mode
    }

    private final RVNKQuests plugin;
    private final LogManager logger;
    private final DatabaseType type;
    private final FallbackTracker fallbackTracker;

    private HikariDataSource dataSource;
    private ExecutorService executor;
    private boolean initialized = false;

    /**
     * Creates a new DatabaseManager.
     *
     * @param plugin The plugin instance
     * @param fallbackTracker The fallback tracker instance
     */
    public DatabaseManager(RVNKQuests plugin, FallbackTracker fallbackTracker) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "DatabaseManager");
        this.fallbackTracker = fallbackTracker;

        // Determine database type from config
        String configType = plugin.getConfigManager().getConfig().getString("database.type", "sqlite");
        this.type = switch (configType.toLowerCase()) {
            case "mysql" -> DatabaseType.MYSQL;
            case "yaml" -> DatabaseType.YAML;
            default -> DatabaseType.SQLITE;
        };

        logger.info("Database type configured: " + type);
    }

    /**
     * Initialize the database connection pool and schema.
     *
     * @return true if initialization succeeded
     */
    public boolean initialize() {
        if (initialized) {
            logger.warning("DatabaseManager already initialized");
            return true;
        }

        if (type == DatabaseType.YAML) {
            logger.info("Using YAML storage mode (no database)");
            initialized = true;
            return true;
        }

        try {
            // Create executor for async operations
            int threads = (type == DatabaseType.SQLITE) ? 1 : 4;
            executor = Executors.newFixedThreadPool(threads);

            // Configure and create datasource
            HikariConfig config = createHikariConfig();
            dataSource = new HikariDataSource(config);

            // Test connection
            try (Connection conn = dataSource.getConnection()) {
                logger.info("Database connection established");
            }

            // Initialize schema
            initializeSchema();

            initialized = true;
            logger.info("DatabaseManager initialized successfully");
            return true;

        } catch (Exception e) {
            logger.error("Failed to initialize database", e);
            fallbackTracker.recordFailure();
            return false;
        }
    }

    /**
     * Creates HikariCP configuration based on database type.
     */
    private HikariConfig createHikariConfig() {
        HikariConfig config = new HikariConfig();

        if (type == DatabaseType.MYSQL) {
            ConfigurationSection mysql = plugin.getConfigManager().getConfig()
                .getConfigurationSection("database.mysql");

            String host = mysql.getString("host", "localhost");
            int port = mysql.getInt("port", 3306);
            String database = mysql.getString("database", "minecraft");
            String username = mysql.getString("username", "root");
            String password = mysql.getString("password", "");
            boolean useSsl = mysql.getBoolean("useSSL", false);

            config.setJdbcUrl(String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=%s&serverTimezone=UTC&allowPublicKeyRetrieval=true",
                host, port, database, useSsl
            ));
            config.setUsername(username);
            config.setPassword(password);

            // MySQL pool settings
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);

            // Performance tuning
            config.setLeakDetectionThreshold(60000);
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");

        } else { // SQLite
            String dbFile = plugin.getConfigManager().getConfig()
                .getString("database.sqlite.file", "data/quests.db");
            File file = new File(plugin.getDataFolder(), dbFile);

            // Ensure parent directory exists
            file.getParentFile().mkdirs();

            config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());

            // SQLite-specific settings
            config.setMaximumPoolSize(1);  // SQLite limitation: single writer
            config.setConnectionTimeout(30000);

            // SQLite performance tuning
            config.addDataSourceProperty("journal_mode", "WAL");
            config.addDataSourceProperty("synchronous", "NORMAL");
            config.addDataSourceProperty("temp_store", "MEMORY");
            config.addDataSourceProperty("cache_size", "-20000");  // 20MB
        }

        config.setPoolName("RVNKQuests-Pool");
        return config;
    }

    /**
     * Initialize database schema from SQL files.
     */
    private void initializeSchema() throws SQLException, IOException {
        String schemaFile = (type == DatabaseType.MYSQL) ? "schema/mysql.sql" : "schema/sqlite.sql";

        try (InputStream is = plugin.getResource(schemaFile)) {
            if (is == null) {
                throw new IOException("Schema file not found: " + schemaFile);
            }

            String schema = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            try (Connection conn = dataSource.getConnection()) {
                // Disable auto-commit for atomic schema creation
                boolean originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                try (Statement stmt = conn.createStatement()) {
                    // Remove SQL comments and normalize whitespace
                    StringBuilder cleanedSchema = new StringBuilder();
                    for (String line : schema.split("\n")) {
                        String trimmedLine = line.trim();
                        // Skip comment lines
                        if (!trimmedLine.startsWith("--") && !trimmedLine.isEmpty()) {
                            cleanedSchema.append(trimmedLine).append(" ");
                        }
                    }

                    // Split by semicolon and execute each statement
                    String[] statements = cleanedSchema.toString().split(";");
                    for (String sql : statements) {
                        sql = sql.trim();
                        if (!sql.isEmpty()) {
                            logger.debug("Executing SQL: " + sql.substring(0, Math.min(sql.length(), 50)) + "...");
                            stmt.execute(sql);
                        }
                    }

                    conn.commit();
                    logger.info("Database schema initialized successfully (" + statements.length + " statements)");

                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(originalAutoCommit);
                }
            }
        }
    }

    /**
     * Get a database connection from the pool.
     *
     * @return A database connection
     * @throws SQLException if connection cannot be obtained
     */
    public Connection getConnection() throws SQLException {
        if (type == DatabaseType.YAML || !initialized) {
            throw new SQLException("Database not available - using YAML fallback");
        }
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("DataSource is not available");
        }
        return dataSource.getConnection();
    }

    /**
     * Get the executor service for async operations.
     *
     * @return The executor service
     */
    public ExecutorService getExecutor() {
        return executor;
    }

    /**
     * Get the database type.
     *
     * @return The configured database type
     */
    public DatabaseType getType() {
        return type;
    }

    /**
     * Check if the database is initialized and available.
     *
     * @return true if database is ready for operations
     */
    public boolean isAvailable() {
        if (type == DatabaseType.YAML) {
            return false;  // YAML mode means no database
        }
        return initialized && dataSource != null && !dataSource.isClosed();
    }

    /**
     * Get the fallback tracker.
     *
     * @return The fallback tracker
     */
    public FallbackTracker getFallbackTracker() {
        return fallbackTracker;
    }

    /**
     * Shutdown the database manager and release resources.
     */
    public void shutdown() {
        logger.info("Shutting down DatabaseManager");

        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed");
        }

        initialized = false;
    }

    /**
     * Check if currently in fallback mode.
     *
     * @return true if in fallback mode
     */
    public boolean isInFallbackMode() {
        return type == DatabaseType.YAML || fallbackTracker.isInFallbackMode();
    }
}
