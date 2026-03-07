package org.fourz.RVNKQuests.data;

import org.fourz.RVNKQuests.data.dto.ObjectiveDTO;
import org.fourz.RVNKQuests.data.dto.QuestDTO;
import org.fourz.RVNKQuests.data.dto.RewardDTO;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for quest definition persistence.
 *
 * <p>This interface handles CRUD operations for quest templates/definitions,
 * not player progress tracking. For player progress, see {@link IQuestProgressRepository}.</p>
 *
 * <p>All methods return CompletableFuture to support async operations
 * without blocking the main server thread.</p>
 */
public interface IQuestRepository {

    // ==================== Quest CRUD Operations ====================

    /**
     * Save or update a quest definition.
     *
     * @param quest The quest definition to save
     * @return CompletableFuture that completes with true if save succeeded
     */
    CompletableFuture<Boolean> save(QuestDTO quest);

    /**
     * Get a quest by its unique identifier.
     *
     * @param questId The quest identifier
     * @return The quest definition, or empty if not found
     */
    CompletableFuture<Optional<QuestDTO>> findById(String questId);

    /**
     * Get a quest by its display name.
     *
     * @param name The quest display name (case-insensitive)
     * @return The quest definition, or empty if not found
     */
    CompletableFuture<Optional<QuestDTO>> findByName(String name);

    /**
     * Get all quest definitions.
     *
     * @return List of all quest definitions
     */
    CompletableFuture<List<QuestDTO>> findAll();

    /**
     * Delete a quest definition by its identifier.
     *
     * @param questId The quest identifier
     * @return true if deletion succeeded
     */
    CompletableFuture<Boolean> deleteById(String questId);

    /**
     * Check if a quest exists by identifier.
     *
     * @param questId The quest identifier
     * @return true if the quest exists
     */
    CompletableFuture<Boolean> exists(String questId);

    // ==================== Query Operations ====================

    /**
     * Get all quests in a specific category.
     *
     * @param category The category name
     * @return List of quests in the category
     */
    CompletableFuture<List<QuestDTO>> findByCategory(String category);

    /**
     * Get all repeatable quests.
     *
     * @return List of repeatable quest definitions
     */
    CompletableFuture<List<QuestDTO>> findRepeatable();

    /**
     * Get all quests that have a specific quest as prerequisite.
     *
     * @param prerequisiteQuestId The prerequisite quest ID
     * @return List of quests that require the specified quest
     */
    CompletableFuture<List<QuestDTO>> findByPrerequisite(String prerequisiteQuestId);

    /**
     * Get all quests matching a search term in name or description.
     *
     * @param searchTerm The search term (case-insensitive)
     * @return List of matching quests
     */
    CompletableFuture<List<QuestDTO>> search(String searchTerm);

    /**
     * Get all distinct categories that have quests.
     *
     * @return List of category names
     */
    CompletableFuture<List<String>> findAllCategories();

    /**
     * Get quests without any prerequisites (starter quests).
     *
     * @return List of quests with empty prerequisites
     */
    CompletableFuture<List<QuestDTO>> findStarterQuests();

    // ==================== Objective Operations ====================

    /**
     * Save or update objectives for a quest.
     * Replaces all existing objectives for the quest.
     *
     * @param questId The quest identifier
     * @param objectives The objectives to save
     * @return true if save succeeded
     */
    CompletableFuture<Boolean> saveObjectives(String questId, List<ObjectiveDTO> objectives);

    /**
     * Get all objectives for a quest.
     *
     * @param questId The quest identifier
     * @return List of objectives for the quest
     */
    CompletableFuture<List<ObjectiveDTO>> findObjectives(String questId);

    /**
     * Add an objective to a quest.
     *
     * @param questId The quest identifier
     * @param objective The objective to add
     * @return true if addition succeeded
     */
    CompletableFuture<Boolean> addObjective(String questId, ObjectiveDTO objective);

    /**
     * Remove an objective from a quest.
     *
     * @param questId The quest identifier
     * @param objectiveId The objective identifier
     * @return true if removal succeeded
     */
    CompletableFuture<Boolean> removeObjective(String questId, String objectiveId);

    // ==================== Reward Operations ====================

    /**
     * Save or update rewards for a quest.
     * Replaces all existing rewards for the quest.
     *
     * @param questId The quest identifier
     * @param rewards The rewards to save
     * @return true if save succeeded
     */
    CompletableFuture<Boolean> saveRewards(String questId, List<RewardDTO> rewards);

    /**
     * Get all rewards for a quest.
     *
     * @param questId The quest identifier
     * @return List of rewards for the quest
     */
    CompletableFuture<List<RewardDTO>> findRewards(String questId);

    /**
     * Add a reward to a quest.
     *
     * @param questId The quest identifier
     * @param reward The reward to add
     * @return true if addition succeeded
     */
    CompletableFuture<Boolean> addReward(String questId, RewardDTO reward);

    /**
     * Remove a reward from a quest.
     *
     * @param questId The quest identifier
     * @param rewardId The reward identifier
     * @return true if removal succeeded
     */
    CompletableFuture<Boolean> removeReward(String questId, String rewardId);

    // ==================== Bulk Operations ====================

    /**
     * Save multiple quest definitions in a batch.
     *
     * @param quests The quests to save
     * @return The number of quests successfully saved
     */
    CompletableFuture<Integer> saveAll(List<QuestDTO> quests);

    /**
     * Delete all quests in a category.
     *
     * @param category The category name
     * @return The number of quests deleted
     */
    CompletableFuture<Integer> deleteByCategory(String category);

    /**
     * Count total number of quests.
     *
     * @return The total quest count
     */
    CompletableFuture<Long> count();

    /**
     * Count quests in a specific category.
     *
     * @param category The category name
     * @return The quest count in the category
     */
    CompletableFuture<Long> countByCategory(String category);

    // ==================== Utility Operations ====================

    /**
     * Check if the repository is currently in fallback mode.
     *
     * @return true if using fallback storage (YAML)
     */
    boolean isInFallbackMode();

    /**
     * Flush any pending writes (for YAML fallback).
     *
     * @return CompletableFuture that completes when flush is done
     */
    CompletableFuture<Void> flush();

    /**
     * Reload all quest definitions from storage.
     * Useful after external changes to quest files.
     *
     * @return CompletableFuture that completes when reload is done
     */
    CompletableFuture<Void> reload();
}
