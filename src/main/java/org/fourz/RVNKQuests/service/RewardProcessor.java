package org.fourz.RVNKQuests.service;

import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.data.dto.RewardType;
import org.fourz.RVNKQuests.service.IRewardService.RewardDeliveryResult;
import org.fourz.RVNKQuests.service.IRewardService.RewardValidationResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for reward type processors.
 *
 * <p>Each reward type (ITEM, EXPERIENCE, CURRENCY, etc.) has a dedicated
 * processor that handles validation and delivery. Processors are registered
 * with {@link IRewardService} and called during reward delivery.</p>
 *
 * <h2>Implementation Requirements</h2>
 * <ul>
 *   <li>Must be thread-safe (may be called concurrently)</li>
 *   <li>Must handle offline players gracefully</li>
 *   <li>Must return meaningful error messages</li>
 *   <li>Should support async operations where appropriate</li>
 * </ul>
 *
 * <h2>Example Implementation</h2>
 * <pre>{@code
 * public class ItemRewardProcessor implements RewardProcessor {
 *     @Override
 *     public RewardType getType() {
 *         return RewardType.ITEM;
 *     }
 *
 *     @Override
 *     public CompletableFuture<RewardDeliveryResult> deliver(UUID playerId, RewardDTO reward) {
 *         return CompletableFuture.supplyAsync(() -> {
 *             Player player = Bukkit.getPlayer(playerId);
 *             if (player == null) {
 *                 return RewardDeliveryResult.failure(reward, "Player offline", "PLAYER_OFFLINE");
 *             }
 *             // Item delivery logic...
 *             return RewardDeliveryResult.success(reward, "Delivered " + reward.amount() + " items");
 *         });
 *     }
 * }
 * }</pre>
 *
 * @since 1.0
 */
public interface RewardProcessor {

    /**
     * Get the reward type this processor handles.
     *
     * @return The reward type
     */
    RewardType getType();

    /**
     * Deliver the reward to a player.
     *
     * <p>This method performs the actual reward delivery. It should:
     * <ul>
     *   <li>Check if the player is online (for most reward types)</li>
     *   <li>Validate any preconditions</li>
     *   <li>Perform the delivery action</li>
     *   <li>Return a detailed result</li>
     * </ul></p>
     *
     * @param playerId The player's UUID
     * @param reward The reward to deliver
     * @return CompletableFuture with delivery result
     */
    CompletableFuture<RewardDeliveryResult> deliver(UUID playerId, RewardDTO reward);

    /**
     * Validate if the reward can be delivered to the player.
     *
     * <p>This method checks preconditions without performing delivery.
     * Override to add custom validation logic.</p>
     *
     * @param playerId The player's UUID
     * @param reward The reward to validate
     * @return CompletableFuture with validation result
     */
    default CompletableFuture<RewardValidationResult> validate(UUID playerId, RewardDTO reward) {
        // Default implementation - just check type matches
        if (reward.type() != getType()) {
            return CompletableFuture.completedFuture(
                RewardValidationResult.invalid(
                    reward, 
                    "Wrong processor for reward type",
                    "Expected " + getType() + " but got " + reward.type()
                )
            );
        }
        return CompletableFuture.completedFuture(RewardValidationResult.valid(reward));
    }

    /**
     * Check if this processor requires the player to be online.
     *
     * <p>Override to return false for rewards that can be delivered
     * to offline players (e.g., database-backed currency, permissions).</p>
     *
     * @return true if player must be online (default: true)
     */
    default boolean requiresOnlinePlayer() {
        return true;
    }

    /**
     * Check if this processor supports queuing for offline players.
     *
     * <p>If true and player is offline, rewards can be queued for
     * delivery when they log in.</p>
     *
     * @return true if offline queueing is supported
     */
    default boolean supportsOfflineQueue() {
        return false;
    }

    /**
     * Get the priority of this processor for execution order.
     *
     * <p>Higher priority processors are executed first in batch
     * deliveries. Default is 0 (normal priority).</p>
     *
     * @return Priority value (higher = earlier execution)
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Check if this processor is currently available.
     *
     * <p>Processors may be unavailable if external dependencies
     * (economy plugins, permission systems) are not loaded.</p>
     *
     * @return true if processor can handle rewards
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * Get the display name for this reward type.
     *
     * <p>Used in messages and UI elements.</p>
     *
     * @return Human-readable type name
     */
    default String getDisplayName() {
        return getType().name().toLowerCase().replace('_', ' ');
    }

    /**
     * Format a reward for display in messages.
     *
     * @param reward The reward to format
     * @return Formatted string (e.g., "10 Experience Points")
     */
    default String formatReward(RewardDTO reward) {
        if (reward.description() != null && !reward.description().isEmpty()) {
            return reward.description();
        }
        return reward.amount() + " " + getDisplayName();
    }
}
