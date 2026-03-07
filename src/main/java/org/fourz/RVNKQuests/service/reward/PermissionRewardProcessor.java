package org.fourz.RVNKQuests.service.reward;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.data.dto.RewardType;
import org.fourz.RVNKQuests.service.IRewardService.RewardDeliveryResult;
import org.fourz.RVNKQuests.service.IRewardService.RewardValidationResult;
import org.fourz.RVNKQuests.service.RewardProcessor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Reward processor for permission-based rewards.
 *
 * <p>Grants permissions to players using command execution
 * (compatible with LuckPerms and other permission plugins).</p>
 *
 * <h2>Metadata Keys</h2>
 * <ul>
 *   <li>{@code permission} - The permission node to grant (required)</li>
 *   <li>{@code duration} - Duration in seconds (optional, 0 for permanent)</li>
 *   <li>{@code server} - Server context (optional)</li>
 *   <li>{@code world} - World context (optional)</li>
 *   <li>{@code temporary} - Whether this is a temporary permission</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * RewardDTO.builder()
 *     .id("vip_access")
 *     .type(RewardType.PERMISSION)
 *     .name("VIP Access")
 *     .metadata(Map.of(
 *         "permission", "group.vip",
 *         "duration", "604800"  // 7 days in seconds
 *     ))
 *     .build()
 * }</pre>
 *
 * @since 1.0
 */
public class PermissionRewardProcessor implements RewardProcessor {

    @Override
    public RewardType getType() {
        return RewardType.PERMISSION;
    }

    @Override
    public CompletableFuture<RewardDeliveryResult> deliver(UUID playerId, RewardDTO reward) {
        return CompletableFuture.supplyAsync(() -> {
            String permission = getMetadataString(reward, "permission");
            if (permission == null || permission.isBlank()) {
                return RewardDeliveryResult.failure(
                    reward,
                    "Missing permission node in metadata",
                    "INVALID_PERMISSION"
                );
            }

            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                return RewardDeliveryResult.failure(
                    reward,
                    "Player must be online for permission rewards",
                    "PLAYER_OFFLINE"
                );
            }

            // Check for duration (temporary permission)
            String durationStr = getMetadataString(reward, "duration");
            long duration = 0;
            if (durationStr != null && !durationStr.isEmpty()) {
                try {
                    duration = Long.parseLong(durationStr);
                } catch (NumberFormatException e) {
                    return RewardDeliveryResult.failure(
                        reward,
                        "Invalid duration format: " + durationStr,
                        "INVALID_DURATION"
                    );
                }
            }

            // Build LuckPerms command
            String command = buildPermissionCommand(player.getName(), permission, duration, reward);

            try {
                // Execute on main thread
                final String finalCommand = command;
                CompletableFuture<Boolean> commandResult = new CompletableFuture<>();
                
                Bukkit.getScheduler().runTask(
                    Bukkit.getPluginManager().getPlugins()[0],
                    () -> {
                        boolean success = Bukkit.dispatchCommand(
                            Bukkit.getConsoleSender(),
                            finalCommand
                        );
                        commandResult.complete(success);
                    }
                );

                // Wait for command to complete
                boolean success = commandResult.join();

                if (success) {
                    String message = duration > 0 
                        ? String.format("Granted permission '%s' for %d seconds", permission, duration)
                        : String.format("Granted permission '%s' permanently", permission);
                    return RewardDeliveryResult.success(reward, message);
                } else {
                    return RewardDeliveryResult.failure(
                        reward,
                        "Permission command failed",
                        "COMMAND_FAILED"
                    );
                }
            } catch (Exception e) {
                return RewardDeliveryResult.failure(
                    reward,
                    "Error granting permission: " + e.getMessage(),
                    "PERMISSION_ERROR"
                );
            }
        });
    }

    /**
     * Build the permission command for LuckPerms.
     */
    private String buildPermissionCommand(String playerName, String permission, long duration, RewardDTO reward) {
        StringBuilder cmd = new StringBuilder();
        
        // Check if it's a group assignment
        if (permission.startsWith("group.")) {
            String group = permission.substring(6);
            cmd.append("lp user ").append(playerName).append(" parent add ").append(group);
        } else {
            cmd.append("lp user ").append(playerName).append(" permission set ").append(permission);
        }

        // Add duration if temporary
        if (duration > 0) {
            cmd.append(" ").append(duration).append("s");
        }

        // Add world context if specified
        String world = getMetadataString(reward, "world");
        if (world != null && !world.isEmpty()) {
            cmd.append(" world=").append(world);
        }

        // Add server context if specified
        String server = getMetadataString(reward, "server");
        if (server != null && !server.isEmpty()) {
            cmd.append(" server=").append(server);
        }

        return cmd.toString();
    }

    @Override
    public CompletableFuture<RewardValidationResult> validate(UUID playerId, RewardDTO reward) {
        String permission = getMetadataString(reward, "permission");
        
        if (permission == null || permission.isBlank()) {
            return CompletableFuture.completedFuture(
                RewardValidationResult.invalid(
                    reward,
                    "Missing required 'permission' in metadata",
                    "Specify permission node"
                )
            );
        }

        // Validate permission format (basic check)
        if (!permission.matches("^[a-zA-Z0-9._-]+$")) {
            return CompletableFuture.completedFuture(
                RewardValidationResult.invalid(
                    reward,
                    "Invalid permission format: " + permission,
                    "Use alphanumeric with . _ - only"
                )
            );
        }

        // Check duration format if provided
        String durationStr = getMetadataString(reward, "duration");
        if (durationStr != null && !durationStr.isEmpty()) {
            try {
                long duration = Long.parseLong(durationStr);
                if (duration < 0) {
                    return CompletableFuture.completedFuture(
                        RewardValidationResult.invalid(
                            reward,
                            "Duration cannot be negative",
                            "Use positive duration or 0 for permanent"
                        )
                    );
                }
            } catch (NumberFormatException e) {
                return CompletableFuture.completedFuture(
                    RewardValidationResult.invalid(
                        reward,
                        "Invalid duration format: " + durationStr,
                        "Use number of seconds"
                    )
                );
            }
        }

        return CompletableFuture.completedFuture(
            RewardValidationResult.valid(reward)
        );
    }

    @Override
    public boolean requiresOnlinePlayer() {
        return true;
    }

    @Override
    public boolean supportsOfflineQueue() {
        return true; // Can queue for when player comes online
    }

    @Override
    public int getPriority() {
        return 60; // Medium priority
    }

    @Override
    public boolean isAvailable() {
        // Check if LuckPerms is installed
        return Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
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
        String permission = getMetadataString(reward, "permission");
        String durationStr = getMetadataString(reward, "duration");
        
        if (permission == null) {
            return "Permission Reward (unspecified)";
        }

        if (durationStr != null && !durationStr.isEmpty()) {
            try {
                long duration = Long.parseLong(durationStr);
                if (duration > 0) {
                    return String.format("Permission: %s (for %s)", 
                        permission, formatDuration(duration));
                }
            } catch (NumberFormatException ignored) {}
        }

        return "Permission: " + permission + " (permanent)";
    }

    /**
     * Format duration in human-readable form.
     */
    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m";
        } else if (seconds < 86400) {
            return (seconds / 3600) + "h";
        } else {
            return (seconds / 86400) + "d";
        }
    }
}
