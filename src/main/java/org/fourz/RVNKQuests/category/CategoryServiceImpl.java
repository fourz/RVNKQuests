package org.fourz.RVNKQuests.category;

import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.DatabaseManager;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Implementation of ICategoryService for managing quest categories and tags.
 *
 * <p>Provides operations for:</p>
 * <ul>
 *   <li>Category assignment and retrieval</li>
 *   <li>Tag creation, management, and assignment</li>
 *   <li>Quest filtering by category and tags</li>
 *   <li>Statistics and distribution analysis</li>
 * </ul>
 *
 * <p>Pattern compliance:</p>
 * <ul>
 *   <li>Async-first with CompletableFuture returns</li>
 *   <li>Delegates persistence to CategoryRepository and TagRepository</li>
 *   <li>Thread-safe for concurrent access</li>
 *   <li>Registers with ServiceRegistry for cross-plugin access</li>
 * </ul>
 */
public class CategoryServiceImpl implements ICategoryService {

    private final RVNKQuests plugin;
    private final LogManager logger;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;

    /**
     * Creates a new CategoryServiceImpl.
     *
     * @param plugin The plugin instance
     * @param dbManager The database manager instance
     */
    public CategoryServiceImpl(RVNKQuests plugin, DatabaseManager dbManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "CategoryService");
        this.categoryRepository = new CategoryRepository(dbManager, logger);
        this.tagRepository = new TagRepository(dbManager, logger);
        logger.info("CategoryService initialized");
    }

    // ========================================
    // Category Operations
    // ========================================

    @Override
    public CompletableFuture<Boolean> categorizeQuest(String questId, QuestCategory category) {
        logger.debug("Categorizing quest " + questId + " as " + category);
        return categoryRepository.setCategoryForQuest(questId, category);
    }

    @Override
    public CompletableFuture<QuestCategory> getQuestCategory(String questId) {
        return categoryRepository.getCategoryForQuest(questId);
    }

    @Override
    public CompletableFuture<List<String>> filterByCategory(QuestCategory category) {
        logger.debug("Filtering quests by category: " + category);
        return categoryRepository.getQuestsByCategory(category);
    }

    @Override
    public CompletableFuture<Boolean> removeCategory(String questId) {
        logger.debug("Removing category from quest: " + questId);
        return categoryRepository.removeCategoryFromQuest(questId);
    }

    @Override
    public CompletableFuture<Map<QuestCategory, Integer>> getCategoryDistribution() {
        return categoryRepository.getCategoryDistribution();
    }

    // ========================================
    // Tag Operations
    // ========================================

    @Override
    public CompletableFuture<Boolean> createTag(QuestTag tag) {
        logger.debug("Creating tag: " + tag.name());
        return tagRepository.createTag(tag)
            .thenApply(Optional::isPresent);
    }

    @Override
    public CompletableFuture<QuestTag> getTag(String name) {
        return tagRepository.getTag(name)
            .thenApply(opt -> opt.orElse(null));
    }

    @Override
    public CompletableFuture<List<QuestTag>> getAllTags() {
        return tagRepository.getAllTags();
    }

    @Override
    public CompletableFuture<Boolean> updateTag(QuestTag tag) {
        logger.debug("Updating tag: " + tag.name());
        return tagRepository.updateTag(tag);
    }

    @Override
    public CompletableFuture<Boolean> deleteTag(String name) {
        logger.info("Deleting tag: " + name);
        return tagRepository.deleteTag(name);
    }

    // ========================================
    // Tag Assignment Operations
    // ========================================

    @Override
    public CompletableFuture<Boolean> addTag(String questId, String tagName) {
        logger.debug("Adding tag " + tagName + " to quest " + questId);

        // Verify tag exists first
        return tagRepository.tagExists(tagName)
            .thenCompose(exists -> {
                if (!exists) {
                    logger.warning("Attempted to assign non-existent tag: " + tagName);
                    return CompletableFuture.completedFuture(false);
                }
                return tagRepository.assignTagToQuest(questId, tagName);
            });
    }

    @Override
    public CompletableFuture<Boolean> removeTag(String questId, String tagName) {
        logger.debug("Removing tag " + tagName + " from quest " + questId);
        return tagRepository.removeTagFromQuest(questId, tagName);
    }

    @Override
    public CompletableFuture<List<QuestTag>> getQuestTags(String questId) {
        return tagRepository.getQuestTags(questId);
    }

    @Override
    public CompletableFuture<List<String>> filterByTag(String tagName) {
        logger.debug("Filtering quests by tag: " + tagName);
        return tagRepository.getQuestsByTag(tagName);
    }

    @Override
    public CompletableFuture<Integer> removeAllTags(String questId) {
        logger.debug("Removing all tags from quest: " + questId);
        return tagRepository.removeAllTagsFromQuest(questId);
    }

    // ========================================
    // Combined Filtering
    // ========================================

    @Override
    public CompletableFuture<List<String>> filterQuests(QuestCategory category, List<String> tags) {
        logger.debug("Filtering quests - category: " + category + ", tags: " + tags);

        // Start with all quest IDs
        CompletableFuture<Set<String>> resultFuture;

        if (category != null) {
            // Filter by category first
            resultFuture = categoryRepository.getQuestsByCategory(category)
                .thenApply(HashSet::new);
        } else {
            // No category filter - start with empty set (will be populated by tags)
            resultFuture = CompletableFuture.completedFuture(new HashSet<>());
        }

        // Apply tag filters (AND logic - quest must have all specified tags)
        if (tags != null && !tags.isEmpty()) {
            for (String tag : tags) {
                resultFuture = resultFuture.thenCompose(currentSet ->
                    tagRepository.getQuestsByTag(tag)
                        .thenApply(questsWithTag -> {
                            if (category == null && currentSet.isEmpty()) {
                                // First tag filter - add all quests with this tag
                                return new HashSet<>(questsWithTag);
                            } else {
                                // Subsequent filters - retain only quests that have this tag
                                currentSet.retainAll(questsWithTag);
                                return currentSet;
                            }
                        })
                );
            }
        }

        return resultFuture.thenApply(ArrayList::new);
    }

    // ========================================
    // Statistics
    // ========================================

    @Override
    public CompletableFuture<Map<String, Integer>> getTagUsageStatistics() {
        return tagRepository.getTagUsageStatistics();
    }

    @Override
    public CompletableFuture<Void> initializePredefinedTags() {
        logger.info("Initializing predefined tags...");

        List<QuestTag> predefinedTags = List.of(
            QuestTag.PVE,
            QuestTag.PVP,
            QuestTag.EXPLORATION,
            QuestTag.CRAFTING,
            QuestTag.SOCIAL,
            QuestTag.COMBAT,
            QuestTag.PUZZLE
        );

        List<CompletableFuture<Void>> futures = predefinedTags.stream()
            .map(tag -> tagRepository.tagExists(tag.name())
                .thenCompose(exists -> {
                    if (!exists) {
                        logger.info("Creating predefined tag: " + tag.name());
                        return tagRepository.createTag(tag)
                            .thenApply(opt -> (Void) null);
                    } else {
                        logger.debug("Predefined tag already exists: " + tag.name());
                        return CompletableFuture.completedFuture((Void) null);
                    }
                })
            )
            .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
}
