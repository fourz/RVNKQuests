package org.fourz.RVNKQuests.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.dto.QuestObjectiveProgressDTO;
import org.fourz.RVNKQuests.data.dto.QuestProgressDTO;
import org.fourz.RVNKQuests.data.dto.QuestRewardClaimedDTO;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.util.log.LogManager;

import java.lang.reflect.Type;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * SQL implementation of IQuestProgressRepository using MySQL/SQLite.
 *
 * <p>All database operations run asynchronously to avoid blocking
 * the Minecraft main thread.</p>
 */
public class QuestProgressRepositoryImpl implements IQuestProgressRepository {

    private final DatabaseManager databaseManager;
    private final FallbackTracker fallbackTracker;
    private final LogManager logger;
    private final Gson gson;
    private final boolean isMySQL;

    // Prefixed table names (e.g. "quests_quest_progress" when prefix is "quests_")
    private final String tblProgress;
    private final String tblObjective;
    private final String tblRewards;

    /**
     * Creates a new repository implementation.
     *
     * @param plugin The plugin instance
     * @param databaseManager The database manager
     */
    public QuestProgressRepositoryImpl(RVNKQuests plugin, DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.fallbackTracker = databaseManager.getFallbackTracker();
        this.logger = LogManager.getInstance(plugin, "QuestProgressRepository");
        this.gson = new GsonBuilder().create();
        this.isMySQL = databaseManager.getType() == DatabaseManager.DatabaseType.MYSQL;
        this.tblProgress = databaseManager.table("quest_progress");
        this.tblObjective = databaseManager.table("quest_objective_progress");
        this.tblRewards = databaseManager.table("quest_rewards_claimed");
    }

    // ==================== Quest Progress Operations ====================

