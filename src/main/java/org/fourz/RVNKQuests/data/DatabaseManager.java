package org.fourz.RVNKQuests.data;

import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.rvnkcore.config.dto.DatabaseSettingsDTO;
import org.fourz.rvnkcore.config.dto.MySQLSettingsDTO;
import org.fourz.rvnkcore.config.dto.SQLiteSettingsDTO;
import org.fourz.rvnkcore.data.FallbackTracker;
import org.fourz.rvnkcore.database.config.DatabaseConfig;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.rvnkcore.database.connection.ConnectionProviderFactory;
import org.fourz.RVNKQuests.service.IQuestDatabaseService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Database connection manager with HikariCP connection pooling.
 *
 * <p>Supports both MySQL and SQLite backends with automatic schema initialization.
 * Uses HikariCP for efficient connection pooling.</p>
 */
public class DatabaseManager implements IQuestDatabaseService {

    /**
     * Supported database types.
     */
    public enum DatabaseType {
        MYSQL,
        SQLITE,
        YAML  // Fallback mode
    }

    // Base table names (used for prefix replacement in schema files)
    private static final String[] BASE_TABLE_NAMES = {
        "quest_definitions",
        "quest_definition_objectives",
        "quest_definition_rewards",
        "quest_progress",
        "quest_objective_progress",
        "quest_rewards_claimed",
        "quest_journal_entries",
        "quest_categories",
        "quest_tags",
        "quest_tag_assignments",
        "quest_leaderboard_entries",
        "quest_leaderboard_cache",
        "quest_repeat_config"
    };

    private final RVNKQuests plugin;
    private final LogManager logger;
    private final DatabaseType type;
    private final FallbackTracker fallbackTracker;
    private final String tablePrefix;

    private ConnectionProvider connectionProvider;
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

        // Load table prefix from DTO (YAML mode has no DTO)
        if (this.type == DatabaseType.YAML) {
            this.tablePrefix = "";
        } else {
            DatabaseSettingsDTO settings = plugin.getConfigManager().getDatabaseSettings();
            this.tablePrefix = settings.getTablePrefix();
        }
        if (tablePrefix != null && !tablePrefix.isEmpty()) {
            logger.debug("Using table prefix: " + tablePrefix);
        }

