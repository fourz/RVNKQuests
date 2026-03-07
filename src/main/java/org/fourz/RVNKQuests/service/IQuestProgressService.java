package org.fourz.RVNKQuests.service;

import org.fourz.RVNKQuests.data.dto.QuestObjectiveProgressDTO;
import org.fourz.RVNKQuests.data.dto.QuestProgressDTO;
import org.fourz.RVNKQuests.quest.QuestState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for quest progress management.
 *
 * <p>Provides high-level operations for managing player quest progress,
 * with caching and async support.</p>
 */
public interface IQuestProgressService {

    // ==================== Player Session Management ====================

    /**
     * Load quest progress for a player (call on join).
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture that completes when data is loaded
     */
    CompletableFuture<Void> loadPlayerProgress(UUID playerUuid);

    /**
     * Save and unload quest progress for a player (call on quit).
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture that completes when data is saved
     */
    CompletableFuture<Void> saveAndUnloadPlayerProgress(UUID playerUuid);

    // ==================== Quest State Operations ====================

    /**
     * Get a player's progress on a specific quest.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return The progress, or empty if not started
     */
    CompletableFuture<Optional<QuestProgressDTO>> getProgress(UUID playerUuid, String questId);

    /**
     * Get a player's current state for a quest.
     * Returns NOT_STARTED if no progress exists.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return The quest state
     */
    CompletableFuture<QuestState> getQuestState(UUID playerUuid, String questId);

    /**
     * Update a player's quest state.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param newState The new state
     * @return The updated progress
     */
    CompletableFuture<QuestProgressDTO> updateQuestState(UUID playerUuid, String questId, QuestState newState);

    /**
     * Set a player's path choice for a quest.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param pathChoice The chosen path
     * @return The updated progress
     */
    CompletableFuture<QuestProgressDTO> setPathChoice(UUID playerUuid, String questId, String pathChoice);

    /**
     * Reset a player's progress on a specific quest.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return true if reset succeeded
     */
    CompletableFuture<Boolean> resetQuestProgress(UUID playerUuid, String questId);

    /**
     * Get all quest progress for a player.
     *
     * @param playerUuid The player's UUID
     * @return List of all progress records
     */
    CompletableFuture<List<QuestProgressDTO>> getAllProgress(UUID playerUuid);

    // ==================== Objective Operations ====================

    /**
     * Get objective progress for a specific objective.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param objectiveId The objective identifier
     * @return The objective progress, or empty if not tracked
     */
    CompletableFuture<Optional<QuestObjectiveProgressDTO>> getObjectiveProgress(
        UUID playerUuid, String questId, String objectiveId);

    /**
     * Increment objective progress.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param objectiveId The objective identifier
     * @param amount Amount to increment
     * @return The updated objective progress
     */
    CompletableFuture<QuestObjectiveProgressDTO> incrementObjectiveProgress(
        UUID playerUuid, String questId, String objectiveId, int amount);

    /**
     * Initialize objective tracking for a player.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param objectiveId The objective identifier
     * @param targetCount The target count for completion
     * @return The new objective progress
     */
    CompletableFuture<QuestObjectiveProgressDTO> initializeObjective(
        UUID playerUuid, String questId, String objectiveId, int targetCount);

    /**
     * Mark an objective as complete.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param objectiveId The objective identifier
     * @return The updated objective progress
     */
    CompletableFuture<QuestObjectiveProgressDTO> completeObjective(
        UUID playerUuid, String questId, String objectiveId);

    /**
     * Get all objectives for a quest.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return List of all objective progress
     */
    CompletableFuture<List<QuestObjectiveProgressDTO>> getAllObjectives(UUID playerUuid, String questId);

    // ==================== Reward Operations ====================

    /**
     * Check if a reward has been claimed.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param rewardId The reward identifier
     * @return true if already claimed
     */
    CompletableFuture<Boolean> hasClaimedReward(UUID playerUuid, String questId, String rewardId);

    /**
     * Mark a reward as claimed.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param rewardId The reward identifier
     * @return true if successfully marked
     */
    CompletableFuture<Boolean> claimReward(UUID playerUuid, String questId, String rewardId);

    // ==================== Utility ====================

    /**
     * Check if using fallback storage.
     *
     * @return true if in fallback mode
     */
    boolean isInFallbackMode();

    /**
     * Flush pending changes to storage.
     *
     * @return CompletableFuture that completes when flush is done
     */
    CompletableFuture<Void> flush();

    /**
     * Shutdown the service.
     */
    void shutdown();
}
