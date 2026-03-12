package org.fourz.RVNKQuests.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.dto.ObjectiveDTO;
import org.fourz.RVNKQuests.data.dto.ObjectiveType;
import org.fourz.RVNKQuests.data.dto.QuestDTO;
import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.data.dto.RewardType;
import org.fourz.rvnkcore.data.FallbackTracker;
import org.fourz.rvnkcore.util.log.LogManager;

import java.lang.reflect.Type;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * SQL implementation of IQuestRepository for quest definition persistence.
 * Supports MySQL and SQLite with table prefix substitution.
 */
public class QuestRepositoryImpl implements IQuestRepository {

    private final DatabaseManager databaseManager;
    private final FallbackTracker fallbackTracker;
    private final LogManager logger;
    private final Gson gson;
    private final boolean isMySQL;

    private final String tblDefinitions;
    private final String tblObjectives;
    private final String tblRewards;

    public QuestRepositoryImpl(RVNKQuests plugin, DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.fallbackTracker = databaseManager.getFallbackTracker();
        this.logger = LogManager.getInstance(plugin, "QuestRepository");
        this.gson = new GsonBuilder().create();
        this.isMySQL = databaseManager.getType() == DatabaseManager.DatabaseType.MYSQL;
        this.tblDefinitions = databaseManager.table("quest_definitions");
        this.tblObjectives = databaseManager.table("quest_definition_objectives");
        this.tblRewards = databaseManager.table("quest_definition_rewards");
    }

    // ==================== Quest CRUD Operations ====================

    @Override
    public CompletableFuture<Boolean> save(QuestDTO quest) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = isMySQL
                ? "INSERT INTO " + tblDefinitions + " (quest_id, name, description, category, repeatable, cooldown_minutes, prerequisites, metadata) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description), " +
                  "category = VALUES(category), repeatable = VALUES(repeatable), cooldown_minutes = VALUES(cooldown_minutes), " +
                  "prerequisites = VALUES(prerequisites), metadata = VALUES(metadata)"
                : "INSERT OR REPLACE INTO " + tblDefinitions + " (quest_id, name, description, category, repeatable, cooldown_minutes, prerequisites, metadata, updated_at) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, quest.questId());
                pstmt.setString(2, quest.name());
                pstmt.setString(3, quest.description());
                pstmt.setString(4, quest.category());
                pstmt.setBoolean(5, quest.repeatable());
                pstmt.setInt(6, quest.cooldownMinutes());
                pstmt.setString(7, quest.prerequisites().isEmpty() ? null : gson.toJson(quest.prerequisites()));
                pstmt.setString(8, quest.metadata().isEmpty() ? null : gson.toJson(quest.metadata()));

                int rows = pstmt.executeUpdate();
                fallbackTracker.recordSuccess();

                // Save objectives and rewards
                if (rows > 0 || isMySQL) {
                    saveObjectivesInternal(conn, quest.questId(), quest.objectives());
                    saveRewardsInternal(conn, quest.questId(), quest.rewards());
                }

