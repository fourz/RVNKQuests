package org.fourz.RVNKQuests.data.repository;

import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.DatabaseManager;
import org.fourz.RVNKQuests.data.dto.JournalEntryDTO;
import org.fourz.RVNKQuests.data.dto.JournalEntryDTO.JournalAction;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository implementation for quest journal entry persistence.
 *
 * <p>Provides async data access operations using HikariCP connection pool.
 * Supports both MySQL and SQLite backends with automatic schema adaptation.</p>
 *
 * <p>Pattern compliance:</p>
 * <ul>
 *   <li>Async operations return CompletableFuture</li>
 *   <li>Uses DatabaseManager executor for non-blocking operations</li>
 *   <li>Handles MySQL/SQLite timestamp differences</li>
 *   <li>Proper connection management with try-with-resources</li>
 * </ul>
 */
public class JournalRepositoryImpl implements IJournalRepository {

    private final RVNKQuests plugin;
    private final DatabaseManager databaseManager;
    private final LogManager logger;
    private final String tblJournalEntries;

    /**
     * Creates a new JournalRepositoryImpl.
     *
     * @param plugin The plugin instance
     * @param databaseManager The database manager
     */
    public JournalRepositoryImpl(RVNKQuests plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.logger = LogManager.getInstance(plugin, "JournalRepository");
        this.tblJournalEntries = databaseManager.table("quest_journal_entries");
    }

    @Override
    public CompletableFuture<JournalEntryDTO> save(JournalEntryDTO entry) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO " + tblJournalEntries + " (player_uuid, quest_id, action, timestamp, details) " +
                         "VALUES (?, ?, ?, ?, ?)";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, entry.playerUuid().toString());
                stmt.setString(2, entry.questId());
                stmt.setString(3, entry.action().name());

                // Schema uses BIGINT for timestamp (epoch millis)
                stmt.setLong(4, entry.timestamp().toEpochMilli());

                stmt.setString(5, entry.details());

                int affectedRows = stmt.executeUpdate();

                if (affectedRows == 0) {
                    throw new SQLException("Failed to insert journal entry, no rows affected");
                }

                // Retrieve generated ID
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        long id = generatedKeys.getLong(1);
                        return new JournalEntryDTO(
                            id,
                            entry.playerUuid(),
                            entry.questId(),
                            entry.action(),
                            entry.timestamp(),
                            entry.details()
                        );
                    } else {
                        throw new SQLException("Failed to retrieve generated ID for journal entry");
                    }
                }

            } catch (SQLException e) {
                logger.error("Failed to save journal entry for player " + entry.playerUuid(), e);
                throw new RuntimeException("Failed to save journal entry", e);
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<List<JournalEntryDTO>> findByPlayer(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tblJournalEntries + " WHERE player_uuid = ? ORDER BY timestamp DESC";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    return mapResultSet(rs);
                }

            } catch (SQLException e) {
                logger.error("Failed to find journal entries for player " + playerUuid, e);
                return new ArrayList<>();
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<List<JournalEntryDTO>> findByPlayerAndQuest(UUID playerUuid, String questId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tblJournalEntries + " " +
                         "WHERE player_uuid = ? AND quest_id = ? " +
                         "ORDER BY timestamp DESC";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, questId);

                try (ResultSet rs = stmt.executeQuery()) {
                    return mapResultSet(rs);
                }

            } catch (SQLException e) {
                logger.error("Failed to find journal entries for player " + playerUuid + " and quest " + questId, e);
                return new ArrayList<>();
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<List<JournalEntryDTO>> findByPlayerAndAction(UUID playerUuid, JournalAction action) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tblJournalEntries + " " +
                         "WHERE player_uuid = ? AND action = ? " +
                         "ORDER BY timestamp DESC";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, action.name());

                try (ResultSet rs = stmt.executeQuery()) {
                    return mapResultSet(rs);
                }

            } catch (SQLException e) {
                logger.error("Failed to find journal entries for player " + playerUuid + " and action " + action, e);
                return new ArrayList<>();
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<List<JournalEntryDTO>> findByPlayerAndTimeRange(
            UUID playerUuid, Instant startTime, Instant endTime) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tblJournalEntries + " " +
                         "WHERE player_uuid = ? AND timestamp >= ? AND timestamp <= ? " +
                         "ORDER BY timestamp DESC";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());

                stmt.setLong(2, startTime.toEpochMilli());
                stmt.setLong(3, endTime.toEpochMilli());

                try (ResultSet rs = stmt.executeQuery()) {
                    return mapResultSet(rs);
                }

            } catch (SQLException e) {
                logger.error("Failed to find journal entries in time range for player " + playerUuid, e);
                return new ArrayList<>();
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<List<JournalEntryDTO>> findRecentByPlayer(UUID playerUuid, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tblJournalEntries + " " +
                         "WHERE player_uuid = ? " +
                         "ORDER BY timestamp DESC " +
                         "LIMIT ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());
                stmt.setInt(2, limit);

                try (ResultSet rs = stmt.executeQuery()) {
                    return mapResultSet(rs);
                }

            } catch (SQLException e) {
                logger.error("Failed to find recent journal entries for player " + playerUuid, e);
                return new ArrayList<>();
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Integer> deleteByPlayer(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + tblJournalEntries + " WHERE player_uuid = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());
                int deleted = stmt.executeUpdate();

                logger.info("Deleted " + deleted + " journal entries for player " + playerUuid);
                return deleted;

            } catch (SQLException e) {
                logger.error("Failed to delete journal entries for player " + playerUuid, e);
                return 0;
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Integer> deleteByPlayerAndQuest(UUID playerUuid, String questId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + tblJournalEntries + " WHERE player_uuid = ? AND quest_id = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, questId);
                int deleted = stmt.executeUpdate();

                logger.debug("Deleted " + deleted + " journal entries for player " + playerUuid + " quest " + questId);
                return deleted;

            } catch (SQLException e) {
                logger.error("Failed to delete journal entries for player " + playerUuid + " and quest " + questId, e);
                return 0;
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Integer> deleteOlderThan(Instant beforeTime) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + tblJournalEntries + " WHERE timestamp < ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setLong(1, beforeTime.toEpochMilli());

                int deleted = stmt.executeUpdate();
                logger.info("Deleted " + deleted + " journal entries older than " + beforeTime);
                return deleted;

            } catch (SQLException e) {
                logger.error("Failed to delete old journal entries", e);
                return 0;
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Long> countByPlayer(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM " + tblJournalEntries + " WHERE player_uuid = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                    return 0L;
                }

            } catch (SQLException e) {
                logger.error("Failed to count journal entries for player " + playerUuid, e);
                return 0L;
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public boolean isAvailable() {
        return databaseManager != null && databaseManager.isInitialized();
    }

    /**
     * Maps a ResultSet to a list of JournalEntryDTO objects.
     *
     * @param rs The ResultSet to map
     * @return List of journal entries
     * @throws SQLException if database access fails
     */
    private List<JournalEntryDTO> mapResultSet(ResultSet rs) throws SQLException {
        List<JournalEntryDTO> entries = new ArrayList<>();

        while (rs.next()) {
            long id = rs.getLong("id");
            UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
            String questId = rs.getString("quest_id");
            JournalAction action = JournalAction.valueOf(rs.getString("action"));

            // Schema uses BIGINT (epoch millis) for timestamp
            Instant timestamp = Instant.ofEpochMilli(rs.getLong("timestamp"));

            String details = rs.getString("details");

            entries.add(new JournalEntryDTO(id, playerUuid, questId, action, timestamp, details));
        }

        return entries;
    }
}
