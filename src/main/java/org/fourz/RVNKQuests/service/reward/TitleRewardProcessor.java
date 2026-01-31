package org.fourz.RVNKQuests.service.reward;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
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
 * Processor for Title display rewards.
 *
 * <p>Displays titles/subtitles to players as visual rewards.
 * Useful for achievement notifications and quest milestones.</p>
 *
 * <h2>Reward Configuration</h2>
 * <ul>
 *   <li>value: The title text</li>
 *   <li>metadata.subtitle: Subtitle text (optional)</li>
 *   <li>metadata.fadeIn: Fade in ticks (default: 10)</li>
 *   <li>metadata.stay: Display duration ticks (default: 70)</li>
 *   <li>metadata.fadeOut: Fade out ticks (default: 20)</li>
 *   <li>metadata.sound: Sound effect name (optional)</li>
 * </ul>
 *
 * @since 1.0
 */
public class TitleRewardProcessor implements RewardProcessor {

    private static final int DEFAULT_FADE_IN = 10;   // 0.5 seconds
    private static final int DEFAULT_STAY = 70;      // 3.5 seconds
    private static final int DEFAULT_FADE_OUT = 20;  // 1 second

    @Override
    public RewardType getType() {
        return RewardType.TITLE;
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

            String title = reward.value();
            if (title == null || title.isEmpty()) {
                title = "";
            }

            String subtitle = reward.metadata().get("subtitle");
            if (subtitle == null) {
                subtitle = "";
            }

            // Parse timing values
            int fadeIn = parseIntOrDefault(reward.metadata().get("fadeIn"), DEFAULT_FADE_IN);
            int stay = parseIntOrDefault(reward.metadata().get("stay"), DEFAULT_STAY);
            int fadeOut = parseIntOrDefault(reward.metadata().get("fadeOut"), DEFAULT_FADE_OUT);

            try {
                // Apply color codes
                String finalTitle = applyColorCodes(title);
                String finalSubtitle = applyColorCodes(subtitle);

                // Send title (must be on main thread)
                Bukkit.getScheduler().runTask(
                    Bukkit.getPluginManager().getPlugins()[0],
                    () -> {
                        player.sendTitle(finalTitle, finalSubtitle, fadeIn, stay, fadeOut);
                        
                        // Play sound if configured
                        String soundName = reward.metadata().get("sound");
                        if (soundName != null && !soundName.isEmpty()) {
                            try {
                                Sound sound = Sound.valueOf(soundName.toUpperCase());
                                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                            } catch (IllegalArgumentException ignored) {
                                // Invalid sound name, skip
                            }
                        }
                    }
                );

                return RewardDeliveryResult.success(
                    reward,
                    "Displayed title: " + finalTitle,
                    Map.of(
                        "title", finalTitle,
                        "subtitle", finalSubtitle,
                        "fadeIn", fadeIn,
                        "stay", stay,
                        "fadeOut", fadeOut
                    )
                );
            } catch (Exception e) {
                return RewardDeliveryResult.failure(
                    reward,
                    "Failed to display title: " + e.getMessage(),
                    "DELIVERY_ERROR",
                    Map.of("exception", e.getClass().getSimpleName())
                );
            }
        });
    }

    @Override
    public CompletableFuture<RewardValidationResult> validate(UUID playerId, RewardDTO reward) {
        return CompletableFuture.supplyAsync(() -> {
            if (reward.type() != RewardType.TITLE) {
                return RewardValidationResult.invalid(
                    reward,
                    "Wrong reward type",
                    "Expected TITLE"
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

            // Validate sound if specified
            String soundName = reward.metadata().get("sound");
            if (soundName != null && !soundName.isEmpty()) {
                try {
                    Sound.valueOf(soundName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return RewardValidationResult.invalid(
                        reward,
                        "Invalid sound: " + soundName,
                        "Valid Bukkit Sound name required"
                    );
                }
            }

            return RewardValidationResult.valid(reward);
        });
    }

    @Override
    public boolean requiresOnlinePlayer() {
        return true; // Titles can only be shown to online players
    }

    @Override
    public boolean supportsOfflineQueue() {
        return false; // No point queueing visual rewards
    }

    @Override
    public int getPriority() {
        return 100; // High priority - visual feedback should be immediate
    }

    @Override
    public String getDisplayName() {
        return "Title";
    }

    @Override
    public String formatReward(RewardDTO reward) {
        if (reward.description() != null && !reward.description().isEmpty()) {
            return reward.description();
        }
        return "Achievement Unlocked";
    }

    /**
     * Parse an integer from string with default fallback.
     *
     * @param value The string value
     * @param defaultValue The default if parsing fails
     * @return The parsed integer or default
     */
    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Apply Minecraft color codes to text.
     *
     * @param text The text with & color codes
     * @return Text with § color codes
     */
    private String applyColorCodes(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "§");
    }
}