        logger.debug("Database type configured: " + type);
    }

    /**
     * Get the table name with prefix applied.
     * @param baseName The base table name (e.g., "quest_progress")
     * @return The prefixed table name (e.g., "quests_quest_progress")
     */
    public String table(String baseName) {
        if (tablePrefix == null || tablePrefix.isEmpty()) {
            return baseName;
        }
        return tablePrefix + baseName;
    }

    /**
     * Get the configured table prefix.
     * @return The table prefix, or empty string if none
     */
    public String getTablePrefix() {
        return tablePrefix != null ? tablePrefix : "";
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

            // Build DatabaseConfig and create ConnectionProvider via RVNKCore
            DatabaseConfig dbConfig;
            if (type == DatabaseType.MYSQL) {
                MySQLSettingsDTO mysql = plugin.getConfigManager().getDatabaseSettings().getMysqlSettings();
                dbConfig = DatabaseConfig.builder()
                        .type("mysql")
                        .host(mysql.getHost())
                        .port(mysql.getPort())
                        .database(mysql.getDatabase())
                        .username(mysql.getUsername())
                        .password(mysql.getPassword())
                        .useSSL(mysql.isUseSSL())
                        .maxConnections(10)
                        .minIdleConnections(2)
                        .connectionTimeoutMs(30000)
                        .idleTimeoutMs(600000L)
                        .maxLifetimeMs(1800000L)
                        .build();
            } else { // SQLite
                SQLiteSettingsDTO sqlite = plugin.getConfigManager().getDatabaseSettings().getSqliteSettings();
                File file = new File(sqlite.getFilePath());
                file.getParentFile().mkdirs();
                dbConfig = DatabaseConfig.sqlite(file.getName());
            }

            connectionProvider = new ConnectionProviderFactory(plugin).createConnectionProvider(dbConfig);

            // Test connection
            try (Connection conn = connectionProvider.getConnection()) {
                logger.info("Database connection established");
            }

            // Initialize schema
            initializeSchema();

            initialized = true;
            return true;

        } catch (Exception e) {
            logger.error("Failed to initialize database", e);
            fallbackTracker.recordFailure();
            return false;
        }
    }

    /**
     * Initialize database schema from SQL files.
     * Applies table prefix substitution if configured.
     */
    private void initializeSchema() throws SQLException, IOException {
        String schemaFile = (type == DatabaseType.MYSQL) ? "schema/mysql.sql" : "schema/sqlite.sql";

        try (InputStream is = plugin.getResource(schemaFile)) {
            if (is == null) {
                throw new IOException("Schema file not found: " + schemaFile);
            }

            String schema = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            // Apply table prefix substitution if configured
            if (tablePrefix != null && !tablePrefix.isEmpty()) {
                for (String baseTable : BASE_TABLE_NAMES) {
                    schema = schema.replace(baseTable, table(baseTable));
                }
                logger.debug("Applied table prefix '" + tablePrefix + "' to schema");
            }

            try (Connection conn = connectionProvider.getConnection()) {
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
        if (connectionProvider == null || !connectionProvider.isValid()) {
            throw new SQLException("DataSource is not available");
        }
        return connectionProvider.getConnection();
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
        return initialized && connectionProvider != null && connectionProvider.isValid();
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

        if (connectionProvider != null && connectionProvider.isValid()) {
            connectionProvider.close();
            connectionProvider = null;
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

    // ==================== IQuestDatabaseService Implementation ====================

    @Override
    public boolean isMySQL() {
        return type == DatabaseType.MYSQL;
    }

    @Override
    public boolean isSQLite() {
        return type == DatabaseType.SQLITE;
    }

    @Override
    public boolean isYAMLFallback() {
        return type == DatabaseType.YAML;
    }

    @Override
    public boolean isSchemaInitialized() {
        return initialized;
    }

    @Override
    public CompletableFuture<Boolean> reinitializeSchema() {
        if (executor == null) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                initializeSchema();
                return true;
            } catch (Exception e) {
                logger.error("Failed to reinitialize schema", e);
                return false;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<SchemaValidationResult> validateSchema() {
        if (!initialized || type == DatabaseType.YAML) {
            return CompletableFuture.completedFuture(
                SchemaValidationResult.failure("Database not initialized", List.of("Database is in YAML mode or not initialized"))
            );
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection()) {
                // Basic schema validation - check if main tables exist (with prefix)
                java.util.List<String> errors = new java.util.ArrayList<>();

                for (String baseTable : BASE_TABLE_NAMES) {
                    String prefixedTable = table(baseTable);
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("SELECT 1 FROM " + prefixedTable + " LIMIT 1");
                    } catch (SQLException e) {
                        errors.add("Table missing or invalid: " + prefixedTable);
                    }
                }

                if (errors.isEmpty()) {
                    return SchemaValidationResult.success();
                } else {
                    return SchemaValidationResult.failure("Schema validation failed", errors);
                }
            } catch (SQLException e) {
                return SchemaValidationResult.failure("Database connection failed", List.of(e.getMessage()));
            }
        }, executor);
    }

    @Override
    public boolean testConnection() {
        if (!initialized || connectionProvider == null || !connectionProvider.isValid()) {
            return false;
        }

        try (Connection conn = connectionProvider.getConnection()) {
            return conn.isValid(5);
        } catch (SQLException e) {
            logger.warning("Connection test failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public DatabaseHealthMetrics getHealthMetrics() {
        if (connectionProvider == null || !connectionProvider.isValid()) {
            return new DatabaseHealthMetrics(0, 0, 0, 0, false, "DataSource not available");
        }
        boolean healthy = testConnection();
        String status = healthy ? "Healthy" : "Connection issues detected";
        // Pool introspection not available via ConnectionProvider; RVNKCore owns pool metrics
        return new DatabaseHealthMetrics(0, 0, 0, 0, healthy, status);
    }

    @Override
    public int getActiveConnectionCount() {
        // Pool introspection not available via ConnectionProvider; RVNKCore owns pool lifecycle
        return 0;
    }

    @Override
    public int getIdleConnectionCount() {
        // Pool introspection not available via ConnectionProvider; RVNKCore owns pool lifecycle
        return 0;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void reload() {
        logger.info("Reloading database manager");
        shutdown();
        initialize();
    }
}
