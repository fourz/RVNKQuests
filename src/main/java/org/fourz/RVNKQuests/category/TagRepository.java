package org.fourz.RVNKQuests.category;

import org.fourz.RVNKQuests.data.DatabaseManager;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Repository for quest tags and tag assignments.
 *
 * <p>Provides async data access operations for managing quest tags and
 * their assignments to quests. Supports both MySQL and SQLite backends.</p>
 *
 * <p>Pattern compliance:</p>
 * <ul>
 *   <li>All operations return CompletableFuture (async-first)</li>
 *   <li>Uses DatabaseManager's executor for thread-safe async operations</li>
 *   <li>Handles MySQL/SQLite differences transparently</li>
 * </ul>
 */
public class TagRepository {

    private final DatabaseManager dbManager;
    private final LogManager logger;
    private final String tblTags;
    private final String tblTagAssignments;

    /**
     * Creates a new TagRepository.
     *
     * @param dbManager The database manager instance
     * @param logger The logger instance
     */
    public TagRepository(DatabaseManager dbManager, LogManager logger) {
        this.dbManager = dbManager;
        this.logger = logger;
        this.tblTags = dbManager.table("quest_tags");
        this.tblTagAssignments = dbManager.table("quest_tag_assignments");
    }

    // ========================================
    // Tag Management
    // ========================================

