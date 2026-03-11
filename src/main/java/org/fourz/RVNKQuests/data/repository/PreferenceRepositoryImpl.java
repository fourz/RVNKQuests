package org.fourz.RVNKQuests.data.repository;

import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.DatabaseManager;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of IPreferenceRepository using JDBC and HikariCP.
 *
 * <p>Stores player preferences in the quest_player_preferences table with
 * support for both MySQL and SQLite backends.</p>
 *
 * <p>Table schema:</p>
 * <pre>
 * CREATE TABLE quest_player_preferences (
 *     player_id VARCHAR(36) NOT NULL,
 *     pref_key VARCHAR(64) NOT NULL,
 *     pref_value TEXT NOT NULL,
 *     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 *     PRIMARY KEY (player_id, pref_key)
 * );
 * </pre>
 *
 * @since 1.0-SNAPSHOT
 */
public class PreferenceRepositoryImpl implements IPreferenceRepository {

    private final RVNKQuests plugin;
    private final DatabaseManager databaseManager;
    private final LogManager logger;

    public PreferenceRepositoryImpl(RVNKQuests plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.logger = LogManager.getInstance(plugin, "PreferenceRepository");
        initializeTable();
    }

    /**
     * Initialize the preferences table if it doesn't exist.
     * Called on repository creation to ensure schema is ready.
     */
    private void initializeTable() {
        try {
            String tableName = databaseManager.table("quest_player_preferences");
            String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "player_id VARCHAR(36) NOT NULL, " +
                    "pref_key VARCHAR(100) NOT NULL, " +
                    "pref_value VARCHAR(255), " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "UNIQUE (player_id, pref_key)" +
                    ")";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.executeUpdate();
                logger.debug("Preferences table initialized successfully");
            }
        } catch (SQLException e) {
            logger.error("Failed to initialize preferences table", e);
        }
    }

    @Override
    public CompletableFuture<Void> savePreference(UUID playerUuid, String prefKey, String prefValue) {
        return CompletableFuture.runAsync(() -> {
            String tableName = databaseManager.table("quest_player_preferences");
            String playerId = playerUuid.toString();

            // REPLACE INTO works for both MySQL and SQLite
            String sql = "REPLACE INTO " + tableName +
                    " (player_id, pref_key, pref_value, updated_at) VALUES (?, ?, ?, ?)";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerId);
                stmt.setString(2, prefKey);
                stmt.setString(3, prefValue);
                stmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));

                stmt.executeUpdate();
                logger.debug("Saved preference for player " + playerId + ": " + prefKey + " = " + prefValue);

            } catch (SQLException e) {
                logger.error("Failed to save preference for player " + playerId + " key " + prefKey, e);
                throw new RuntimeException("Preference save failed", e);
            }
        });
    }

    @Override
    public CompletableFuture<String> getPreference(UUID playerUuid, String prefKey) {
        return CompletableFuture.supplyAsync(() -> {
            String tableName = databaseManager.table("quest_player_preferences");
            String playerId = playerUuid.toString();

            String sql = "SELECT pref_value FROM " + tableName +
                    " WHERE player_id = ? AND pref_key = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerId);
                stmt.setString(2, prefKey);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("pref_value");
                    }
                    return null;
                }

            } catch (SQLException e) {
                logger.error("Failed to get preference for player " + playerId + " key " + prefKey, e);
                throw new RuntimeException("Preference retrieval failed", e);
            }
        });
    }

    @Override
    public CompletableFuture<Map<String, String>> getAllPreferences(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String tableName = databaseManager.table("quest_player_preferences");
            String playerId = playerUuid.toString();

            String sql = "SELECT pref_key, pref_value FROM " + tableName +
                    " WHERE player_id = ?";

            Map<String, String> preferences = new HashMap<>();

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerId);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        preferences.put(rs.getString("pref_key"), rs.getString("pref_value"));
                    }
                }

                logger.debug("Loaded " + preferences.size() + " preferences for player " + playerId);
                return preferences;

            } catch (SQLException e) {
                logger.error("Failed to get all preferences for player " + playerId, e);
                throw new RuntimeException("Preferences retrieval failed", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> deletePreference(UUID playerUuid, String prefKey) {
        return CompletableFuture.runAsync(() -> {
            String tableName = databaseManager.table("quest_player_preferences");
            String playerId = playerUuid.toString();

            String sql = "DELETE FROM " + tableName +
                    " WHERE player_id = ? AND pref_key = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerId);
                stmt.setString(2, prefKey);

                int rowsDeleted = stmt.executeUpdate();
                logger.debug("Deleted preference for player " + playerId + ": " + prefKey +
                        " (rows affected: " + rowsDeleted + ")");

            } catch (SQLException e) {
                logger.error("Failed to delete preference for player " + playerId + " key " + prefKey, e);
                throw new RuntimeException("Preference deletion failed", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteAllPreferences(UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            String tableName = databaseManager.table("quest_player_preferences");
            String playerId = playerUuid.toString();

            String sql = "DELETE FROM " + tableName +
                    " WHERE player_id = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerId);

                int rowsDeleted = stmt.executeUpdate();
                logger.debug("Deleted all preferences for player " + playerId +
                        " (rows affected: " + rowsDeleted + ")");

            } catch (SQLException e) {
                logger.error("Failed to delete all preferences for player " + playerId, e);
                throw new RuntimeException("Preferences deletion failed", e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> hasPreference(UUID playerUuid, String prefKey) {
        return CompletableFuture.supplyAsync(() -> {
            String tableName = databaseManager.table("quest_player_preferences");
            String playerId = playerUuid.toString();

            String sql = "SELECT COUNT(*) as count FROM " + tableName +
                    " WHERE player_id = ? AND pref_key = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerId);
                stmt.setString(2, prefKey);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("count") > 0;
                    }
                    return false;
                }

            } catch (SQLException e) {
                logger.error("Failed to check preference existence for player " + playerId + " key " + prefKey, e);
                throw new RuntimeException("Preference existence check failed", e);
            }
        });
    }
}
