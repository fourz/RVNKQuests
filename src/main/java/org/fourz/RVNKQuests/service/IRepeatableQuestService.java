package org.fourz.RVNKQuests.service;

import org.fourz.RVNKQuests.data.dto.PlayerQuestRepeatDTO;
import org.fourz.RVNKQuests.data.dto.QuestRepeatConfigDTO;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for repeatable quest management.
 *
 * <p>Handles quest repeatability configuration, cooldown tracking, and
 * completion count management.</p>
 */
public interface IRepeatableQuestService {

    // ==================== Configuration Management ====================

    /**
     * Get the repeat configuration for a quest.
     *
     * @param questId The quest identifier
     * @return The repeat configuration, or empty if not configured
     */
    CompletableFuture<Optional<QuestRepeatConfigDTO>> getRepeatConfig(String questId);

    /**
     * Save or update repeat configuration for a quest.
     *
     * @param config The repeat configuration
     * @return true if saved successfully
     */
    CompletableFuture<Boolean> saveRepeatConfig(QuestRepeatConfigDTO config);

    /**
     * Delete repeat configuration for a quest.
     *
     * @param questId The quest identifier
     * @return true if deleted successfully
     */
    CompletableFuture<Boolean> deleteRepeatConfig(String questId);

    // ==================== Player Repeat Tracking ====================

    /**
     * Get a player's repeat tracking data for a specific quest.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return The repeat tracking data, or empty if not tracked
     */
    CompletableFuture<Optional<PlayerQuestRepeatDTO>> getPlayerRepeatData(UUID playerUuid, String questId);

    /**
     * Record a quest completion for a player.
     * Updates completion count and sets next available time based on cooldown.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return The updated repeat tracking data
     */
    CompletableFuture<PlayerQuestRepeatDTO> recordCompletion(UUID playerUuid, String questId);

    /**
     * Check if a quest is available for a player.
     * Returns false if on cooldown or max completions reached.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return true if the quest is available
     */
    CompletableFuture<Boolean> isQuestAvailable(UUID playerUuid, String questId);

    /**
     * Get the remaining cooldown time for a quest in seconds.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return Remaining cooldown in seconds, or 0 if available
     */
    CompletableFuture<Long> getRemainingCooldown(UUID playerUuid, String questId);

    /**
     * Get the completion count for a player's quest.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return The completion count
     */
    CompletableFuture<Integer> getCompletionCount(UUID playerUuid, String questId);

    // ==================== Quest Reset ====================

    /**
     * Reset a player's progress on a repeatable quest.
     * Clears objectives, rewards, and resets cooldown.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return true if reset succeeded
     */
    CompletableFuture<Boolean> resetQuestForPlayer(UUID playerUuid, String questId);

    /**
     * Check if a quest can be repeated by a player.
     * Returns false if max completions reached or not repeatable.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return true if the quest can be repeated
     */
    CompletableFuture<Boolean> canRepeatQuest(UUID playerUuid, String questId);

    // ==================== Utility ====================

    /**
     * Check if using fallback storage.
     *
     * @return true if in fallback mode
     */
    boolean isInFallbackMode();

    /**
     * Shutdown the service.
     */
    void shutdown();
}