                logger.debug("Saved quest definition: " + quest.questId());
                return true;

            } catch (SQLException e) {
                logger.error("Failed to save quest definition: " + quest.questId(), e);
                fallbackTracker.recordFailure();
                return false;
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Optional<QuestDTO>> findById(String questId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tblDefinitions + " WHERE quest_id = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, questId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        QuestDTO quest = mapResultSetToQuest(rs);
                        // Load objectives and rewards
                        List<ObjectiveDTO> objectives = findObjectivesInternal(conn, questId);
                        List<RewardDTO> rewards = findRewardsInternal(conn, questId);
                        quest = quest.withObjectives(objectives).withRewards(rewards);
                        fallbackTracker.recordSuccess();
                        return Optional.of(quest);
                    }
                }

                fallbackTracker.recordSuccess();
                return Optional.empty();

            } catch (SQLException e) {
                logger.error("Failed to find quest by ID: " + questId, e);
                fallbackTracker.recordFailure();
                return Optional.empty();
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Optional<QuestDTO>> findByName(String name) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tblDefinitions + " WHERE LOWER(name) = LOWER(?)";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, name);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        QuestDTO quest = mapResultSetToQuest(rs);
                        List<ObjectiveDTO> objectives = findObjectivesInternal(conn, quest.questId());
                        List<RewardDTO> rewards = findRewardsInternal(conn, quest.questId());
                        quest = quest.withObjectives(objectives).withRewards(rewards);
                        fallbackTracker.recordSuccess();
                        return Optional.of(quest);
                    }
                }

                fallbackTracker.recordSuccess();
                return Optional.empty();

            } catch (SQLException e) {
                logger.error("Failed to find quest by name: " + name, e);
                fallbackTracker.recordFailure();
                return Optional.empty();
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<List<QuestDTO>> findAll() {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tblDefinitions + " ORDER BY name";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                List<QuestDTO> quests = new ArrayList<>();
                while (rs.next()) {
                    QuestDTO quest = mapResultSetToQuest(rs);
                    List<ObjectiveDTO> objectives = findObjectivesInternal(conn, quest.questId());
                    List<RewardDTO> rewards = findRewardsInternal(conn, quest.questId());
                    quests.add(quest.withObjectives(objectives).withRewards(rewards));
                }

                fallbackTracker.recordSuccess();
                logger.debug("Found " + quests.size() + " quest definitions");
                return quests;

            } catch (SQLException e) {
                logger.error("Failed to find all quest definitions", e);
                fallbackTracker.recordFailure();
                return List.of();
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Boolean> deleteById(String questId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            // CASCADE delete handles objectives and rewards
            String sql = "DELETE FROM " + tblDefinitions + " WHERE quest_id = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, questId);
                int rows = pstmt.executeUpdate();
                fallbackTracker.recordSuccess();
                logger.debug("Deleted quest definition: " + questId + " (rows: " + rows + ")");
                return rows > 0;

            } catch (SQLException e) {
                logger.error("Failed to delete quest: " + questId, e);
                fallbackTracker.recordFailure();
                return false;
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Boolean> exists(String questId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM " + tblDefinitions + " WHERE quest_id = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, questId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    fallbackTracker.recordSuccess();
                    return rs.next();
                }

            } catch (SQLException e) {
                logger.error("Failed to check quest existence: " + questId, e);
                fallbackTracker.recordFailure();
                return false;
            }
        }, databaseManager.getExecutor());
    }

    // ==================== Query Operations ====================

    @Override
    public CompletableFuture<List<QuestDTO>> findByCategory(String category) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tblDefinitions + " WHERE category = ? ORDER BY name";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, category);
                return executeQuestListQuery(conn, pstmt);

            } catch (SQLException e) {
                logger.error("Failed to find quests by category: " + category, e);
                fallbackTracker.recordFailure();
                return List.of();
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<List<QuestDTO>> findRepeatable() {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tblDefinitions + " WHERE repeatable = " + (isMySQL ? "TRUE" : "1") + " ORDER BY name";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                return executeQuestListQuery(conn, pstmt);

            } catch (SQLException e) {
                logger.error("Failed to find repeatable quests", e);
                fallbackTracker.recordFailure();
                return List.of();
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<List<QuestDTO>> findByPrerequisite(String prerequisiteQuestId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            // Search in JSON prerequisites array
            String sql = isMySQL
                ? "SELECT * FROM " + tblDefinitions + " WHERE JSON_CONTAINS(prerequisites, ?, '$') ORDER BY name"
                : "SELECT * FROM " + tblDefinitions + " WHERE prerequisites LIKE ? ORDER BY name";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, isMySQL ? "\"" + prerequisiteQuestId + "\"" : "%" + prerequisiteQuestId + "%");
                return executeQuestListQuery(conn, pstmt);

            } catch (SQLException e) {
                logger.error("Failed to find quests by prerequisite: " + prerequisiteQuestId, e);
                fallbackTracker.recordFailure();
                return List.of();
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<List<QuestDTO>> search(String searchTerm) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tblDefinitions + " WHERE LOWER(name) LIKE LOWER(?) OR LOWER(description) LIKE LOWER(?) ORDER BY name";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                String pattern = "%" + searchTerm + "%";
                pstmt.setString(1, pattern);
                pstmt.setString(2, pattern);
                return executeQuestListQuery(conn, pstmt);

            } catch (SQLException e) {
                logger.error("Failed to search quests: " + searchTerm, e);
                fallbackTracker.recordFailure();
                return List.of();
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<List<String>> findAllCategories() {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT DISTINCT category FROM " + tblDefinitions + " WHERE category IS NOT NULL ORDER BY category";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                List<String> categories = new ArrayList<>();
                while (rs.next()) {
                    categories.add(rs.getString("category"));
                }
                fallbackTracker.recordSuccess();
                return categories;

            } catch (SQLException e) {
                logger.error("Failed to find all categories", e);
                fallbackTracker.recordFailure();
                return List.of();
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<List<QuestDTO>> findStarterQuests() {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tblDefinitions + " WHERE prerequisites IS NULL OR prerequisites = '[]' ORDER BY name";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                return executeQuestListQuery(conn, pstmt);

            } catch (SQLException e) {
                logger.error("Failed to find starter quests", e);
                fallbackTracker.recordFailure();
                return List.of();
            }
        }, databaseManager.getExecutor());
    }

    // ==================== Objective Operations ====================

    @Override
    public CompletableFuture<Boolean> saveObjectives(String questId, List<ObjectiveDTO> objectives) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                saveObjectivesInternal(conn, questId, objectives);
                fallbackTracker.recordSuccess();
                return true;
            } catch (SQLException e) {
                logger.error("Failed to save objectives for quest: " + questId, e);
                fallbackTracker.recordFailure();
                return false;
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<List<ObjectiveDTO>> findObjectives(String questId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                List<ObjectiveDTO> objectives = findObjectivesInternal(conn, questId);
                fallbackTracker.recordSuccess();
                return objectives;
            } catch (SQLException e) {
                logger.error("Failed to find objectives for quest: " + questId, e);
                fallbackTracker.recordFailure();
                return List.of();
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Boolean> addObjective(String questId, ObjectiveDTO objective) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = isMySQL
                ? "INSERT INTO " + tblObjectives + " (quest_id, objective_id, type, target, required_amount, description, sort_order, metadata) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE type = VALUES(type), target = VALUES(target), required_amount = VALUES(required_amount), " +
                  "description = VALUES(description), sort_order = VALUES(sort_order), metadata = VALUES(metadata)"
                : "INSERT OR REPLACE INTO " + tblObjectives + " (quest_id, objective_id, type, target, required_amount, description, sort_order, metadata, updated_at) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                setObjectiveParams(pstmt, questId, objective);
                pstmt.executeUpdate();
                fallbackTracker.recordSuccess();
                return true;

            } catch (SQLException e) {
                logger.error("Failed to add objective " + objective.objectiveId() + " to quest " + questId, e);
                fallbackTracker.recordFailure();
                return false;
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Boolean> removeObjective(String questId, String objectiveId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + tblObjectives + " WHERE quest_id = ? AND objective_id = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, questId);
                pstmt.setString(2, objectiveId);
                int rows = pstmt.executeUpdate();
                fallbackTracker.recordSuccess();
                return rows > 0;

            } catch (SQLException e) {
                logger.error("Failed to remove objective " + objectiveId + " from quest " + questId, e);
                fallbackTracker.recordFailure();
                return false;
            }
        }, databaseManager.getExecutor());
    }

    // ==================== Reward Operations ====================

    @Override
    public CompletableFuture<Boolean> saveRewards(String questId, List<RewardDTO> rewards) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                saveRewardsInternal(conn, questId, rewards);
                fallbackTracker.recordSuccess();
                return true;
            } catch (SQLException e) {
                logger.error("Failed to save rewards for quest: " + questId, e);
                fallbackTracker.recordFailure();
                return false;
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<List<RewardDTO>> findRewards(String questId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                List<RewardDTO> rewards = findRewardsInternal(conn, questId);
                fallbackTracker.recordSuccess();
                return rewards;
            } catch (SQLException e) {
                logger.error("Failed to find rewards for quest: " + questId, e);
                fallbackTracker.recordFailure();
                return List.of();
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Boolean> addReward(String questId, RewardDTO reward) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = isMySQL
                ? "INSERT INTO " + tblRewards + " (quest_id, reward_id, type, value, amount, description, metadata) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE type = VALUES(type), value = VALUES(value), amount = VALUES(amount), " +
                  "description = VALUES(description), metadata = VALUES(metadata)"
                : "INSERT OR REPLACE INTO " + tblRewards + " (quest_id, reward_id, type, value, amount, description, metadata, updated_at) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now'))";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                setRewardParams(pstmt, questId, reward);
                pstmt.executeUpdate();
                fallbackTracker.recordSuccess();
                return true;

            } catch (SQLException e) {
                logger.error("Failed to add reward " + reward.rewardId() + " to quest " + questId, e);
                fallbackTracker.recordFailure();
                return false;
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Boolean> removeReward(String questId, String rewardId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + tblRewards + " WHERE quest_id = ? AND reward_id = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, questId);
                pstmt.setString(2, rewardId);
                int rows = pstmt.executeUpdate();
                fallbackTracker.recordSuccess();
                return rows > 0;

            } catch (SQLException e) {
                logger.error("Failed to remove reward " + rewardId + " from quest " + questId, e);
                fallbackTracker.recordFailure();
                return false;
            }
        }, databaseManager.getExecutor());
    }

    // ==================== Bulk Operations ====================

    @Override
    public CompletableFuture<Integer> saveAll(List<QuestDTO> quests) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(0);
        }

        return CompletableFuture.supplyAsync(() -> {
            int saved = 0;
            for (QuestDTO quest : quests) {
                try {
                    Boolean result = save(quest).join();
                    if (Boolean.TRUE.equals(result)) {
                        saved++;
                    }
                } catch (Exception e) {
                    logger.error("Failed to save quest in batch: " + quest.questId(), e);
                }
            }
            return saved;
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Integer> deleteByCategory(String category) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(0);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + tblDefinitions + " WHERE category = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, category);
                int rows = pstmt.executeUpdate();
                fallbackTracker.recordSuccess();
                return rows;

            } catch (SQLException e) {
                logger.error("Failed to delete quests by category: " + category, e);
                fallbackTracker.recordFailure();
                return 0;
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Long> count() {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(0L);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM " + tblDefinitions;

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                fallbackTracker.recordSuccess();
                return rs.next() ? rs.getLong(1) : 0L;

            } catch (SQLException e) {
                logger.error("Failed to count quest definitions", e);
                fallbackTracker.recordFailure();
                return 0L;
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Long> countByCategory(String category) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(0L);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM " + tblDefinitions + " WHERE category = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, category);
                try (ResultSet rs = pstmt.executeQuery()) {
                    fallbackTracker.recordSuccess();
                    return rs.next() ? rs.getLong(1) : 0L;
                }

            } catch (SQLException e) {
                logger.error("Failed to count quests by category: " + category, e);
                fallbackTracker.recordFailure();
                return 0L;
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
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> reload() {
        return CompletableFuture.completedFuture(null);
    }

    // ==================== Internal Helper Methods ====================

    private List<QuestDTO> executeQuestListQuery(Connection conn, PreparedStatement pstmt) throws SQLException {
        List<QuestDTO> quests = new ArrayList<>();
        try (ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                QuestDTO quest = mapResultSetToQuest(rs);
                List<ObjectiveDTO> objectives = findObjectivesInternal(conn, quest.questId());
                List<RewardDTO> rewards = findRewardsInternal(conn, quest.questId());
                quests.add(quest.withObjectives(objectives).withRewards(rewards));
            }
        }
        fallbackTracker.recordSuccess();
        return quests;
    }

    private void saveObjectivesInternal(Connection conn, String questId, List<ObjectiveDTO> objectives) throws SQLException {
        // Delete existing objectives
        String deleteSql = "DELETE FROM " + tblObjectives + " WHERE quest_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
            pstmt.setString(1, questId);
            pstmt.executeUpdate();
        }

        if (objectives == null || objectives.isEmpty()) {
            return;
        }

        // Insert new objectives
        String insertSql = "INSERT INTO " + tblObjectives +
            " (quest_id, objective_id, type, target, required_amount, description, sort_order, metadata) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            for (ObjectiveDTO obj : objectives) {
                setObjectiveParams(pstmt, questId, obj);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }

    private void saveRewardsInternal(Connection conn, String questId, List<RewardDTO> rewards) throws SQLException {
        // Delete existing rewards
        String deleteSql = "DELETE FROM " + tblRewards + " WHERE quest_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
            pstmt.setString(1, questId);
            pstmt.executeUpdate();
        }

        if (rewards == null || rewards.isEmpty()) {
            return;
        }

        // Insert new rewards
        String insertSql = "INSERT INTO " + tblRewards +
            " (quest_id, reward_id, type, value, amount, description, metadata) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            for (RewardDTO reward : rewards) {
                setRewardParams(pstmt, questId, reward);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }

    private List<ObjectiveDTO> findObjectivesInternal(Connection conn, String questId) throws SQLException {
        String sql = "SELECT * FROM " + tblObjectives + " WHERE quest_id = ? ORDER BY sort_order";
        List<ObjectiveDTO> objectives = new ArrayList<>();

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, questId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    objectives.add(mapResultSetToObjective(rs));
                }
            }
        }
        return objectives;
    }

    private List<RewardDTO> findRewardsInternal(Connection conn, String questId) throws SQLException {
        String sql = "SELECT * FROM " + tblRewards + " WHERE quest_id = ? ORDER BY reward_id";
        List<RewardDTO> rewards = new ArrayList<>();

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, questId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    rewards.add(mapResultSetToReward(rs));
                }
            }
        }
        return rewards;
    }

    private void setObjectiveParams(PreparedStatement pstmt, String questId, ObjectiveDTO obj) throws SQLException {
        pstmt.setString(1, questId);
        pstmt.setString(2, obj.objectiveId());
        pstmt.setString(3, obj.type().name());
        pstmt.setString(4, obj.target());
        pstmt.setInt(5, obj.requiredAmount());
        pstmt.setString(6, obj.description());
        pstmt.setInt(7, obj.order());
        pstmt.setString(8, obj.metadata().isEmpty() ? null : gson.toJson(obj.metadata()));
    }

    private void setRewardParams(PreparedStatement pstmt, String questId, RewardDTO reward) throws SQLException {
        pstmt.setString(1, questId);
        pstmt.setString(2, reward.rewardId());
        pstmt.setString(3, reward.type().name());
        pstmt.setString(4, reward.value());
        pstmt.setInt(5, reward.amount());
        pstmt.setString(6, reward.description());
        pstmt.setString(7, reward.metadata().isEmpty() ? null : gson.toJson(reward.metadata()));
    }

    private QuestDTO mapResultSetToQuest(ResultSet rs) throws SQLException {
        String questId = rs.getString("quest_id");
        String name = rs.getString("name");
        String description = rs.getString("description");
        String category = rs.getString("category");
        boolean repeatable = rs.getBoolean("repeatable");
        int cooldownMinutes = rs.getInt("cooldown_minutes");

        // Parse prerequisites JSON
        List<String> prerequisites = List.of();
        String prereqJson = rs.getString("prerequisites");
        if (prereqJson != null && !prereqJson.isEmpty()) {
            Type listType = new TypeToken<List<String>>(){}.getType();
            prerequisites = gson.fromJson(prereqJson, listType);
        }

        // Parse metadata JSON
        Map<String, Object> metadata = Map.of();
        String metaJson = rs.getString("metadata");
        if (metaJson != null && !metaJson.isEmpty()) {
            Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
            metadata = gson.fromJson(metaJson, mapType);
        }

        // Parse created_at
        Instant createdAt = Instant.now();
        try {
            String createdAtStr = rs.getString("created_at");
            if (createdAtStr != null) {
                createdAt = Timestamp.valueOf(createdAtStr).toInstant();
            }
        } catch (Exception ignored) {
            // Use default if parsing fails
        }

        return new QuestDTO(questId, name, description, category, repeatable,
                           cooldownMinutes, List.of(), List.of(), prerequisites,
                           createdAt, metadata);
    }

    private ObjectiveDTO mapResultSetToObjective(ResultSet rs) throws SQLException {
        String objectiveId = rs.getString("objective_id");
        ObjectiveType type = ObjectiveType.valueOf(rs.getString("type"));
        String target = rs.getString("target");
        int requiredAmount = rs.getInt("required_amount");
        String description = rs.getString("description");
        int order = rs.getInt("sort_order");

        Map<String, String> metadata = Map.of();
        String metaJson = rs.getString("metadata");
        if (metaJson != null && !metaJson.isEmpty()) {
            Type mapType = new TypeToken<Map<String, String>>(){}.getType();
            metadata = gson.fromJson(metaJson, mapType);
        }

        return new ObjectiveDTO(objectiveId, type, target, requiredAmount, description, order, metadata);
    }

    private RewardDTO mapResultSetToReward(ResultSet rs) throws SQLException {
        String rewardId = rs.getString("reward_id");
        RewardType type = RewardType.valueOf(rs.getString("type"));
        String value = rs.getString("value");
        int amount = rs.getInt("amount");
        String description = rs.getString("description");

        Map<String, String> metadata = Map.of();
        String metaJson = rs.getString("metadata");
        if (metaJson != null && !metaJson.isEmpty()) {
            Type mapType = new TypeToken<Map<String, String>>(){}.getType();
            metadata = gson.fromJson(metaJson, mapType);
        }

        return new RewardDTO(rewardId, type, value, amount, description, metadata);
    }
}