    @Override
    public CompletableFuture<Boolean> saveProgress(QuestProgressDTO progress) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = isMySQL
                ? "INSERT INTO " + tblProgress + " (player_uuid, quest_id, state, path_choice, started_at, completed_at, metadata) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE state = VALUES(state), path_choice = VALUES(path_choice), " +
                  "started_at = VALUES(started_at), completed_at = VALUES(completed_at), metadata = VALUES(metadata)"
                : "INSERT OR REPLACE INTO " + tblProgress + " (player_uuid, quest_id, state, path_choice, started_at, completed_at, metadata, updated_at) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now'))";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, progress.playerUuid().toString());
                pstmt.setString(2, progress.questId());
                pstmt.setString(3, progress.state().name());
                pstmt.setString(4, progress.pathChoice());

                if (isMySQL) {
                    pstmt.setTimestamp(5, progress.startedAt() != null
                        ? Timestamp.from(progress.startedAt()) : null);
                    pstmt.setTimestamp(6, progress.completedAt() != null
                        ? Timestamp.from(progress.completedAt()) : null);
                } else {
                    pstmt.setString(5, progress.startedAt() != null
                        ? progress.startedAt().toString() : null);
                    pstmt.setString(6, progress.completedAt() != null
                        ? progress.completedAt().toString() : null);
                }

                pstmt.setString(7, gson.toJson(progress.metadata()));

                int affected = pstmt.executeUpdate();
                fallbackTracker.recordSuccess();
                logger.debug("Saved progress for " + progress.playerUuid() + " on " + progress.questId());
                return affected > 0;

            } catch (SQLException e) {
                fallbackTracker.recordFailure();
                logger.error("Failed to save quest progress", e);
                throw new RuntimeException("Quest progress save failed", e);
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Optional<QuestProgressDTO>> getProgress(UUID playerUuid, String questId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tblProgress + " WHERE player_uuid = ? AND quest_id = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, playerUuid.toString());
                pstmt.setString(2, questId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        fallbackTracker.recordSuccess();
                        return Optional.of(mapProgressResultSet(rs));
                    }
                }
                fallbackTracker.recordSuccess();
                return Optional.empty();

            } catch (SQLException e) {
                fallbackTracker.recordFailure();
                logger.error("Failed to get quest progress", e);
                throw new RuntimeException("Quest progress query failed", e);
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<List<QuestProgressDTO>> getAllProgressForPlayer(UUID playerUuid) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tblProgress + " WHERE player_uuid = ?";
            List<QuestProgressDTO> results = new ArrayList<>();

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, playerUuid.toString());

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(mapProgressResultSet(rs));
                    }
                }
                fallbackTracker.recordSuccess();
                return results;

            } catch (SQLException e) {
                fallbackTracker.recordFailure();
                logger.error("Failed to get all progress for player", e);
                throw new RuntimeException("Query failed", e);
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<List<QuestProgressDTO>> getAllProgressForQuest(String questId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tblProgress + " WHERE quest_id = ?";
            List<QuestProgressDTO> results = new ArrayList<>();

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, questId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(mapProgressResultSet(rs));
                    }
                }
                fallbackTracker.recordSuccess();
                return results;

            } catch (SQLException e) {
                fallbackTracker.recordFailure();
                logger.error("Failed to get all progress for quest", e);
                throw new RuntimeException("Query failed", e);
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<List<QuestProgressDTO>> getProgressByState(String questId, QuestState state) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tblProgress + " WHERE quest_id = ? AND state = ?";
            List<QuestProgressDTO> results = new ArrayList<>();

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, questId);
                pstmt.setString(2, state.name());

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(mapProgressResultSet(rs));
                    }
                }
                fallbackTracker.recordSuccess();
                return results;

            } catch (SQLException e) {
                fallbackTracker.recordFailure();
                logger.error("Failed to get progress by state", e);
                throw new RuntimeException("Query failed", e);
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Boolean> deleteProgress(UUID playerUuid, String questId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + tblProgress + " WHERE player_uuid = ? AND quest_id = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, playerUuid.toString());
                pstmt.setString(2, questId);

                int affected = pstmt.executeUpdate();
                fallbackTracker.recordSuccess();
                return affected > 0;

            } catch (SQLException e) {
                fallbackTracker.recordFailure();
                logger.error("Failed to delete progress", e);
                throw new RuntimeException("Delete failed", e);
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Boolean> deleteAllProgressForPlayer(UUID playerUuid) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + tblProgress + " WHERE player_uuid = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, playerUuid.toString());

                int affected = pstmt.executeUpdate();
                fallbackTracker.recordSuccess();
                return affected > 0;

            } catch (SQLException e) {
                fallbackTracker.recordFailure();
                logger.error("Failed to delete all progress for player", e);
                throw new RuntimeException("Delete failed", e);
            }
        }, databaseManager.getExecutor());
    }

    // ==================== Objective Progress Operations ====================

    @Override
    public CompletableFuture<Boolean> saveObjectiveProgress(QuestObjectiveProgressDTO objective) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = isMySQL
                ? "INSERT INTO " + tblObjective + " (player_uuid, quest_id, objective_id, progress_count, target_count, is_completed, completed_at, metadata) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE progress_count = VALUES(progress_count), is_completed = VALUES(is_completed), " +
                  "completed_at = VALUES(completed_at), metadata = VALUES(metadata)"
                : "INSERT OR REPLACE INTO " + tblObjective + " (player_uuid, quest_id, objective_id, progress_count, target_count, is_completed, completed_at, metadata, updated_at) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, objective.playerUuid().toString());
                pstmt.setString(2, objective.questId());
                pstmt.setString(3, objective.objectiveId());
                pstmt.setInt(4, objective.progressCount());
                pstmt.setInt(5, objective.targetCount());
                pstmt.setBoolean(6, objective.completed());

                if (isMySQL) {
                    pstmt.setTimestamp(7, objective.completedAt() != null
                        ? Timestamp.from(objective.completedAt()) : null);
                } else {
                    pstmt.setString(7, objective.completedAt() != null
                        ? objective.completedAt().toString() : null);
                }

                pstmt.setString(8, gson.toJson(objective.metadata()));

                int affected = pstmt.executeUpdate();
                fallbackTracker.recordSuccess();
                return affected > 0;

            } catch (SQLException e) {
                fallbackTracker.recordFailure();
                logger.error("Failed to save objective progress", e);
                throw new RuntimeException("Save failed", e);
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Optional<QuestObjectiveProgressDTO>> getObjectiveProgress(
            UUID playerUuid, String questId, String objectiveId) {

        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tblObjective + " WHERE player_uuid = ? AND quest_id = ? AND objective_id = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, playerUuid.toString());
                pstmt.setString(2, questId);
                pstmt.setString(3, objectiveId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        fallbackTracker.recordSuccess();
                        return Optional.of(mapObjectiveResultSet(rs));
                    }
                }
                fallbackTracker.recordSuccess();
                return Optional.empty();

            } catch (SQLException e) {
                fallbackTracker.recordFailure();
                logger.error("Failed to get objective progress", e);
                throw new RuntimeException("Query failed", e);
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<List<QuestObjectiveProgressDTO>> getAllObjectiveProgress(
            UUID playerUuid, String questId) {

        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tblObjective + " WHERE player_uuid = ? AND quest_id = ?";
            List<QuestObjectiveProgressDTO> results = new ArrayList<>();

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, playerUuid.toString());
                pstmt.setString(2, questId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(mapObjectiveResultSet(rs));
                    }
                }
                fallbackTracker.recordSuccess();
                return results;

            } catch (SQLException e) {
                fallbackTracker.recordFailure();
                logger.error("Failed to get all objective progress", e);
                throw new RuntimeException("Query failed", e);
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Boolean> deleteObjectiveProgress(UUID playerUuid, String questId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + tblObjective + " WHERE player_uuid = ? AND quest_id = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, playerUuid.toString());
                pstmt.setString(2, questId);

                int affected = pstmt.executeUpdate();
                fallbackTracker.recordSuccess();
                return affected >= 0;

            } catch (SQLException e) {
                fallbackTracker.recordFailure();
                logger.error("Failed to delete objective progress", e);
                throw new RuntimeException("Delete failed", e);
            }
        }, databaseManager.getExecutor());
    }

    // ==================== Reward Tracking Operations ====================

    @Override
    public CompletableFuture<Boolean> saveRewardClaimed(QuestRewardClaimedDTO rewardClaimed) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = isMySQL
                ? "INSERT IGNORE INTO " + tblRewards + " (player_uuid, quest_id, reward_id, claimed_at) VALUES (?, ?, ?, ?)"
                : "INSERT OR IGNORE INTO " + tblRewards + " (player_uuid, quest_id, reward_id, claimed_at) VALUES (?, ?, ?, ?)";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, rewardClaimed.playerUuid().toString());
                pstmt.setString(2, rewardClaimed.questId());
                pstmt.setString(3, rewardClaimed.rewardId());

                if (isMySQL) {
                    pstmt.setTimestamp(4, Timestamp.from(rewardClaimed.claimedAt()));
                } else {
                    pstmt.setString(4, rewardClaimed.claimedAt().toString());
                }

                int affected = pstmt.executeUpdate();
                fallbackTracker.recordSuccess();
                return affected > 0;

            } catch (SQLException e) {
                fallbackTracker.recordFailure();
                logger.error("Failed to save reward claimed", e);
                throw new RuntimeException("Save failed", e);
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Boolean> hasClaimedReward(UUID playerUuid, String questId, String rewardId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM " + tblRewards + " WHERE player_uuid = ? AND quest_id = ? AND reward_id = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, playerUuid.toString());
                pstmt.setString(2, questId);
                pstmt.setString(3, rewardId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    boolean claimed = rs.next();
                    fallbackTracker.recordSuccess();
                    return claimed;
                }

            } catch (SQLException e) {
                fallbackTracker.recordFailure();
                logger.error("Failed to check reward claimed", e);
                throw new RuntimeException("Query failed", e);
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<List<QuestRewardClaimedDTO>> getClaimedRewards(UUID playerUuid, String questId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tblRewards + " WHERE player_uuid = ? AND quest_id = ?";
            List<QuestRewardClaimedDTO> results = new ArrayList<>();

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, playerUuid.toString());
                pstmt.setString(2, questId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(mapRewardResultSet(rs));
                    }
                }
                fallbackTracker.recordSuccess();
                return results;

            } catch (SQLException e) {
                fallbackTracker.recordFailure();
                logger.error("Failed to get claimed rewards", e);
                throw new RuntimeException("Query failed", e);
            }
        }, databaseManager.getExecutor());
    }

    // ==================== Utility Operations ====================

    @Override
    public boolean isInFallbackMode() {
        return fallbackTracker.isInFallbackMode();
    }

    @Override
    public CompletableFuture<Void> flush() {
        // SQL implementation doesn't need flushing
        return CompletableFuture.completedFuture(null);
    }

    // ==================== Private Helper Methods ====================

    private QuestProgressDTO mapProgressResultSet(ResultSet rs) throws SQLException {
        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
        String questId = rs.getString("quest_id");
        QuestState state = QuestState.valueOf(rs.getString("state"));
        String pathChoice = rs.getString("path_choice");

        Instant startedAt = null;
        Instant completedAt = null;

        if (isMySQL) {
            Timestamp startTs = rs.getTimestamp("started_at");
            Timestamp completeTs = rs.getTimestamp("completed_at");
            startedAt = startTs != null ? startTs.toInstant() : null;
            completedAt = completeTs != null ? completeTs.toInstant() : null;
        } else {
            String startStr = rs.getString("started_at");
            String completeStr = rs.getString("completed_at");
            startedAt = startStr != null ? Instant.parse(startStr) : null;
            completedAt = completeStr != null ? Instant.parse(completeStr) : null;
        }

        Map<String, Object> metadata = Map.of();
        String metadataJson = rs.getString("metadata");
        if (metadataJson != null && !metadataJson.isEmpty()) {
            Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
            metadata = gson.fromJson(metadataJson, mapType);
        }

        return QuestProgressDTO.builder()
            .playerUuid(playerUuid)
            .questId(questId)
            .state(state)
            .pathChoice(pathChoice)
            .startedAt(startedAt)
            .completedAt(completedAt)
            .metadata(metadata)
            .build();
    }

    private QuestObjectiveProgressDTO mapObjectiveResultSet(ResultSet rs) throws SQLException {
        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
        String questId = rs.getString("quest_id");
        String objectiveId = rs.getString("objective_id");
        int progressCount = rs.getInt("progress_count");
        int targetCount = rs.getInt("target_count");
        boolean completed = rs.getBoolean("is_completed");

        Instant completedAt = null;
        if (isMySQL) {
            Timestamp ts = rs.getTimestamp("completed_at");
            completedAt = ts != null ? ts.toInstant() : null;
        } else {
            String str = rs.getString("completed_at");
            completedAt = str != null ? Instant.parse(str) : null;
        }

        Map<String, Object> metadata = Map.of();
        String metadataJson = rs.getString("metadata");
        if (metadataJson != null && !metadataJson.isEmpty()) {
            Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
            metadata = gson.fromJson(metadataJson, mapType);
        }

        return QuestObjectiveProgressDTO.builder()
            .playerUuid(playerUuid)
            .questId(questId)
            .objectiveId(objectiveId)
            .progressCount(progressCount)
            .targetCount(targetCount)
            .completed(completed)
            .completedAt(completedAt)
            .metadata(metadata)
            .build();
    }

    private QuestRewardClaimedDTO mapRewardResultSet(ResultSet rs) throws SQLException {
        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
        String questId = rs.getString("quest_id");
        String rewardId = rs.getString("reward_id");

        Instant claimedAt;
        if (isMySQL) {
            claimedAt = rs.getTimestamp("claimed_at").toInstant();
        } else {
            claimedAt = Instant.parse(rs.getString("claimed_at"));
        }

        return QuestRewardClaimedDTO.builder()
            .playerUuid(playerUuid)
            .questId(questId)
            .rewardId(rewardId)
            .claimedAt(claimedAt)
            .build();
    }
}
