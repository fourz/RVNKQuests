package org.fourz.RVNKQuests.service.reward;

import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.integration.ILoreIntegration;
import org.fourz.RVNKQuests.data.dto.RewardType;
import org.fourz.RVNKQuests.service.IRewardService.RewardDeliveryResult;
import org.fourz.RVNKQuests.service.IRewardService.RewardValidationResult;
import org.fourz.RVNKQuests.service.RewardProcessor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
     * RVNKLore integration, injected by {@code RewardServiceImpl.setLoreIntegration()} once the
     * plugin has resolved it (#1650).
     *
     * <p>This replaces a {@code BiConsumer<UUID,String> loreUnlockCallback} that had a setter and a
     * constructor but was <b>never assigned anywhere in the codebase</b>. With it null, delivery
     * fell to a branch that returned {@code success("... (pending integration)")} — reporting a
     * delivered reward while writing nothing. Every other lore-touching processor
     * (Item, RngItem, LoreItem) was already wired through setLoreIntegration; this one was simply
     * missed, and the miss was invisible because its failure mode was a false success.</p>
     *
     * <p>Using {@link ILoreIntegration} rather than a callback also lets delivery report the real
     * outcome: {@code grantLoreDiscovery} returns a future carrying whether the row was written.</p>
     */
    private ILoreIntegration loreIntegration;

    /**
     * Flag indicating if RVNKLore is on the server at all.
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
     * Create with the lore integration already resolved.
     */
    public LoreRewardProcessor(ILoreIntegration loreIntegration) {
        this.loreIntegration = loreIntegration;
        this.loreAvailable = true;
    }

    /** Injected after RVNKLore resolves — see {@code RewardServiceImpl.setLoreIntegration()}. */
    public void setLoreIntegration(ILoreIntegration loreIntegration) {
        this.loreIntegration = loreIntegration;
        if (loreIntegration != null) {
            this.loreAvailable = true;
        }
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
                if (loreIntegration == null || !loreIntegration.isLoreAvailable()) {
                    // Report FAILURE, not success. The previous code returned
                    // success("... (pending integration)") here, which is how a reward that
                    // delivered nothing looked delivered in every log and every test (#1650).
                    return RewardDeliveryResult.failure(
                        reward,
                        "Lore integration not wired - discovery not recorded",
                        "LORE_UNAVAILABLE"
                    );
                }

                // Blocking join is safe: deliver() already runs inside supplyAsync.
                boolean granted = Boolean.TRUE.equals(
                        loreIntegration.grantLoreDiscovery(playerId, loreId).join());
                if (!granted) {
                    return RewardDeliveryResult.failure(
                        reward,
                        "Lore discovery was not recorded for " + loreId,
                        "LORE_NOT_RECORDED"
                    );
                }

                String category = getMetadataString(reward, "category");
                String message = category != null
                    ? String.format("Unlocked lore entry '%s' in category '%s'", loreId, category)
                    : String.format("Unlocked lore entry: %s", loreId);
                return RewardDeliveryResult.success(reward, message);
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