    /**
     * Creates a new tag.
     *
     * @param tag The tag to create
     * @return CompletableFuture with the created tag (with generated ID)
     */
    public CompletableFuture<Optional<QuestTag>> createTag(QuestTag tag) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = dbManager.isMySQL()
                ? "INSERT INTO " + tblTags + " (name, description, color) VALUES (?, ?, ?)"
                : "INSERT INTO " + tblTags + " (name, description, color) VALUES (?, ?, ?)";

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, tag.name());
                stmt.setString(2, tag.description());
                stmt.setString(3, tag.color());

                int result = stmt.executeUpdate();
                if (result > 0) {
                    logger.info("Created tag: " + tag.name());
                    return Optional.of(tag);
                }
            } catch (SQLException e) {
                logger.error("Failed to create tag " + tag.name() + ": " + e.getMessage());
            }
            return Optional.empty();
        }, dbManager.getExecutor());
    }

    /**
     * Gets a tag by name.
     *
     * @param name The tag name
     * @return CompletableFuture with the tag, or empty if not found
     */
    public CompletableFuture<Optional<QuestTag>> getTag(String name) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT name, description, color FROM " + tblTags + " WHERE name = ?";

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, name);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new QuestTag(
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getString("color")
                        ));
                    }
                }
            } catch (SQLException e) {
                logger.error("Failed to get tag " + name + ": " + e.getMessage());
            }
            return Optional.empty();
        }, dbManager.getExecutor());
    }

    /**
     * Gets all tags.
     *
     * @return CompletableFuture with list of all tags
     */
    public CompletableFuture<List<QuestTag>> getAllTags() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT name, description, color FROM " + tblTags + " ORDER BY name";
            List<QuestTag> tags = new ArrayList<>();

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    tags.add(new QuestTag(
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("color")
                    ));
                }
            } catch (SQLException e) {
                logger.error("Failed to get all tags: " + e.getMessage());
            }
            return tags;
        }, dbManager.getExecutor());
    }

    /**
     * Updates an existing tag.
     *
     * @param tag The tag with updated data
     * @return CompletableFuture with true if update succeeded
     */
    public CompletableFuture<Boolean> updateTag(QuestTag tag) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE " + tblTags + " SET description = ?, color = ? WHERE name = ?";

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, tag.description());
                stmt.setString(2, tag.color());
                stmt.setString(3, tag.name());

                int result = stmt.executeUpdate();
                if (result > 0) {
                    logger.debug("Updated tag: " + tag.name());
                    return true;
                }
            } catch (SQLException e) {
                logger.error("Failed to update tag " + tag.name() + ": " + e.getMessage());
            }
            return false;
        }, dbManager.getExecutor());
    }

    /**
     * Deletes a tag by name.
     *
     * @param name The tag name
     * @return CompletableFuture with true if deletion succeeded
     */
    public CompletableFuture<Boolean> deleteTag(String name) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + tblTags + " WHERE name = ?";

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, name);
                int result = stmt.executeUpdate();
                if (result > 0) {
                    logger.info("Deleted tag: " + name);
                    return true;
                }
            } catch (SQLException e) {
                logger.error("Failed to delete tag " + name + ": " + e.getMessage());
            }
            return false;
        }, dbManager.getExecutor());
    }

    /**
     * Checks if a tag exists.
     *
     * @param name The tag name
     * @return CompletableFuture with true if tag exists
     */
    public CompletableFuture<Boolean> tagExists(String name) {
        return getTag(name).thenApply(Optional::isPresent);
    }

    // ========================================
    // Tag Assignment Management
    // ========================================

    /**
     * Assigns a tag to a quest.
     *
     * @param questId The quest identifier
     * @param tagName The tag name
     * @return CompletableFuture with true if assignment succeeded
     */
    public CompletableFuture<Boolean> assignTagToQuest(String questId, String tagName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = dbManager.isMySQL()
                ? "INSERT IGNORE INTO " + tblTagAssignments + " (quest_id, tag_name) VALUES (?, ?)"
                : "INSERT OR IGNORE INTO " + tblTagAssignments + " (quest_id, tag_name) VALUES (?, ?)";

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, questId);
                stmt.setString(2, tagName);

                int result = stmt.executeUpdate();
                logger.debug("Assigned tag " + tagName + " to quest " + questId);
                return result > 0;

            } catch (SQLException e) {
                logger.error("Failed to assign tag to quest: " + e.getMessage());
                return false;
            }
        }, dbManager.getExecutor());
    }

    /**
     * Removes a tag from a quest.
     *
     * @param questId The quest identifier
     * @param tagName The tag name
     * @return CompletableFuture with true if removal succeeded
     */
    public CompletableFuture<Boolean> removeTagFromQuest(String questId, String tagName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + tblTagAssignments + " WHERE quest_id = ? AND tag_name = ?";

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, questId);
                stmt.setString(2, tagName);

                int result = stmt.executeUpdate();
                logger.debug("Removed tag " + tagName + " from quest " + questId);
                return result > 0;

            } catch (SQLException e) {
                logger.error("Failed to remove tag from quest: " + e.getMessage());
                return false;
            }
        }, dbManager.getExecutor());
    }

    /**
     * Gets all tags assigned to a quest.
     *
     * @param questId The quest identifier
     * @return CompletableFuture with list of tags
     */
    public CompletableFuture<List<QuestTag>> getQuestTags(String questId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT t.name, t.description, t.color " +
                        "FROM " + tblTags + " t " +
                        "INNER JOIN " + tblTagAssignments + " a ON t.name = a.tag_name " +
                        "WHERE a.quest_id = ? " +
                        "ORDER BY t.name";
            List<QuestTag> tags = new ArrayList<>();

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, questId);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        tags.add(new QuestTag(
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getString("color")
                        ));
                    }
                }
            } catch (SQLException e) {
                logger.error("Failed to get tags for quest " + questId + ": " + e.getMessage());
            }
            return tags;
        }, dbManager.getExecutor());
    }

    /**
     * Gets all quests with a specific tag.
     *
     * @param tagName The tag name
     * @return CompletableFuture with list of quest IDs
     */
    public CompletableFuture<List<String>> getQuestsByTag(String tagName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT quest_id FROM " + tblTagAssignments + " WHERE tag_name = ?";
            List<String> questIds = new ArrayList<>();

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, tagName);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        questIds.add(rs.getString("quest_id"));
                    }
                }
            } catch (SQLException e) {
                logger.error("Failed to get quests by tag " + tagName + ": " + e.getMessage());
            }
            return questIds;
        }, dbManager.getExecutor());
    }

    /**
     * Removes all tag assignments from a quest.
     *
     * @param questId The quest identifier
     * @return CompletableFuture with number of tags removed
     */
    public CompletableFuture<Integer> removeAllTagsFromQuest(String questId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + tblTagAssignments + " WHERE quest_id = ?";

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, questId);
                int result = stmt.executeUpdate();
                logger.debug("Removed " + result + " tags from quest " + questId);
                return result;

            } catch (SQLException e) {
                logger.error("Failed to remove all tags from quest: " + e.getMessage());
                return 0;
            }
        }, dbManager.getExecutor());
    }

    /**
     * Checks if a tag is assigned to a quest.
     *
     * @param questId The quest identifier
     * @param tagName The tag name
     * @return CompletableFuture with true if assigned
     */
    public CompletableFuture<Boolean> isTagAssignedToQuest(String questId, String tagName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM " + tblTagAssignments + " WHERE quest_id = ? AND tag_name = ?";

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, questId);
                stmt.setString(2, tagName);

                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                logger.error("Failed to check tag assignment: " + e.getMessage());
                return false;
            }
        }, dbManager.getExecutor());
    }

    /**
     * Gets tag usage statistics (tag name -> count of quests).
     *
     * @return CompletableFuture with tag usage distribution
     */
    public CompletableFuture<java.util.Map<String, Integer>> getTagUsageStatistics() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT tag_name, COUNT(*) as count FROM " + tblTagAssignments + " GROUP BY tag_name";
            java.util.Map<String, Integer> stats = new java.util.HashMap<>();

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    stats.put(rs.getString("tag_name"), rs.getInt("count"));
                }
            } catch (SQLException e) {
                logger.error("Failed to get tag usage statistics: " + e.getMessage());
            }
            return stats;
        }, dbManager.getExecutor());
    }
}
