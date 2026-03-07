package org.fourz.RVNKQuests.data;

import org.fourz.rvnkcore.testing.TestDataGenerator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

/**
 * Test data generator for RVNKQuests plugin.
 *
 * <p>Seeds 8 core tables with deterministic test data:
 * <ul>
 *   <li>quest_categories - 5 categories (main, side, daily, weekly, events)</li>
 *   <li>quest_tags - 10 tags (combat, mining, building, etc.)</li>
 *   <li>quest_progress - N player-quest records</li>
 *   <li>quest_objective_progress - 3x objectives per quest</li>
 *   <li>quest_rewards_claimed - 50% of completed quests</li>
 *   <li>quest_journal_entries - 5x entries per quest</li>
 *   <li>quest_tag_assignments - Random tag assignments</li>
 *   <li>quest_leaderboard_entries - Top N players</li>
 * </ul>
 * </p>
 */
public class QuestsTestDataGenerator extends TestDataGenerator {

    // Category definitions
    private static final String[][] CATEGORIES = {
        {"main", "Main Quests", "&6", "BOOK", "Primary storyline quests"},
        {"side", "Side Quests", "&e", "PAPER", "Optional side adventures"},
        {"daily", "Daily Quests", "&a", "CLOCK", "Reset every 24 hours"},
        {"weekly", "Weekly Quests", "&b", "COMPASS", "Reset every week"},
        {"events", "Event Quests", "&d", "FIREWORK_ROCKET", "Limited-time special events"}
    };

    // Tag definitions
    private static final String[][] TAGS = {
        {"combat", "Combat", "&c"},
        {"mining", "Mining", "&7"},
        {"building", "Building", "&e"},
        {"exploration", "Exploration", "&2"},
        {"gathering", "Gathering", "&a"},
        {"fishing", "Fishing", "&9"},
        {"farming", "Farming", "&6"},
        {"trading", "Trading", "&d"},
        {"crafting", "Crafting", "&f"},
        {"social", "Social", "&b"}
    };

    // Quest states
    private static final String[] QUEST_STATES = {
        "NOT_STARTED", "TRIGGER_FOUND", "QUEST_ACTIVE", "OBJECTIVE_FOUND", "COMPLETED"
    };

    // Journal actions
    private static final String[] JOURNAL_ACTIONS = {
        "QUEST_STARTED", "OBJECTIVE_PROGRESS", "OBJECTIVE_COMPLETE",
        "QUEST_COMPLETE", "REWARD_CLAIMED"
    };

    // Leaderboard types
    private static final String[] LEADERBOARD_TYPES = {
        "quests_completed", "objectives_completed", "rewards_claimed",
        "total_progress", "daily_completions"
    };

    private final DatabaseManager databaseManager;
    private final ExecutorService executor;
    private final String tablePrefix;

    /**
     * Create a new QuestsTestDataGenerator.
     *
     * @param databaseManager the database manager instance
     */
    public QuestsTestDataGenerator(DatabaseManager databaseManager) {
        super(
            Logger.getLogger("RVNKQuests"),
            databaseManager::isMySQL,
            () -> {
                try {
                    return databaseManager.getConnection();
                } catch (SQLException e) {
                    throw new RuntimeException("Failed to get connection", e);
                }
            }
        );
        this.databaseManager = databaseManager;
        this.executor = databaseManager.getExecutor();
        this.tablePrefix = databaseManager.getTablePrefix();
    }

    /**
     * Get prefixed table name.
     */
    private String table(String baseName) {
        return databaseManager.table(baseName);
    }

    @Override
    public String getGeneratorName() {
        return "QuestsTestDataGenerator";
    }

