package org.fourz.RVNKQuests.data;

import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.dto.QuestChainProgressDTO;
import org.fourz.rvnkcore.data.FallbackTracker;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SQL implementation of {@link IChainProgressRepository}.
 *
 * <p>Persists player chain progress to the {@code quest_chain_progress} table
 * using HikariCP-backed connections from {@link DatabaseManager}.
 * All operations run asynchronously on the database executor thread pool.</p>
 *
 * <p>Supports both MySQL (INSERT … ON DUPLICATE KEY UPDATE) and SQLite
 * (INSERT OR REPLACE) upsert semantics, mirroring the pattern used by
 * {@code QuestProgressRepositoryImpl}.</p>
 *
 * @since 1.1
 */
public class ChainProgressRepositoryImpl implements IChainProgressRepository {

    private final DatabaseManager databaseManager;
    private final FallbackTracker fallbackTracker;
    private final LogManager logger;
    private final boolean isMySQL;

    /** Prefixed table name, e.g. {@code quests_quest_chain_progress} */
    private final String tblChainProgress;

    /**
     * Creates a new repository and initialises the {@code quest_chain_progress} table.
     *
     * @param plugin          The plugin instance (used for logging)
     * @param databaseManager The database manager providing connections and executor
     */
    public ChainProgressRepositoryImpl(RVNKQuests plugin, DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.fallbackTracker = databaseManager.getFallbackTracker();
        this.logger = LogManager.getInstance(plugin, "ChainProgressRepository");
        this.isMySQL = databaseManager.getType() == DatabaseManager.DatabaseType.MYSQL;
        this.tblChainProgress = databaseManager.table("quest_chain_progress");

        initTable();
    }

    // ==================== Schema Bootstrap ====================

    /**
     * Creates the {@code quest_chain_progress} table if it does not yet exist.
     * Runs synchronously during construction — called once at plugin enable time
     * before any player joins.
     */
    private void initTable() {
        if (fallbackTracker.isInFallbackMode()) {
            logger.debug("Skipping chain progress table init — database in fallback mode");
            return;
        }

        String sql = isMySQL
            ? "CREATE TABLE IF NOT EXISTS " + tblChainProgress + " ("
                + "player_uuid VARCHAR(36) NOT NULL,"
                + "chain_id VARCHAR(100) NOT NULL,"
                + "current_quest_id VARCHAR(100),"
                + "completed BOOLEAN DEFAULT FALSE,"
                + "started_at BIGINT NOT NULL,"
                + "last_updated BIGINT NOT NULL,"
                + "PRIMARY KEY (player_uuid, chain_id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            : "CREATE TABLE IF NOT EXISTS " + tblChainProgress + " ("
                + "player_uuid TEXT NOT NULL,"
                + "chain_id TEXT NOT NULL,"
                + "current_quest_id TEXT,"
                + "completed INTEGER DEFAULT 0,"
                + "started_at INTEGER NOT NULL,"
                + "last_updated INTEGER NOT NULL,"
                + "PRIMARY KEY (player_uuid, chain_id)"
                + ")";

        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            fallbackTracker.recordSuccess();
            logger.debug("quest_chain_progress table ready");
        } catch (SQLException e) {
            fallbackTracker.recordFailure();
            logger.error("Failed to create quest_chain_progress table", e);
        }
    }

    // ==================== IChainProgressRepository ====================

    @Override
    public CompletableFuture<Optional<QuestChainProgressDTO>> loadProgress(UUID playerUuid, String chainId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tblChainProgress
                + " WHERE player_uuid = ? AND chain_id = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, playerUuid.toString());
                pstmt.setString(2, chainId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        fallbackTracker.recordSuccess();
                        return Optional.of(mapResultSet(rs));
                    }
                }
                fallbackTracker.recordSuccess();
                return Optional.empty();

            } catch (SQLException e) {
                fallbackTracker.recordFailure();
                logger.error("Failed to load chain progress for " + playerUuid + " / " + chainId, e);
                throw new RuntimeException("Chain progress load failed", e);
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<List<QuestChainProgressDTO>> loadAllProgress(UUID playerUuid) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tblChainProgress + " WHERE player_uuid = ?";
            List<QuestChainProgressDTO> results = new ArrayList<>();

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, playerUuid.toString());

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(mapResultSet(rs));
                    }
                }
                fallbackTracker.recordSuccess();
                return results;

            } catch (SQLException e) {
                fallbackTracker.recordFailure();
                logger.error("Failed to load all chain progress for " + playerUuid, e);
                throw new RuntimeException("Chain progress load-all failed", e);
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Boolean> saveProgress(QuestChainProgressDTO progress) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = isMySQL
                ? "INSERT INTO " + tblChainProgress
                    + " (player_uuid, chain_id, current_quest_id, completed, started_at, last_updated)"
                    + " VALUES (?, ?, ?, ?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE"
                    + " current_quest_id = VALUES(current_quest_id),"
                    + " completed = VALUES(completed),"
                    + " started_at = VALUES(started_at),"
                    + " last_updated = VALUES(last_updated)"
                : "INSERT OR REPLACE INTO " + tblChainProgress
                    + " (player_uuid, chain_id, current_quest_id, completed, started_at, last_updated)"
                    + " VALUES (?, ?, ?, ?, ?, ?)";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, progress.playerUuid().toString());
                pstmt.setString(2, progress.chainId());
                pstmt.setString(3, progress.currentQuestId());
                pstmt.setBoolean(4, progress.completed());
                pstmt.setLong(5, progress.startedAt());
                pstmt.setLong(6, progress.lastUpdated());

                int affected = pstmt.executeUpdate();
                fallbackTracker.recordSuccess();
                logger.debug("Saved chain progress for " + progress.playerUuid()
                    + " on chain " + progress.chainId());
                return affected > 0;

            } catch (SQLException e) {
                fallbackTracker.recordFailure();
                logger.error("Failed to save chain progress for "
                    + progress.playerUuid() + " / " + progress.chainId(), e);
                throw new RuntimeException("Chain progress save failed", e);
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Boolean> deleteProgress(UUID playerUuid, String chainId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + tblChainProgress
                + " WHERE player_uuid = ? AND chain_id = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, playerUuid.toString());
                pstmt.setString(2, chainId);

                int affected = pstmt.executeUpdate();
                fallbackTracker.recordSuccess();
                return affected > 0;

            } catch (SQLException e) {
                fallbackTracker.recordFailure();
                logger.error("Failed to delete chain progress for "
                    + playerUuid + " / " + chainId, e);
                throw new RuntimeException("Chain progress delete failed", e);
            }
        }, databaseManager.getExecutor());
    }

    // ==================== Private Helpers ====================

    private QuestChainProgressDTO mapResultSet(ResultSet rs) throws SQLException {
        return new QuestChainProgressDTO(
            UUID.fromString(rs.getString("player_uuid")),
            rs.getString("chain_id"),
            rs.getString("current_quest_id"),
            rs.getBoolean("completed"),
            rs.getLong("started_at"),
            rs.getLong("last_updated")
        );
    }
}
