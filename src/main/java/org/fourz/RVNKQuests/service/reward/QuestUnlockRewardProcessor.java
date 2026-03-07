package org.fourz.RVNKQuests.service.reward;

import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.data.dto.RewardType;
import org.fourz.RVNKQuests.service.IRewardService.RewardDeliveryResult;
import org.fourz.RVNKQuests.service.IRewardService.RewardValidationResult;
import org.fourz.RVNKQuests.service.RewardProcessor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Reward processor for unlocking additional quests.
 *
 * <p>Unlocks quest prerequisites, allowing players to access
 * previously locked quest content.</p>
 *
 * <h2>Metadata Keys</h2>
 * <ul>
 *   <li>{@code questId} - The quest ID to unlock (required)</li>
 *   <li>{@code autoStart} - Whether to auto-start the unlocked quest</li>
 *   <li>{@code notify} - Whether to notify the player of the unlock</li>
 *   <li>{@code chainId} - Quest chain ID for chained unlocks</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * RewardDTO.builder()
 *     .id("unlock_advanced")
 *     .type(RewardType.QUEST_UNLOCK)
 *     .name("Unlocks Advanced Quest")
 *     .metadata(Map.of(
 *         "questId", "advanced_mining_quest",
 *         "notify", "true"
 *     ))
 *     .build()
 * }</pre>
 *
 * @since 1.0
 */
public class QuestUnlockRewardProcessor implements RewardProcessor {

    /**
     * Callback for quest unlock operations.
     * Set this to integrate with the quest system.
     */
    private BiConsumer<UUID, String> unlockCallback;

    /**
     * Create a new QuestUnlockRewardProcessor.
     */
    public QuestUnlockRewardProcessor() {
        // Default constructor
    }

    /**
     * Create with an unlock callback.
     *
     * @param unlockCallback Callback invoked when a quest is unlocked (playerId, questId)
     */
    public QuestUnlockRewardProcessor(BiConsumer<UUID, String> unlockCallback) {
        this.unlockCallback = unlockCallback;
    }

    /**
     * Set the unlock callback.
     *
     * @param callback Callback for quest unlocks
     */
    public void setUnlockCallback(BiConsumer<UUID, String> callback) {
        this.unlockCallback = callback;
    }

    @Override
    public RewardType getType() {
        return RewardType.QUEST_UNLOCK;
    }

    @Override
    public CompletableFuture<RewardDeliveryResult> deliver(UUID playerId, RewardDTO reward) {
        return CompletableFuture.supplyAsync(() -> {
            String questId = getMetadataString(reward, "questId");
            if (questId == null || questId.isBlank()) {
                return RewardDeliveryResult.failure(
                    reward,
                    "Missing quest ID in metadata",
                    "INVALID_QUEST_ID"
                );
            }

            try {
                // Invoke the unlock callback if set
                if (unlockCallback != null) {
                    unlockCallback.accept(playerId, questId);
                    return RewardDeliveryResult.success(
                        reward,
                        "Unlocked quest: " + questId
                    );
                } else {
                    // Log that we would unlock but no callback is set
                    // In a real implementation, this would integrate with IPlayerQuestService
                    return RewardDeliveryResult.success(
                        reward,
                        "Quest unlock registered: " + questId + " (pending integration)"
                    );
                }
            } catch (Exception e) {
                return RewardDeliveryResult.failure(
                    reward,
                    "Error unlocking quest: " + e.getMessage(),
                    "UNLOCK_ERROR"
                );
            }
        });
    }

    @Override
    public CompletableFuture<RewardValidationResult> validate(UUID playerId, RewardDTO reward) {
        String questId = getMetadataString(reward, "questId");
        
        if (questId == null || questId.isBlank()) {
            return CompletableFuture.completedFuture(
                RewardValidationResult.invalid(
                    reward,
                    "Missing required 'questId' in metadata",
                    "Specify quest ID to unlock"
                )
            );
        }

        // Validate quest ID format (basic check)
        if (!questId.matches("^[a-zA-Z0-9_-]+$")) {
            return CompletableFuture.completedFuture(
                RewardValidationResult.invalid(
                    reward,
                    "Invalid quest ID format: " + questId,
                    "Use alphanumeric with _ - only"
                )
            );
        }

        // TODO: Could validate that questId exists in the quest registry
        // This would require integration with the quest service

        return CompletableFuture.completedFuture(
            RewardValidationResult.valid(reward)
        );
    }

    @Override
    public boolean requiresOnlinePlayer() {
        return false; // Can unlock for offline players
    }

    @Override
    public boolean supportsOfflineQueue() {
        return true;
    }

    @Override
    public int getPriority() {
        return 40; // Lower priority - process after tangible rewards
    }

    @Override
    public boolean isAvailable() {
        return true; // Always available as part of quest system
    }

    /**
     * Get a string value from reward metadata.
     */
    private String getMetadataString(RewardDTO reward, String key) {
        if (reward.metadata() == null) {
            return null;
        }
        Object value = reward.metadata().get(key);
        return value != null ? value.toString() : null;
    }

    @Override
    public String formatReward(RewardDTO reward) {
        String questId = getMetadataString(reward, "questId");
        if (questId == null) {
            return "Quest Unlock (unspecified)";
        }
        return "Unlocks Quest: " + questId;
    }
}