    @Override
    public CompletableFuture<Integer> seed(DataCategory category) {
        return CompletableFuture.supplyAsync(() -> {
            logInfo("Seeding " + category.name() + " data...");
            int totalRecords = 0;

            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);

                try {
                    // 1. Seed categories
                    totalRecords += seedCategories(conn);

                    // 2. Seed tags
                    totalRecords += seedTags(conn);

                    // 3. Seed quest progress
                    int progressCount = seedQuestProgress(conn, category.getBaseCount());
                    totalRecords += progressCount;

                    // 4. Seed objective progress (3x per quest)
                    totalRecords += seedObjectiveProgress(conn, category.getBaseCount());

                    // 5. Seed rewards claimed (50% of completed)
                    totalRecords += seedRewardsClaimed(conn, category.getBaseCount());

                    // 6. Seed journal entries (5x per quest)
                    totalRecords += seedJournalEntries(conn, category.getBaseCount());

                    // 7. Seed tag assignments
                    totalRecords += seedTagAssignments(conn, category.getBaseCount());

                    // 8. Seed leaderboard entries
                    totalRecords += seedLeaderboardEntries(conn, category.getBaseCount());

                    conn.commit();
                    logInfo("Seed complete: " + totalRecords + " total records");

                } catch (SQLException e) {
                    conn.rollback();
                    logSevere("Seed failed, rolling back: " + e.getMessage());
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }

            } catch (SQLException e) {
                logSevere("Failed to seed data: " + e.getMessage());
                return 0;
            }

            return totalRecords;
        }, executor);
    }

    private int seedCategories(Connection conn) throws SQLException {
        String sql = "INSERT OR IGNORE INTO " + table("quest_categories") +
            " (name, display_name, color_code, icon, description, sort_order) VALUES (?, ?, ?, ?, ?, ?)";

        if (isMySQL()) {
            sql = "INSERT IGNORE INTO " + table("quest_categories") +
                " (name, display_name, color_code, icon, description, sort_order) VALUES (?, ?, ?, ?, ?, ?)";
        }

        int count = 0;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < CATEGORIES.length; i++) {
                stmt.setString(1, CATEGORIES[i][0]);
                stmt.setString(2, CATEGORIES[i][1]);
                stmt.setString(3, CATEGORIES[i][2]);
                stmt.setString(4, CATEGORIES[i][3]);
                stmt.setString(5, CATEGORIES[i][4]);
                stmt.setInt(6, i * 10);
                stmt.addBatch();
                count++;
            }
            stmt.executeBatch();
        }
        logSeeded("quest_categories", count);
        return count;
    }

    private int seedTags(Connection conn) throws SQLException {
        String sql = "INSERT OR IGNORE INTO " + table("quest_tags") +
            " (name, display_name, color_code) VALUES (?, ?, ?)";

        if (isMySQL()) {
            sql = "INSERT IGNORE INTO " + table("quest_tags") +
                " (name, display_name, color_code) VALUES (?, ?, ?)";
        }

        int count = 0;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (String[] tag : TAGS) {
                stmt.setString(1, tag[0]);
                stmt.setString(2, tag[1]);
                stmt.setString(3, tag[2]);
                stmt.addBatch();
                count++;
            }
            stmt.executeBatch();
        }
        logSeeded("quest_tags", count);
        return count;
    }

    private int seedQuestProgress(Connection conn, int count) throws SQLException {
        String sql = "INSERT OR REPLACE INTO " + table("quest_progress") +
            " (player_uuid, quest_id, state, path_choice, started_at, completed_at, metadata) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

        if (isMySQL()) {
            sql = "REPLACE INTO " + table("quest_progress") +
                " (player_uuid, quest_id, state, path_choice, started_at, completed_at, metadata) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        }

        int inserted = 0;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < count; i++) {
                UUID playerUuid = testUUID(i);
                String questId = "test_quest_" + (i % 20);  // 20 unique quests
                String state = QUEST_STATES[i % QUEST_STATES.length];
                String pathChoice = (i % 3 == 0) ? "path_a" : (i % 3 == 1) ? "path_b" : null;
                String startedAt = testTimestamp(randomInt(1, 30)).toString();
                String completedAt = state.equals("COMPLETED") ? testTimestamp(0).toString() : null;
                String metadata = "{\"test\": true, \"seed\": " + i + "}";

                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, questId);
                stmt.setString(3, state);
                stmt.setString(4, pathChoice);
                stmt.setString(5, startedAt);
                stmt.setString(6, completedAt);
                stmt.setString(7, metadata);
                stmt.addBatch();
                inserted++;

                if (inserted % 100 == 0) {
                    stmt.executeBatch();
                }
            }
            stmt.executeBatch();
        }
        logSeeded("quest_progress", inserted);
        return inserted;
    }

    private int seedObjectiveProgress(Connection conn, int count) throws SQLException {
        String sql = "INSERT OR REPLACE INTO " + table("quest_objective_progress") +
            " (player_uuid, quest_id, objective_id, progress_count, target_count, is_completed, completed_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

        if (isMySQL()) {
            sql = "REPLACE INTO " + table("quest_objective_progress") +
                " (player_uuid, quest_id, objective_id, progress_count, target_count, is_completed, completed_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        }

        int inserted = 0;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < count; i++) {
                UUID playerUuid = testUUID(i);
                String questId = "test_quest_" + (i % 20);

                // 3 objectives per quest
                for (int j = 1; j <= 3; j++) {
                    String objectiveId = "obj_" + j;
                    int targetCount = 10 * j;
                    int progressCount = randomInt(0, targetCount + 1);
                    boolean isCompleted = progressCount >= targetCount;
                    String completedAt = isCompleted ? testTimestamp(0).toString() : null;

                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, questId);
                    stmt.setString(3, objectiveId);
                    stmt.setInt(4, progressCount);
                    stmt.setInt(5, targetCount);
                    stmt.setInt(6, isCompleted ? 1 : 0);
                    stmt.setString(7, completedAt);
                    stmt.addBatch();
                    inserted++;

                    if (inserted % 100 == 0) {
                        stmt.executeBatch();
                    }
                }
            }
            stmt.executeBatch();
        }
        logSeeded("quest_objective_progress", inserted);
        return inserted;
    }

    private int seedRewardsClaimed(Connection conn, int count) throws SQLException {
        String sql = "INSERT OR IGNORE INTO " + table("quest_rewards_claimed") +
            " (player_uuid, quest_id, reward_id) VALUES (?, ?, ?)";

        if (isMySQL()) {
            sql = "INSERT IGNORE INTO " + table("quest_rewards_claimed") +
                " (player_uuid, quest_id, reward_id) VALUES (?, ?, ?)";
        }

        int inserted = 0;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // Only 50% of players claim rewards
            for (int i = 0; i < count / 2; i++) {
                UUID playerUuid = testUUID(i * 2);  // Even numbered players
                String questId = "test_quest_" + (i % 20);
                String rewardId = "reward_" + (i % 5 + 1);

                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, questId);
                stmt.setString(3, rewardId);
                stmt.addBatch();
                inserted++;

                if (inserted % 100 == 0) {
                    stmt.executeBatch();
                }
            }
            stmt.executeBatch();
        }
        logSeeded("quest_rewards_claimed", inserted);
        return inserted;
    }

    private int seedJournalEntries(Connection conn, int count) throws SQLException {
        String sql = "INSERT INTO " + table("quest_journal_entries") +
            " (player_uuid, quest_id, action, timestamp, details) VALUES (?, ?, ?, ?, ?)";

        int inserted = 0;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < count; i++) {
                UUID playerUuid = testUUID(i);
                String questId = "test_quest_" + (i % 20);

                // 5 journal entries per player-quest
                for (int j = 0; j < 5; j++) {
                    String action = JOURNAL_ACTIONS[j % JOURNAL_ACTIONS.length];
                    long timestamp = testTimestamp(30 - j).getTime();
                    String details = "{\"entry\": " + j + ", \"action\": \"" + action + "\"}";

                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, questId);
                    stmt.setString(3, action);
                    stmt.setLong(4, timestamp);
                    stmt.setString(5, details);
                    stmt.addBatch();
                    inserted++;

                    if (inserted % 100 == 0) {
                        stmt.executeBatch();
                    }
                }
            }
            stmt.executeBatch();
        }
        logSeeded("quest_journal_entries", inserted);
        return inserted;
    }

    private int seedTagAssignments(Connection conn, int count) throws SQLException {
        String sql = "INSERT OR IGNORE INTO " + table("quest_tag_assignments") +
            " (quest_id, tag_id) VALUES (?, ?)";

        if (isMySQL()) {
            sql = "INSERT IGNORE INTO " + table("quest_tag_assignments") +
                " (quest_id, tag_id) VALUES (?, ?)";
        }

        int inserted = 0;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // Assign 2-3 tags per quest
            for (int questNum = 0; questNum < 20; questNum++) {
                String questId = "test_quest_" + questNum;
                int numTags = 2 + (questNum % 2);  // 2 or 3 tags

                for (int t = 0; t < numTags; t++) {
                    int tagId = ((questNum + t) % TAGS.length) + 1;  // Tag IDs start at 1

                    stmt.setString(1, questId);
                    stmt.setInt(2, tagId);
                    stmt.addBatch();
                    inserted++;
                }
            }
            stmt.executeBatch();
        }
        logSeeded("quest_tag_assignments", inserted);
        return inserted;
    }

    private int seedLeaderboardEntries(Connection conn, int count) throws SQLException {
        String sql = "INSERT OR REPLACE INTO " + table("quest_leaderboard_entries") +
            " (player_uuid, player_name, leaderboard_type, value, rank, metadata) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

        if (isMySQL()) {
            sql = "REPLACE INTO " + table("quest_leaderboard_entries") +
                " (player_uuid, player_name, leaderboard_type, value, rank, metadata) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        }

        int inserted = 0;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // Top 10 players per leaderboard type
            int topN = Math.min(10, count);

            for (String lbType : LEADERBOARD_TYPES) {
                for (int rank = 1; rank <= topN; rank++) {
                    UUID playerUuid = testUUID(rank - 1);
                    String playerName = testPlayerName(rank - 1);
                    int value = (topN - rank + 1) * 100 + randomInt(0, 50);
                    String metadata = "{\"leaderboard\": \"" + lbType + "\"}";

                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, playerName);
                    stmt.setString(3, lbType);
                    stmt.setInt(4, value);
                    stmt.setInt(5, rank);
                    stmt.setString(6, metadata);
                    stmt.addBatch();
                    inserted++;
                }
            }
            stmt.executeBatch();
        }
        logSeeded("quest_leaderboard_entries", inserted);
        return inserted;
    }

    @Override
    public CompletableFuture<Boolean> cleanup() {
        return CompletableFuture.supplyAsync(() -> {
            logInfo("Cleaning up all test data...");

            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);

                try {
                    // Disable FK checks for cleanup
                    try (PreparedStatement stmt = conn.prepareStatement(disableForeignKeyChecks())) {
                        stmt.execute();
                    }

                    // Delete in reverse FK order
                    String[] tables = {
                        "quest_leaderboard_entries",
                        "quest_journal_entries",
                        "quest_rewards_claimed",
                        "quest_objective_progress",
                        "quest_progress",
                        "quest_tag_assignments",
                        "quest_tags",
                        "quest_categories"
                    };

                    for (String tableName : tables) {
                        String sql = "DELETE FROM " + table(tableName) +
                            " WHERE " + getTestDataCondition(tableName);
                        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                            int deleted = stmt.executeUpdate();
                            logInfo("Deleted " + deleted + " records from " + tableName);
                        }
                    }

                    // Re-enable FK checks
                    try (PreparedStatement stmt = conn.prepareStatement(enableForeignKeyChecks())) {
                        stmt.execute();
                    }

                    conn.commit();
                    logInfo("Cleanup complete");
                    return true;

                } catch (SQLException e) {
                    conn.rollback();
                    logSevere("Cleanup failed: " + e.getMessage());
                    return false;
                } finally {
                    conn.setAutoCommit(true);
                }

            } catch (SQLException e) {
                logSevere("Failed to cleanup: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    /**
     * Get WHERE condition to identify test data in each table.
     */
    private String getTestDataCondition(String tableName) {
        return switch (tableName) {
            case "quest_categories" -> "name LIKE 'main' OR name LIKE 'side' OR name LIKE 'daily' OR name LIKE 'weekly' OR name LIKE 'events'";
            case "quest_tags" -> "name IN ('combat','mining','building','exploration','gathering','fishing','farming','trading','crafting','social')";
            case "quest_progress", "quest_objective_progress", "quest_rewards_claimed",
                 "quest_journal_entries" -> "quest_id LIKE 'test_quest_%'";
            case "quest_leaderboard_entries" -> "player_name LIKE 'TestPlayer%'";
            case "quest_tag_assignments" -> "quest_id LIKE 'test_quest_%'";
            default -> "1=0";  // Safe fallback - deletes nothing
        };
    }

    @Override
    public CompletableFuture<Integer> cleanupByPlayer(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            logInfo("Cleaning up data for player: " + playerUuid);
            int totalDeleted = 0;

            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);

                try {
                    String[] tables = {
                        "quest_leaderboard_entries",
                        "quest_journal_entries",
                        "quest_rewards_claimed",
                        "quest_objective_progress",
                        "quest_progress"
                    };

                    for (String tableName : tables) {
                        String sql = "DELETE FROM " + table(tableName) + " WHERE player_uuid = ?";
                        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                            stmt.setString(1, playerUuid.toString());
                            int deleted = stmt.executeUpdate();
                            totalDeleted += deleted;
                            logInfo("Deleted " + deleted + " records from " + tableName);
                        }
                    }

                    conn.commit();
                    logInfo("Player cleanup complete: " + totalDeleted + " records");

                } catch (SQLException e) {
                    conn.rollback();
                    logSevere("Player cleanup failed: " + e.getMessage());
                    return 0;
                } finally {
                    conn.setAutoCommit(true);
                }

            } catch (SQLException e) {
                logSevere("Failed to cleanup player data: " + e.getMessage());
                return 0;
            }

            return totalDeleted;
        }, executor);
    }
}
