package org.fourz.RVNKQuests.service.reward;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.data.dto.RewardType;
import org.fourz.RVNKQuests.service.IRewardService.RewardDeliveryResult;
import org.fourz.RVNKQuests.service.IRewardService.RewardValidationResult;
import org.fourz.RVNKQuests.service.RewardProcessor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Processor for Experience Point rewards.
 *
 * <p>Grants vanilla Minecraft XP to players. Supports both
 * experience points and experience levels.</p>
 *
 * <h2>Reward Configuration</h2>
 * <ul>
 *   <li>value: "points" or "levels" (default: points)</li>
 *   <li>amount: Number of XP points or levels</li>
 *   <li>metadata.mode: "points" | "levels" (alternative)</li>
 * </ul>
 *
 * @since 1.0
 */
public class ExperienceRewardProcessor implements RewardProcessor {

    private static final String MODE_LEVELS = "levels";
    private static final String MODE_POINTS = "points";

    @Override
    public RewardType getType() {
        return RewardType.EXPERIENCE;
    }

    @Override
    public CompletableFuture<RewardDeliveryResult> deliver(UUID playerId, RewardDTO reward) {
        return CompletableFuture.supplyAsync(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                return RewardDeliveryResult.failure(
                    reward,
                    "Player is offline",
                    "PLAYER_OFFLINE"
                );
            }

            String mode = getMode(reward);
            int amount = reward.amount();

            if (amount <= 0) {
                return RewardDeliveryResult.failure(
                    reward,
                    "Invalid experience amount: " + amount,
                    "INVALID_AMOUNT"
                );
            }

            try {
                if (MODE_LEVELS.equalsIgnoreCase(mode)) {
                    int oldLevel = player.getLevel();
                    player.setLevel(player.getLevel() + amount);
                    return RewardDeliveryResult.success(
                        reward,
                        "Granted " + amount + " experience levels",
                        Map.of(
                            "mode", "levels",
                            "amount", amount,
                            "oldLevel", oldLevel,
                            "newLevel", player.getLevel()
                        )
                    );
                } else {
                    player.giveExp(amount);
                    return RewardDeliveryResult.success(
                        reward,
                        "Granted " + amount + " experience points",
                        Map.of(
                            "mode", "points",
                            "amount", amount,
                            "totalExp", player.getTotalExperience()
                        )
                    );
                }
            } catch (Exception e) {
                return RewardDeliveryResult.failure(
                    reward,
                    "Failed to grant experience: " + e.getMessage(),
                    "DELIVERY_ERROR",
                    Map.of("exception", e.getClass().getSimpleName())
                );
            }
        });
    }

    @Override
    public CompletableFuture<RewardValidationResult> validate(UUID playerId, RewardDTO reward) {
        return CompletableFuture.supplyAsync(() -> {
            if (reward.type() != RewardType.EXPERIENCE) {
                return RewardValidationResult.invalid(
                    reward,
                    "Wrong reward type",
                    "Expected EXPERIENCE"
                );
            }

            if (reward.amount() <= 0) {
                return RewardValidationResult.invalid(
                    reward,
                    "Experience amount must be positive",
                    "amount > 0"
                );
            }

            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                return RewardValidationResult.invalid(
                    reward,
                    "Player must be online",
                    "Player online required"
                );
            }

            return RewardValidationResult.valid(reward);
        });
    }

    @Override
    public boolean requiresOnlinePlayer() {
        return true;
    }

    @Override
    public boolean supportsOfflineQueue() {
        return true; // XP can be queued for offline players
    }

    @Override
    public int getPriority() {
        return 50; // Higher priority - XP is quick to deliver
    }

    @Override
    public String getDisplayName() {
        return "Experience";
    }

    @Override
    public String formatReward(RewardDTO reward) {
        String mode = getMode(reward);
        if (MODE_LEVELS.equalsIgnoreCase(mode)) {
            return reward.amount() + " Experience Level" + (reward.amount() > 1 ? "s" : "");
        }
        return reward.amount() + " Experience Points";
    }

    /**
     * Get the experience mode from reward configuration.
     *
     * @param reward The reward DTO
     * @return "points" or "levels"
     */
    private String getMode(RewardDTO reward) {
        // Check value first
        if (MODE_LEVELS.equalsIgnoreCase(reward.value())) {
            return MODE_LEVELS;
        }
        // Check metadata
        String metaMode = reward.metadata().get("mode");
        if (MODE_LEVELS.equalsIgnoreCase(metaMode)) {
            return MODE_LEVELS;
        }
        return MODE_POINTS;
    }
}
