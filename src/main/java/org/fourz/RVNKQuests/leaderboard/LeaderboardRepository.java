package org.fourz.RVNKQuests.leaderboard;

import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.DatabaseManager;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository for leaderboard data access with async operations.
 * Handles database queries for leaderboard entries, rankings, and caching.
 */
public class LeaderboardRepository {

    private final RVNKQuests plugin;
    private final DatabaseManager dbManager;
    private final LogManager logger;

    public LeaderboardRepository(RVNKQuests plugin, DatabaseManager dbManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;
        this.logger = LogManager.getInstance(plugin, "LeaderboardRepository");
    }

    /**
     * Get top N entries for a specific leaderboard type.
     * Returns entries ordered by rank (1 = first place).
     *
     * @param type Leaderboard type
     * @param limit Maximum number of entries to return
     * @return Future completing with list of leaderboard entries
     */
    public CompletableFuture<List<LeaderboardEntry>> getTopEntries(LeaderboardType type, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT player_uuid, player_name, leaderboard_type, value, rank, updated_at " +
                        "FROM quest_leaderboard_entries " +
                        "WHERE leaderboard_type = ? " +
                        "ORDER BY rank ASC " +
                        "LIMIT ?";

            List<LeaderboardEntry> entries = new ArrayList<>();

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, type.name());
                stmt.setInt(2, limit);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        entries.add(createEntryFromResultSet(rs, type));
                    }
                }

                logger.debug("Retrieved " + entries.size() + " entries for " + type.name());
                return entries;

            } catch (SQLException e) {
                logger.error("Failed to fetch leaderboard entries for " + type.name(), e);
                throw new RuntimeException("Database error retrieving leaderboard", e);
            }
        }, dbManager.getExecutor());
    }

    /**
     * Get a player's rank for a specific leaderboard type.
     *
     * @param playerUuid Player's UUID
     * @param type Leaderboard type
     * @return Future completing with optional leaderboard entry
     */
    public CompletableFuture<Optional<LeaderboardEntry>> getPlayerRank(UUID playerUuid, LeaderboardType type) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT player_uuid, player_name, leaderboard_type, value, rank, updated_at " +
                        "FROM quest_leaderboard_entries " +
                        "WHERE player_uuid = ? AND leaderboard_type = ?";

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, type.name());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        LeaderboardEntry entry = createEntryFromResultSet(rs, type);
                        logger.debug("Found rank for player " + playerUuid + ": " + entry.rank());
                        return Optional.of(entry);
                    } else {
                        logger.debug("No rank found for player " + playerUuid + " in " + type.name());
                        return Optional.empty();
                    }
                }

            } catch (SQLException e) {
                logger.error("Failed to fetch player rank", e);
                throw new RuntimeException("Database error retrieving player rank", e);
            }
        }, dbManager.getExecutor());
    }

    /**
     * Update or insert a leaderboard entry for a player.
     *
     * @param entry Leaderboard entry to save
     * @return Future completing with true if successful
     */
    public CompletableFuture<Boolean> updateEntry(LeaderboardEntry entry) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = dbManager.isMySQL()
                ? "INSERT INTO quest_leaderboard_entries (player_uuid, player_name, leaderboard_type, value, rank, updated_at) " +
                  "VALUES (?, ?, ?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), value = VALUES(value), rank = VALUES(rank), updated_at = VALUES(updated_at)"
                : "INSERT OR REPLACE INTO quest_leaderboard_entries (player_uuid, player_name, leaderboard_type, value, rank, updated_at) " +
                  "VALUES (?, ?, ?, ?, ?, ?)";

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, entry.playerUuid().toString());
                stmt.setString(2, entry.playerName());
                stmt.setString(3, entry.leaderboardType().name());
                stmt.setInt(4, entry.value());
                stmt.setInt(5, entry.rank());

                if (dbManager.isMySQL()) {
                    stmt.setTimestamp(6, java.sql.Timestamp.from(entry.updatedAt()));
                } else {
                    stmt.setString(6, entry.updatedAt().toString());
                }

                int rowsAffected = stmt.executeUpdate();
                logger.debug("Updated leaderboard entry for player " + entry.playerName() + ": " + rowsAffected + " rows");
                return rowsAffected > 0;

            } catch (SQLException e) {
                logger.error("Failed to update leaderboard entry", e);
                throw new RuntimeException("Database error updating leaderboard", e);
            }
        }, dbManager.getExecutor());
    }

    /**
     * Refresh cache for a specific leaderboard type.
     * Recalculates ranks and updates the cache table.
     *
     * @param type Leaderboard type
     * @return Future completing with true if successful
     */
    public CompletableFuture<Boolean> refreshCache(LeaderboardType type) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dbManager.getConnection()) {
                // Recalculate ranks based on value (descending order)
                String updateRanksSql = dbManager.isMySQL()
                    ? "UPDATE quest_leaderboard_entries e " +
                      "SET rank = ( " +
                      "  SELECT COUNT(*) + 1 " +
                      "  FROM quest_leaderboard_entries e2 " +
                      "  WHERE e2.leaderboard_type = e.leaderboard_type " +
                      "  AND e2.value > e.value " +
                      ") " +
                      "WHERE leaderboard_type = ?"
                    : "UPDATE quest_leaderboard_entries " +
                      "SET rank = ( " +
                      "  SELECT COUNT(*) + 1 " +
                      "  FROM quest_leaderboard_entries AS e2 " +
                      "  WHERE e2.leaderboard_type = quest_leaderboard_entries.leaderboard_type " +
                      "  AND e2.value > quest_leaderboard_entries.value " +
                      ") " +
                      "WHERE leaderboard_type = ?";

                try (PreparedStatement stmt = conn.prepareStatement(updateRanksSql)) {
                    stmt.setString(1, type.name());
                    int updated = stmt.executeUpdate();
                    logger.debug("Refreshed " + updated + " rank entries for " + type.name());
                }

                // Update cache timestamp
                String cacheSql = dbManager.isMySQL()
                    ? "INSERT INTO quest_leaderboard_cache (leaderboard_type, cache_data, updated_at, expires_at) " +
                      "VALUES (?, '{}', NOW(), DATE_ADD(NOW(), INTERVAL ? SECOND)) " +
                      "ON DUPLICATE KEY UPDATE updated_at = NOW(), expires_at = DATE_ADD(NOW(), INTERVAL ? SECOND)"
                    : "INSERT OR REPLACE INTO quest_leaderboard_cache (leaderboard_type, cache_data, updated_at, expires_at) " +
                      "VALUES (?, '{}', datetime('now'), datetime('now', '+' || ? || ' seconds'))";

                try (PreparedStatement stmt = conn.prepareStatement(cacheSql)) {
                    stmt.setString(1, type.name());
                    if (dbManager.isMySQL()) {
                        stmt.setInt(2, type.getUpdateIntervalSeconds());
                        stmt.setInt(3, type.getUpdateIntervalSeconds());
                    } else {
                        stmt.setInt(2, type.getUpdateIntervalSeconds());
                    }
                    stmt.executeUpdate();
                }

                logger.info("Cache refreshed for " + type.name());
                return true;

            } catch (SQLException e) {
                logger.error("Failed to refresh cache for " + type.name(), e);
                throw new RuntimeException("Database error refreshing cache", e);
            }
        }, dbManager.getExecutor());
    }

    /**
     * Create a LeaderboardEntry from a ResultSet row.
     *
     * @param rs ResultSet positioned at a row
     * @param type Leaderboard type
     * @return LeaderboardEntry instance
     * @throws SQLException if column access fails
     */
    private LeaderboardEntry createEntryFromResultSet(ResultSet rs, LeaderboardType type) throws SQLException {
        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
        String playerName = rs.getString("player_name");
        int value = rs.getInt("value");
        int rank = rs.getInt("rank");

        Instant updatedAt;
        if (dbManager.isMySQL()) {
            updatedAt = rs.getTimestamp("updated_at").toInstant();
        } else {
            String timestampStr = rs.getString("updated_at");
            updatedAt = Instant.parse(timestampStr);
        }

        return new LeaderboardEntry(playerUuid, playerName, type, value, rank, updatedAt);
    }
}
