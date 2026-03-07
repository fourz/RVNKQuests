package org.fourz.RVNKQuests.data;

import org.fourz.RVNKQuests.data.dto.QuestObjectiveProgressDTO;
import org.fourz.RVNKQuests.data.dto.QuestProgressDTO;
import org.fourz.RVNKQuests.data.dto.QuestRewardClaimedDTO;
import org.fourz.RVNKQuests.quest.QuestState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for quest progress persistence.
 *
 * <p>All methods return CompletableFuture to support async operations
 * without blocking the main server thread.</p>
 */
public interface IQuestProgressRepository {

    // ==================== Quest Progress Operations ====================

    /**
     * Save or update quest progress for a player.
     *
     * @param progress The quest progress to save
     * @return CompletableFuture that completes when save is done
     */
    CompletableFuture<Boolean> saveProgress(QuestProgressDTO progress);

    /**
     * Get quest progress for a player on a specific quest.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return The quest progress, or empty if not found
     */
    CompletableFuture<Optional<QuestProgressDTO>> getProgress(UUID playerUuid, String questId);

    /**
     * Get all quest progress for a player.
     *
     * @param playerUuid The player's UUID
     * @return List of all quest progress for the player
     */
    CompletableFuture<List<QuestProgressDTO>> getAllProgressForPlayer(UUID playerUuid);

    /**
     * Get all players with progress on a specific quest.
     *
     * @param questId The quest identifier
     * @return List of all progress records for the quest
     */
    CompletableFuture<List<QuestProgressDTO>> getAllProgressForQuest(String questId);

    /**
     * Get all players with a specific quest state.
     *
     * @param questId The quest identifier
     * @param state The quest state to filter by
     * @return List of progress records matching the state
     */
    CompletableFuture<List<QuestProgressDTO>> getProgressByState(String questId, QuestState state);

    /**
     * Delete quest progress for a player on a specific quest.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return true if deletion succeeded
     */
    CompletableFuture<Boolean> deleteProgress(UUID playerUuid, String questId);

    /**
     * Delete all quest progress for a player.
     *
     * @param playerUuid The player's UUID
     * @return true if deletion succeeded
     */
    CompletableFuture<Boolean> deleteAllProgressForPlayer(UUID playerUuid);

    // ==================== Objective Progress Operations ====================

    /**
     * Save or update objective progress.
     *
     * @param objective The objective progress to save
     * @return CompletableFuture that completes when save is done
     */
    CompletableFuture<Boolean> saveObjectiveProgress(QuestObjectiveProgressDTO objective);

    /**
     * Get objective progress for a player.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param objectiveId The objective identifier
     * @return The objective progress, or empty if not found
     */
    CompletableFuture<Optional<QuestObjectiveProgressDTO>> getObjectiveProgress(
        UUID playerUuid, String questId, String objectiveId);

    /**
     * Get all objective progress for a player on a quest.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return List of all objective progress for the quest
     */
    CompletableFuture<List<QuestObjectiveProgressDTO>> getAllObjectiveProgress(
        UUID playerUuid, String questId);

    /**
     * Delete all objective progress for a player on a quest.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return true if deletion succeeded
     */
    CompletableFuture<Boolean> deleteObjectiveProgress(UUID playerUuid, String questId);

    // ==================== Reward Tracking Operations ====================

    /**
     * Record that a player claimed a reward.
     *
     * @param rewardClaimed The reward claim record
     * @return true if the record was saved
     */
    CompletableFuture<Boolean> saveRewardClaimed(QuestRewardClaimedDTO rewardClaimed);

    /**
     * Check if a player has claimed a specific reward.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param rewardId The reward identifier
     * @return true if the reward has been claimed
     */
    CompletableFuture<Boolean> hasClaimedReward(UUID playerUuid, String questId, String rewardId);

    /**
     * Get all claimed rewards for a player on a quest.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return List of all claimed reward records
     */
    CompletableFuture<List<QuestRewardClaimedDTO>> getClaimedRewards(UUID playerUuid, String questId);

    // ==================== Utility Operations ====================

    /**
     * Check if the repository is currently in fallback mode.
     *
     * @return true if using fallback storage
     */
    boolean isInFallbackMode();

    /**
     * Flush any pending writes (for YAML fallback).
     *
     * @return CompletableFuture that completes when flush is done
     */
    CompletableFuture<Void> flush();
}
