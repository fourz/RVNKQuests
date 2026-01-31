package org.fourz.RVNKQuests.service;

import org.fourz.RVNKQuests.data.dto.QuestObjectiveProgressDTO;
import org.fourz.RVNKQuests.data.dto.QuestProgressDTO;
import org.fourz.RVNKQuests.quest.QuestState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for player quest progress tracking.
 * Provides cross-plugin access to player quest data and progress management.
 *
 * <p>Register with RVNKCore ServiceRegistry for use by other plugins:</p>
 * <pre>{@code
 * registry.registerService(IPlayerQuestService.class, questProgressService);
 * }</pre>
 *
 * <p>This interface extends the existing {@link IQuestProgressService} with
 * additional methods for cross-plugin integration.</p>
 *
 * <p>Pattern compliance:</p>
 * <ul>
 *   <li>Interface uses "I" prefix per RVNK coding standards</li>
 *   <li>All I/O operations return CompletableFuture (async-first)</li>
 *   <li>Thread-safe for concurrent access</li>
 * </ul>
 */
public interface IPlayerQuestService {

    // ==================== Player Session Management ====================

    /**
     * Load quest progress for a player (call on join).
     * @param playerId The player's UUID
     * @return CompletableFuture that completes when data is loaded
     */
    CompletableFuture<Void> loadPlayerProgress(UUID playerId);

    /**
     * Save and unload quest progress for a player (call on quit).
     * @param playerId The player's UUID
     * @return CompletableFuture that completes when data is saved
     */
    CompletableFuture<Void> saveAndUnloadPlayerProgress(UUID playerId);

    // ==================== Quest Progress Operations ====================

    /**
     * Get a player's progress on a specific quest.
     * @param playerId The player's UUID
     * @param questId The quest identifier
     * @return The progress, or empty if not started
     */
    CompletableFuture<Optional<QuestProgressDTO>> getProgress(UUID playerId, String questId);

    /**
     * Get a player's current state for a quest.
     * @param playerId The player's UUID
     * @param questId The quest identifier
     * @return The quest state (NOT_STARTED if no progress exists)
     */
    CompletableFuture<QuestState> getQuestState(UUID playerId, String questId);

    /**
     * Update a player's quest state.
     * @param playerId The player's UUID
     * @param questId The quest identifier
     * @param newState The new state
     * @return The updated progress
     */
    CompletableFuture<QuestProgressDTO> updateQuestState(UUID playerId, String questId, QuestState newState);

    /**
     * Set a player's path choice for a branching quest.
     * @param playerId The player's UUID
     * @param questId The quest identifier
     * @param pathChoice The chosen path identifier
     * @return The updated progress
     */
    CompletableFuture<QuestProgressDTO> setPathChoice(UUID playerId, String questId, String pathChoice);

    /**
     * Reset a player's progress on a specific quest.
     * @param playerId The player's UUID
     * @param questId The quest identifier
     * @return true if reset succeeded
     */
    CompletableFuture<Boolean> resetQuestProgress(UUID playerId, String questId);

    /**
     * Get all quest progress for a player.
     * @param playerId The player's UUID
     * @return List of all progress records
     */
    CompletableFuture<List<QuestProgressDTO>> getAllProgress(UUID playerId);

    // ==================== Objective Progress Operations ====================

    /**
     * Get objective progress for a specific objective.
     * @param playerId The player's UUID
     * @param questId The quest identifier
     * @param objectiveId The objective identifier
     * @return The objective progress, or empty if not tracked
     */
    CompletableFuture<Optional<QuestObjectiveProgressDTO>> getObjectiveProgress(
        UUID playerId, String questId, String objectiveId);

    /**
     * Increment objective progress.
     * @param playerId The player's UUID
     * @param questId The quest identifier
     * @param objectiveId The objective identifier
     * @param amount Amount to increment by
     * @return The updated objective progress
     */
    CompletableFuture<QuestObjectiveProgressDTO> incrementObjectiveProgress(
        UUID playerId, String questId, String objectiveId, int amount);

    /**
     * Initialize tracking for a new objective.
     * @param playerId The player's UUID
     * @param questId The quest identifier
     * @param objectiveId The objective identifier
     * @param targetCount The target count for completion
     * @return The new objective progress
     */
    CompletableFuture<QuestObjectiveProgressDTO> initializeObjective(
        UUID playerId, String questId, String objectiveId, int targetCount);

    /**
     * Mark an objective as complete.
     * @param playerId The player's UUID
     * @param questId The quest identifier
     * @param objectiveId The objective identifier
     * @return The updated objective progress
     */
    CompletableFuture<QuestObjectiveProgressDTO> completeObjective(
        UUID playerId, String questId, String objectiveId);

    /**
     * Get all objective progress for a quest.
     * @param playerId The player's UUID
     * @param questId The quest identifier
     * @return List of all objective progress
     */
    CompletableFuture<List<QuestObjectiveProgressDTO>> getAllObjectives(UUID playerId, String questId);

    // ==================== Reward Operations ====================

    /**
     * Check if a player has claimed a specific reward.
     * @param playerId The player's UUID
     * @param questId The quest identifier
     * @param rewardId The reward identifier
     * @return true if reward has been claimed
     */
    CompletableFuture<Boolean> hasClaimedReward(UUID playerId, String questId, String rewardId);

    /**
     * Mark a reward as claimed.
     * @param playerId The player's UUID
     * @param questId The quest identifier
     * @param rewardId The reward identifier
     * @return true if reward was marked as claimed
     */
    CompletableFuture<Boolean> claimReward(UUID playerId, String questId, String rewardId);

    // ==================== Service Status ====================

    /**
     * Check if the service is operating in fallback mode.
     * @return true if database unavailable and using YAML fallback
     */
    boolean isInFallbackMode();

    /**
     * Flush pending changes to persistent storage.
     * @return CompletableFuture that completes when flush is done
     */
    CompletableFuture<Void> flush();

    /**
     * Shutdown the service (call on plugin disable).
     */
    void shutdown();
}
