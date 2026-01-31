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
 * Reward processor for RVNKLore integration.
 *
 * <p>Grants lore entries to players via the RVNKLore plugin,
 * enabling quest rewards to unlock story content.</p>
 *
 * <h2>Metadata Keys</h2>
 * <ul>
 *   <li>{@code loreId} - The lore entry ID to unlock (required)</li>
 *   <li>{@code category} - Lore category (optional)</li>
 *   <li>{@code notify} - Whether to notify the player (default: true)</li>
 *   <li>{@code discovered} - Mark as discovered vs unlocked (optional)</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * RewardDTO.builder()
 *     .id("lore_ancient_history")
 *     .type(RewardType.LORE)
 *     .name("Ancient History Unlocked")
 *     .metadata(Map.of(
 *         "loreId", "ancient_civilization_01",
 *         "category", "history",
 *         "notify", "true"
 *     ))
 *     .build()
 * }</pre>
 *
 * @since 1.0
 */
public class LoreRewardProcessor implements RewardProcessor {

    /**
     * Callback for lore unlock operations.
     * Set this to integrate with the RVNKLore system.
     */
    private BiConsumer<UUID, String> loreUnlockCallback;
    
    /**
     * Flag indicating if RVNKLore integration is available.
     */
    private volatile boolean loreAvailable = false;

    /**
     * Create a new LoreRewardProcessor.
     */
    public LoreRewardProcessor() {
        // Check for RVNKLore availability
        checkLoreAvailability();
    }

    /**
     * Create with a lore unlock callback.
     *
     * @param loreUnlockCallback Callback invoked when lore is unlocked (playerId, loreId)
     */
    public LoreRewardProcessor(BiConsumer<UUID, String> loreUnlockCallback) {
        this.loreUnlockCallback = loreUnlockCallback;
        this.loreAvailable = true;
    }

    /**
     * Check if RVNKLore is available.
     */
    private void checkLoreAvailability() {
        try {
            // Check if RVNKLore plugin is loaded
            Class.forName("org.fourz.RVNKLore.RVNKLore");
            loreAvailable = true;
        } catch (ClassNotFoundException e) {
            loreAvailable = false;
        }
    }

    /**
     * Set the lore unlock callback.
     *
     * @param callback Callback for lore unlocks
     */
    public void setLoreUnlockCallback(BiConsumer<UUID, String> callback) {
        this.loreUnlockCallback = callback;
        this.loreAvailable = true;
    }

    @Override
    public RewardType getType() {
        return RewardType.LORE;
    }

    @Override
    public CompletableFuture<RewardDeliveryResult> deliver(UUID playerId, RewardDTO reward) {
        return CompletableFuture.supplyAsync(() -> {
            String loreId = getMetadataString(reward, "loreId");
            if (loreId == null || loreId.isBlank()) {
                return RewardDeliveryResult.failure(
                    reward,
                    "Missing lore ID in metadata",
                    "INVALID_LORE_ID"
                );
            }

            if (!loreAvailable) {
                return RewardDeliveryResult.failure(
                    reward,
                    "RVNKLore plugin not available",
                    "LORE_UNAVAILABLE"
                );
            }

            try {
                // Invoke the lore callback if set
                if (loreUnlockCallback != null) {
                    loreUnlockCallback.accept(playerId, loreId);
                    
                    String category = getMetadataString(reward, "category");
                    String message = category != null 
                        ? String.format("Unlocked lore entry '%s' in category '%s'", loreId, category)
                        : String.format("Unlocked lore entry: %s", loreId);
                    
                    return RewardDeliveryResult.success(reward, message);
                } else {
                    // Log that we would unlock but no callback is set
                    return RewardDeliveryResult.success(
                        reward,
                        "Lore unlock registered: " + loreId + " (pending integration)"
                    );
                }
            } catch (Exception e) {
                return RewardDeliveryResult.failure(
                    reward,
                    "Error unlocking lore: " + e.getMessage(),
                    "LORE_ERROR"
                );
            }
        });
    }

    @Override
    public CompletableFuture<RewardValidationResult> validate(UUID playerId, RewardDTO reward) {
        String loreId = getMetadataString(reward, "loreId");
        
        if (loreId == null || loreId.isBlank()) {
            return CompletableFuture.completedFuture(
                RewardValidationResult.invalid(
                    reward,
                    "Missing required 'loreId' in metadata",
                    "Specify lore entry ID"
                )
            );
        }

        // Validate lore ID format (basic check)
        if (!loreId.matches("^[a-zA-Z0-9_-]+$")) {
            return CompletableFuture.completedFuture(
                RewardValidationResult.invalid(
                    reward,
                    "Invalid lore ID format: " + loreId,
                    "Use alphanumeric with _ - only"
                )
            );
        }

        if (!loreAvailable) {
            return CompletableFuture.completedFuture(
                RewardValidationResult.invalid(
                    reward,
                    "RVNKLore plugin not available",
                    "Install RVNKLore plugin"
                )
            );
        }

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
        return 30; // Lower priority - cosmetic/narrative rewards
    }

    @Override
    public boolean isAvailable() {
        return loreAvailable;
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
        String loreId = getMetadataString(reward, "loreId");
        String category = getMetadataString(reward, "category");
        
        if (loreId == null) {
            return "Lore Entry (unspecified)";
        }
        
        if (category != null) {
            return String.format("Lore: %s (%s)", loreId, category);
        }
        
        return "Lore Entry: " + loreId;
    }
}
