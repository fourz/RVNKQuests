package org.fourz.RVNKQuests.category;

import org.fourz.RVNKQuests.data.DatabaseManager;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Repository for quest category assignments.
 *
 * <p>Provides async data access operations for managing which categories
 * are assigned to which quests. Supports both MySQL and SQLite backends.</p>
 *
 * <p>Pattern compliance:</p>
 * <ul>
 *   <li>All operations return CompletableFuture (async-first)</li>
 *   <li>Uses DatabaseManager's executor for thread-safe async operations</li>
 *   <li>Handles MySQL/SQLite differences transparently</li>
 * </ul>
 */
public class CategoryRepository {

    private final DatabaseManager dbManager;
    private final LogManager logger;
    private final String tblCategories;

    /**
     * Creates a new CategoryRepository.
     *
     * @param dbManager The database manager instance
     * @param logger The logger instance
     */
    public CategoryRepository(DatabaseManager dbManager, LogManager logger) {
        this.dbManager = dbManager;
        this.logger = logger;
        this.tblCategories = dbManager.table("quest_categories");
    }

    /**
     * Assigns a category to a quest.
     *
     * @param questId The quest identifier
     * @param category The category to assign
     * @return CompletableFuture with true if assignment succeeded
     */
    public CompletableFuture<Boolean> setCategoryForQuest(String questId, QuestCategory category) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = dbManager.isMySQL()
                ? "INSERT INTO " + tblCategories + " (quest_id, category) VALUES (?, ?) ON DUPLICATE KEY UPDATE category = ?"
                : "INSERT OR REPLACE INTO " + tblCategories + " (quest_id, category) VALUES (?, ?)";

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, questId);
                stmt.setString(2, category.name());

                if (dbManager.isMySQL()) {
                    stmt.setString(3, category.name());
                }

                int result = stmt.executeUpdate();
                logger.debug("Assigned category " + category + " to quest " + questId);
                return result > 0;

            } catch (SQLException e) {
                logger.error("Failed to set category for quest " + questId + ": " + e.getMessage());
                return false;
            }
        }, dbManager.getExecutor());
    }

    /**
     * Gets the category assigned to a quest.
     *
     * @param questId The quest identifier
     * @return CompletableFuture with the category, or null if not assigned
     */
    public CompletableFuture<QuestCategory> getCategoryForQuest(String questId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT category FROM " + tblCategories + " WHERE quest_id = ?";

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, questId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String categoryName = rs.getString("category");
                        try {
                            return QuestCategory.valueOf(categoryName);
                        } catch (IllegalArgumentException e) {
                            logger.warning("Invalid category in database: " + categoryName);
                            return null;
                        }
                    }
                }
            } catch (SQLException e) {
                logger.error("Failed to get category for quest " + questId + ": " + e.getMessage());
            }
            return null;
        }, dbManager.getExecutor());
    }

    /**
     * Gets all quests with a specific category.
     *
     * @param category The category to filter by
     * @return CompletableFuture with list of quest IDs
     */
    public CompletableFuture<List<String>> getQuestsByCategory(QuestCategory category) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT quest_id FROM " + tblCategories + " WHERE category = ?";
            List<String> questIds = new ArrayList<>();

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, category.name());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        questIds.add(rs.getString("quest_id"));
                    }
                }
            } catch (SQLException e) {
                logger.error("Failed to get quests by category " + category + ": " + e.getMessage());
            }
            return questIds;
        }, dbManager.getExecutor());
    }

    /**
     * Removes the category assignment from a quest.
     *
     * @param questId The quest identifier
     * @return CompletableFuture with true if removal succeeded
     */
    public CompletableFuture<Boolean> removeCategoryFromQuest(String questId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + tblCategories + " WHERE quest_id = ?";

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, questId);
                int result = stmt.executeUpdate();
                logger.debug("Removed category from quest " + questId);
                return result > 0;

            } catch (SQLException e) {
                logger.error("Failed to remove category from quest " + questId + ": " + e.getMessage());
                return false;
            }
        }, dbManager.getExecutor());
    }

    /**
     * Checks if a quest has a category assigned.
     *
     * @param questId The quest identifier
     * @return CompletableFuture with true if category is assigned
     */
    public CompletableFuture<Boolean> hasCategory(String questId) {
        return getCategoryForQuest(questId).thenApply(category -> category != null);
    }

    /**
     * Gets count of quests in each category.
     *
     * @return CompletableFuture with category distribution (category name -> count)
     */
    public CompletableFuture<java.util.Map<QuestCategory, Integer>> getCategoryDistribution() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT category, COUNT(*) as count FROM " + tblCategories + " GROUP BY category";
            java.util.Map<QuestCategory, Integer> distribution = new java.util.HashMap<>();

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    String categoryName = rs.getString("category");
                    int count = rs.getInt("count");
                    try {
                        QuestCategory category = QuestCategory.valueOf(categoryName);
                        distribution.put(category, count);
                    } catch (IllegalArgumentException e) {
                        logger.warning("Invalid category in database: " + categoryName);
                    }
                }
            } catch (SQLException e) {
                logger.error("Failed to get category distribution: " + e.getMessage());
            }
            return distribution;
        }, dbManager.getExecutor());
    }

    /**
     * Removes all category assignments (used for cleanup/testing).
     *
     * @return CompletableFuture with number of assignments removed
     */
    public CompletableFuture<Integer> removeAllCategories() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + tblCategories;

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                int result = stmt.executeUpdate();
                logger.info("Removed all category assignments: " + result);
                return result;

            } catch (SQLException e) {
                logger.error("Failed to remove all categories: " + e.getMessage());
                return 0;
            }
        }, dbManager.getExecutor());
    }
}
