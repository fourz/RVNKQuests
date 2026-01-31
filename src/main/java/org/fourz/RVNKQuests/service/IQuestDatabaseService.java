package org.fourz.RVNKQuests.service;

import org.fourz.RVNKQuests.data.DatabaseManager;

import java.sql.Connection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Service interface for database operations and schema management.
 * Provides cross-plugin access to RVNKQuests database functionality.
 *
 * <p>Register with RVNKCore ServiceRegistry for use by other plugins:</p>
 * <pre>{@code
 * registry.registerService(IQuestDatabaseService.class, databaseManager);
 * }</pre>
 *
 * <p>This interface exposes database functionality for cross-plugin
 * integration while maintaining proper connection management.</p>
 *
 * <p>Pattern compliance:</p>
 * <ul>
 *   <li>Interface uses "I" prefix per RVNK coding standards</li>
 *   <li>Async executor provided for non-blocking operations</li>
 *   <li>Thread-safe connection pool access</li>
 * </ul>
 */
public interface IQuestDatabaseService {

    // ==================== Connection Management ====================

    /**
     * Gets a database connection from the pool.
     * Caller is responsible for closing the connection.
     *
     * @return A database connection
     * @throws java.sql.SQLException if connection cannot be obtained
     */
    Connection getConnection() throws java.sql.SQLException;

    /**
     * Gets the executor service for async database operations.
     * Use this executor to run database queries asynchronously.
     *
     * @return The async executor
     */
    ExecutorService getExecutor();

    // ==================== Database Information ====================

    /**
     * Gets the database type (MYSQL, SQLITE, or YAML fallback).
     * @return The database type
     */
    DatabaseManager.DatabaseType getType();

    /**
     * Checks if the database is using MySQL.
     * @return true if using MySQL
     */
    boolean isMySQL();

    /**
     * Checks if the database is using SQLite.
     * @return true if using SQLite
     */
    boolean isSQLite();

    /**
     * Checks if the database is in YAML fallback mode.
     * @return true if using YAML fallback (no database)
     */
    boolean isYAMLFallback();

    // ==================== Schema Management ====================

    /**
     * Checks if the database schema is initialized.
     * @return true if schema is ready
     */
    boolean isSchemaInitialized();

    /**
     * Re-initializes the database schema.
     * Warning: This may delete existing data depending on implementation.
     *
     * @return CompletableFuture that completes when schema is ready
     */
    CompletableFuture<Boolean> reinitializeSchema();

    /**
     * Validates the current schema against expected structure.
     * @return CompletableFuture containing validation result
     */
    CompletableFuture<SchemaValidationResult> validateSchema();

    // ==================== Health Monitoring ====================

    /**
     * Tests database connectivity.
     * @return true if database is reachable
     */
    boolean testConnection();

    /**
     * Gets database health metrics.
     * @return Health metrics including pool status and query latency
     */
    DatabaseHealthMetrics getHealthMetrics();

    /**
     * Gets the number of active connections in the pool.
     * @return Active connection count
     */
    int getActiveConnectionCount();

    /**
     * Gets the number of idle connections in the pool.
     * @return Idle connection count
     */
    int getIdleConnectionCount();

    // ==================== Service Lifecycle ====================

    /**
     * Checks if the database service is initialized and ready.
     * @return true if database is operational
     */
    boolean isInitialized();

    /**
     * Reloads database configuration.
     * This may reconnect to the database with new settings.
     */
    void reload();

    /**
     * Shuts down the database service.
     * Closes all connections and releases resources.
     */
    void shutdown();

    // ==================== Inner Types ====================

    /**
     * Schema validation result containing status and any errors found.
     */
    record SchemaValidationResult(
        boolean valid,
        String message,
        java.util.List<String> errors
    ) {
        public SchemaValidationResult {
            if (errors == null) {
                errors = java.util.List.of();
            }
        }

        public static SchemaValidationResult success() {
            return new SchemaValidationResult(true, "Schema validation passed", java.util.List.of());
        }

        public static SchemaValidationResult failure(String message, java.util.List<String> errors) {
            return new SchemaValidationResult(false, message, errors);
        }
    }

    /**
     * Database health metrics for monitoring and diagnostics.
     */
    record DatabaseHealthMetrics(
        int activeConnections,
        int idleConnections,
        int maxConnections,
        long averageQueryTimeMs,
        boolean healthy,
        String statusMessage
    ) {}
}
