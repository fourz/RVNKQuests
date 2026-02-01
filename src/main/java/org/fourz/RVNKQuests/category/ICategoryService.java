package org.fourz.RVNKQuests.category;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for quest category and tag management.
 *
 * <p>Provides operations for:</p>
 * <ul>
 *   <li>Category assignment and retrieval</li>
 *   <li>Tag creation and management</li>
 *   <li>Quest filtering by category and tags</li>
 *   <li>Statistics and distribution analysis</li>
 * </ul>
 *
 * <p>Pattern compliance:</p>
 * <ul>
 *   <li>Interface uses "I" prefix per RVNK coding standards</li>
 *   <li>All I/O operations return CompletableFuture (async-first)</li>
 *   <li>Thread-safe for concurrent access</li>
 * </ul>
 */
public interface ICategoryService {

    // ========================================
    // Category Operations
    // ========================================

    /**
     * Assigns a category to a quest.
     *
     * @param questId The quest identifier
     * @param category The category to assign
     * @return CompletableFuture with true if assignment succeeded
     */
    CompletableFuture<Boolean> categorizeQuest(String questId, QuestCategory category);

    /**
     * Gets the category assigned to a quest.
     *
     * @param questId The quest identifier
     * @return CompletableFuture with the category, or null if not assigned
     */
    CompletableFuture<QuestCategory> getQuestCategory(String questId);

    /**
     * Gets all quests with a specific category.
     *
     * @param category The category to filter by
     * @return CompletableFuture with list of quest IDs
     */
    CompletableFuture<List<String>> filterByCategory(QuestCategory category);

    /**
     * Removes category assignment from a quest.
     *
     * @param questId The quest identifier
     * @return CompletableFuture with true if removal succeeded
     */
    CompletableFuture<Boolean> removeCategory(String questId);

    /**
     * Gets category distribution (category -> quest count).
     *
     * @return CompletableFuture with category distribution
     */
    CompletableFuture<Map<QuestCategory, Integer>> getCategoryDistribution();

    // ========================================
    // Tag Operations
    // ========================================

    /**
     * Creates a new tag.
     *
     * @param tag The tag to create
     * @return CompletableFuture with true if creation succeeded
     */
    CompletableFuture<Boolean> createTag(QuestTag tag);

    /**
     * Gets a tag by name.
     *
     * @param name The tag name
     * @return CompletableFuture with the tag, or null if not found
     */
    CompletableFuture<QuestTag> getTag(String name);

    /**
     * Gets all available tags.
     *
     * @return CompletableFuture with list of all tags
     */
    CompletableFuture<List<QuestTag>> getAllTags();

    /**
     * Updates an existing tag.
     *
     * @param tag The tag with updated data
     * @return CompletableFuture with true if update succeeded
     */
    CompletableFuture<Boolean> updateTag(QuestTag tag);

    /**
     * Deletes a tag.
     *
     * @param name The tag name
     * @return CompletableFuture with true if deletion succeeded
     */
    CompletableFuture<Boolean> deleteTag(String name);

    // ========================================
    // Tag Assignment Operations
    // ========================================

    /**
     * Assigns a tag to a quest.
     *
     * @param questId The quest identifier
     * @param tagName The tag name
     * @return CompletableFuture with true if assignment succeeded
     */
    CompletableFuture<Boolean> addTag(String questId, String tagName);

    /**
     * Removes a tag from a quest.
     *
     * @param questId The quest identifier
     * @param tagName The tag name
     * @return CompletableFuture with true if removal succeeded
     */
    CompletableFuture<Boolean> removeTag(String questId, String tagName);

    /**
     * Gets all tags assigned to a quest.
     *
     * @param questId The quest identifier
     * @return CompletableFuture with list of tags
     */
    CompletableFuture<List<QuestTag>> getQuestTags(String questId);

    /**
     * Gets all quests with a specific tag.
     *
     * @param tagName The tag name
     * @return CompletableFuture with list of quest IDs
     */
    CompletableFuture<List<String>> filterByTag(String tagName);

    /**
     * Removes all tags from a quest.
     *
     * @param questId The quest identifier
     * @return CompletableFuture with number of tags removed
     */
    CompletableFuture<Integer> removeAllTags(String questId);

    // ========================================
    // Combined Filtering
    // ========================================

    /**
     * Filters quests by both category and tags.
     *
     * @param category The category to filter by (null to ignore)
     * @param tags The tags to filter by (empty list to ignore)
     * @return CompletableFuture with list of quest IDs matching criteria
     */
    CompletableFuture<List<String>> filterQuests(QuestCategory category, List<String> tags);

    // ========================================
    // Statistics
    // ========================================

    /**
     * Gets tag usage statistics (tag name -> count of quests).
     *
     * @return CompletableFuture with tag usage distribution
     */
    CompletableFuture<Map<String, Integer>> getTagUsageStatistics();

    /**
     * Initializes predefined tags if they don't exist.
     *
     * @return CompletableFuture that completes when initialization is done
     */
    CompletableFuture<Void> initializePredefinedTags();
}
