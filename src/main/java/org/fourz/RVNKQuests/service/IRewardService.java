package org.fourz.RVNKQuests.service;

import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.data.dto.RewardType;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for quest reward delivery and management.
 *
 * <p>Handles the actual delivery of rewards to players, including
 * validation, processing, and error recovery. Supports async
 * delivery for performance-critical operations.</p>
 *
 * <h2>Supported Reward Types</h2>
 * <ul>
 *   <li>{@link RewardType#ITEM} - Physical item delivery</li>
 *   <li>{@link RewardType#EXPERIENCE} - XP grants</li>
 *   <li>{@link RewardType#CURRENCY} - Token/economy integration</li>
 *   <li>{@link RewardType#PERMISSION} - LuckPerms permission grants</li>
 *   <li>{@link RewardType#COMMAND} - Server command execution</li>
 *   <li>{@link RewardType#TITLE} - Title/achievement display</li>
 *   <li>{@link RewardType#QUEST_UNLOCK} - Quest availability unlock</li>
 *   <li>{@link RewardType#LORE} - RVNKLore integration</li>
 *   <li>{@link RewardType#CUSTOM} - Plugin-defined handlers</li>
 * </ul>
 *
 * @since 1.0
 */
public interface IRewardService {

    // ==================== Single Reward Delivery ====================

    /**
     * Deliver a single reward to a player.
     *
     * <p>Validates the reward, checks if the player can receive it,
     * and processes the delivery. Returns a detailed result including
     * success status and any error messages.</p>
     *
     * @param playerId The player's UUID
     * @param reward The reward to deliver
     * @return CompletableFuture with delivery result
     */
    CompletableFuture<RewardDeliveryResult> deliverReward(UUID playerId, RewardDTO reward);

    /**
     * Deliver a reward with quest context for tracking.
     *
     * @param playerId The player's UUID
     * @param questId The quest this reward is from
     * @param reward The reward to deliver
     * @return CompletableFuture with delivery result
     */
    CompletableFuture<RewardDeliveryResult> deliverReward(UUID playerId, String questId, RewardDTO reward);

    // ==================== Batch Reward Delivery ====================

    /**
     * Deliver multiple rewards to a player.
     *
     * <p>Processes rewards in order, stopping on critical failures
     * unless continueOnError is true. Returns results for each reward.</p>
     *
     * @param playerId The player's UUID
     * @param rewards The list of rewards to deliver
     * @return CompletableFuture with batch delivery result
     */
    CompletableFuture<BatchRewardResult> deliverRewards(UUID playerId, List<RewardDTO> rewards);

    /**
     * Deliver multiple rewards with quest context.
     *
     * @param playerId The player's UUID
     * @param questId The quest these rewards are from
     * @param rewards The list of rewards to deliver
     * @param continueOnError Whether to continue if a reward fails
     * @return CompletableFuture with batch delivery result
     */
    CompletableFuture<BatchRewardResult> deliverRewards(
        UUID playerId, 
        String questId, 
        List<RewardDTO> rewards,
        boolean continueOnError
    );

    // ==================== Validation ====================

    /**
     * Validate a reward can be delivered to a player.
     *
     * <p>Checks requirements like inventory space, permissions,
     * and reward type availability without actually delivering.</p>
     *
     * @param playerId The player's UUID
     * @param reward The reward to validate
     * @return CompletableFuture with validation result
     */
    CompletableFuture<RewardValidationResult> validateReward(UUID playerId, RewardDTO reward);

    /**
     * Validate multiple rewards can be delivered.
     *
     * @param playerId The player's UUID
     * @param rewards The rewards to validate
     * @return CompletableFuture with list of validation results
     */
    CompletableFuture<List<RewardValidationResult>> validateRewards(UUID playerId, List<RewardDTO> rewards);

    // ==================== Processor Management ====================

    /**
     * Register a custom reward processor for a reward type.
     *
     * @param type The reward type to handle
     * @param processor The processor implementation
     */
    void registerProcessor(RewardType type, RewardProcessor processor);

    /**
     * Check if a processor is registered for a reward type.
     *
     * @param type The reward type
     * @return true if a processor is registered
     */
    boolean hasProcessor(RewardType type);

    /**
     * Get the processor for a reward type.
     *
     * @param type The reward type
     * @return The processor, or null if not registered
     */
    RewardProcessor getProcessor(RewardType type);

    // ==================== Service Status ====================

    /**
     * Check if the service is operating in fallback mode.
     *
     * @return true if external integrations unavailable
     */
    boolean isInFallbackMode();

    /**
     * Get supported reward types.
     *
     * @return List of reward types with registered processors
     */
    List<RewardType> getSupportedTypes();

    // ==================== Result Records ====================

    /**
     * Result of a single reward delivery attempt.
     *
     * @param success Whether the delivery succeeded
     * @param reward The reward that was delivered (or attempted)
     * @param message Human-readable status message
     * @param errorCode Error code if failed (null if success)
     * @param metadata Additional delivery information
     */
    record RewardDeliveryResult(
        boolean success,
        RewardDTO reward,
        String message,
        String errorCode,
        Map<String, Object> metadata
    ) {
        /**
         * Compact constructor with validation.
         */
        public RewardDeliveryResult {
            Objects.requireNonNull(reward, "reward cannot be null");
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }

        /**
         * Create a successful result.
         *
         * @param reward The delivered reward
         * @param message Success message
         * @return Successful result
         */
        public static RewardDeliveryResult success(RewardDTO reward, String message) {
            return new RewardDeliveryResult(true, reward, message, null, Map.of());
        }

        /**
         * Create a successful result with metadata.
         *
         * @param reward The delivered reward
         * @param message Success message
         * @param metadata Additional information
         * @return Successful result
         */
        public static RewardDeliveryResult success(RewardDTO reward, String message, Map<String, Object> metadata) {
            return new RewardDeliveryResult(true, reward, message, null, metadata);
        }

        /**
         * Create a failed result.
         *
         * @param reward The reward that failed
         * @param message Error message
         * @param errorCode Error code for programmatic handling
         * @return Failed result
         */
        public static RewardDeliveryResult failure(RewardDTO reward, String message, String errorCode) {
            return new RewardDeliveryResult(false, reward, message, errorCode, Map.of());
        }

        /**
         * Create a failed result with metadata.
         *
         * @param reward The reward that failed
         * @param message Error message
         * @param errorCode Error code
         * @param metadata Additional error information
         * @return Failed result
         */
        public static RewardDeliveryResult failure(RewardDTO reward, String message, 
                                                   String errorCode, Map<String, Object> metadata) {
            return new RewardDeliveryResult(false, reward, message, errorCode, metadata);
        }
    }

    /**
     * Result of batch reward delivery.
     *
     * @param totalRewards Total number of rewards attempted
     * @param successCount Number of successful deliveries
     * @param failureCount Number of failed deliveries
     * @param results Individual results for each reward
     * @param stoppedEarly Whether processing was stopped due to critical error
     */
    record BatchRewardResult(
        int totalRewards,
        int successCount,
        int failureCount,
        List<RewardDeliveryResult> results,
        boolean stoppedEarly
    ) {
        /**
         * Compact constructor with validation.
         */
        public BatchRewardResult {
            results = results == null ? List.of() : List.copyOf(results);
            if (totalRewards < 0) totalRewards = 0;
            if (successCount < 0) successCount = 0;
            if (failureCount < 0) failureCount = 0;
        }

        /**
         * Check if all rewards were delivered successfully.
         *
         * @return true if all succeeded
         */
        public boolean allSucceeded() {
            return failureCount == 0 && !stoppedEarly;
        }

        /**
         * Check if any rewards failed.
         *
         * @return true if any failed
         */
        public boolean hasFailures() {
            return failureCount > 0;
        }

        /**
         * Get only the failed results.
         *
         * @return List of failed delivery results
         */
        public List<RewardDeliveryResult> getFailedResults() {
            return results.stream()
                .filter(r -> !r.success())
                .toList();
        }

        /**
         * Get only the successful results.
         *
         * @return List of successful delivery results
         */
        public List<RewardDeliveryResult> getSuccessfulResults() {
            return results.stream()
                .filter(RewardDeliveryResult::success)
                .toList();
        }
    }

    /**
     * Result of reward validation.
     *
     * @param valid Whether the reward can be delivered
     * @param reward The validated reward
     * @param message Validation message (reason if invalid)
     * @param requirements Unmet requirements (if invalid)
     */
    record RewardValidationResult(
        boolean valid,
        RewardDTO reward,
        String message,
        List<String> requirements
    ) {
        /**
         * Compact constructor with validation.
         */
        public RewardValidationResult {
            Objects.requireNonNull(reward, "reward cannot be null");
            requirements = requirements == null ? List.of() : List.copyOf(requirements);
        }

        /**
         * Create a valid result.
         *
         * @param reward The validated reward
         * @return Valid result
         */
        public static RewardValidationResult valid(RewardDTO reward) {
            return new RewardValidationResult(true, reward, "Reward can be delivered", List.of());
        }

        /**
         * Create an invalid result.
         *
         * @param reward The invalid reward
         * @param message Why it's invalid
         * @param requirements List of unmet requirements
         * @return Invalid result
         */
        public static RewardValidationResult invalid(RewardDTO reward, String message, List<String> requirements) {
            return new RewardValidationResult(false, reward, message, requirements);
        }

        /**
         * Create an invalid result with single requirement.
         *
         * @param reward The invalid reward
         * @param message Why it's invalid
         * @param requirement The unmet requirement
         * @return Invalid result
         */
        public static RewardValidationResult invalid(RewardDTO reward, String message, String requirement) {
            return new RewardValidationResult(false, reward, message, List.of(requirement));
        }
    }
}
