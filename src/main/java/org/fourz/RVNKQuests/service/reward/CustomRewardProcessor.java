package org.fourz.RVNKQuests.service.reward;

import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.data.dto.RewardType;
import org.fourz.RVNKQuests.service.IRewardService.RewardDeliveryResult;
import org.fourz.RVNKQuests.service.IRewardService.RewardValidationResult;
import org.fourz.RVNKQuests.service.RewardProcessor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Extensible processor for custom reward types.
 *
 * <p>Provides a framework for implementing custom rewards
 * without modifying the core reward system.</p>
 *
 * <h2>Metadata Keys</h2>
 * <ul>
 *   <li>{@code handler} - The custom handler name (required)</li>
 *   <li>Additional keys depend on the registered handler</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Register custom handler
 * CustomRewardProcessor processor = new CustomRewardProcessor();
 * processor.registerHandler("firework", (playerId, reward) -> {
 *     // Spawn firework at player location
 *     Player player = Bukkit.getPlayer(playerId);
 *     if (player != null) {
 *         // Create firework effect
 *         return RewardDeliveryResult.success(reward, "Celebration!");
 *     }
 *     return RewardDeliveryResult.failure(reward, "Player offline", "PLAYER_OFFLINE");
 * });
 *
 * // Use in reward
 * RewardDTO.builder()
 *     .id("celebration")
 *     .type(RewardType.CUSTOM)
 *     .name("Celebration Effect")
 *     .metadata(Map.of(
 *         "handler", "firework",
 *         "color", "red"
 *     ))
 *     .build()
 * }</pre>
 *
 * @since 1.0
 */
public class CustomRewardProcessor implements RewardProcessor {

    /**
     * Registry of custom handlers.
     */
    private final Map<String, BiFunction<UUID, RewardDTO, RewardDeliveryResult>> handlers;
    
    /**
     * Registry of custom validators.
     */
    private final Map<String, BiFunction<UUID, RewardDTO, RewardValidationResult>> validators;

    /**
     * Create a new CustomRewardProcessor.
     */
    public CustomRewardProcessor() {
        this.handlers = new ConcurrentHashMap<>();
        this.validators = new ConcurrentHashMap<>();
    }

    /**
     * Register a custom reward handler.
     *
     * @param handlerName Unique handler identifier
     * @param handler Function that processes the reward
     */
    public void registerHandler(
            String handlerName, 
            BiFunction<UUID, RewardDTO, RewardDeliveryResult> handler) {
        handlers.put(handlerName.toLowerCase(), handler);
    }

    /**
     * Register a custom reward validator.
     *
     * @param handlerName Handler name to validate for
     * @param validator Function that validates the reward
     */
    public void registerValidator(
            String handlerName,
            BiFunction<UUID, RewardDTO, RewardValidationResult> validator) {
        validators.put(handlerName.toLowerCase(), validator);
    }

    /**
     * Unregister a handler.
     *
     * @param handlerName Handler to remove
     * @return true if removed
     */
    public boolean unregisterHandler(String handlerName) {
        validators.remove(handlerName.toLowerCase());
        return handlers.remove(handlerName.toLowerCase()) != null;
    }

    /**
     * Check if a handler is registered.
     *
     * @param handlerName Handler name
     * @return true if registered
     */
    public boolean hasHandler(String handlerName) {
        return handlers.containsKey(handlerName.toLowerCase());
    }

    /**
     * Get all registered handler names.
     *
     * @return Set of handler names
     */
    public java.util.Set<String> getRegisteredHandlers() {
        return java.util.Collections.unmodifiableSet(handlers.keySet());
    }

    @Override
    public RewardType getType() {
        return RewardType.CUSTOM;
    }

    @Override
    public CompletableFuture<RewardDeliveryResult> deliver(UUID playerId, RewardDTO reward) {
        return CompletableFuture.supplyAsync(() -> {
            String handlerName = getMetadataString(reward, "handler");
            if (handlerName == null || handlerName.isBlank()) {
                return RewardDeliveryResult.failure(
                    reward,
                    "Missing handler name in metadata",
                    "NO_HANDLER"
                );
            }

            BiFunction<UUID, RewardDTO, RewardDeliveryResult> handler = 
                handlers.get(handlerName.toLowerCase());
            
            if (handler == null) {
                return RewardDeliveryResult.failure(
                    reward,
                    "Unknown custom handler: " + handlerName,
                    "UNKNOWN_HANDLER"
                );
            }

            try {
                return handler.apply(playerId, reward);
            } catch (Exception e) {
                return RewardDeliveryResult.failure(
                    reward,
                    "Custom handler error: " + e.getMessage(),
                    "HANDLER_ERROR"
                );
            }
        });
    }

    @Override
    public CompletableFuture<RewardValidationResult> validate(UUID playerId, RewardDTO reward) {
        return CompletableFuture.supplyAsync(() -> {
            String handlerName = getMetadataString(reward, "handler");
            
            if (handlerName == null || handlerName.isBlank()) {
                return RewardValidationResult.invalid(
                    reward,
                    "Missing required 'handler' in metadata",
                    "Specify custom handler name"
                );
            }

            if (!handlers.containsKey(handlerName.toLowerCase())) {
                return RewardValidationResult.invalid(
                    reward,
                    "Unknown handler: " + handlerName,
                    "Register handler first"
                );
            }

            // Check for custom validator
            BiFunction<UUID, RewardDTO, RewardValidationResult> validator =
                validators.get(handlerName.toLowerCase());
            
            if (validator != null) {
                try {
                    return validator.apply(playerId, reward);
                } catch (Exception e) {
                    return RewardValidationResult.invalid(
                        reward,
                        "Validation error: " + e.getMessage(),
                        "Fix validation error"
                    );
                }
            }

            // No custom validator - assume valid if handler exists
            return RewardValidationResult.valid(reward);
        });
    }

    @Override
    public boolean requiresOnlinePlayer() {
        return false; // Depends on handler
    }

    @Override
    public boolean supportsOfflineQueue() {
        return false; // Custom handlers typically require online player
    }

    @Override
    public int getPriority() {
        return 10; // Low priority - process after standard rewards
    }

    @Override
    public boolean isAvailable() {
        return true; // Always available
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
        String handlerName = getMetadataString(reward, "handler");
        if (handlerName == null) {
            return "Custom Reward (no handler)";
        }
        
        String description = getMetadataString(reward, "description");
        if (description != null) {
            return description;
        }
        
        return "Custom: " + handlerName;
    }
}
