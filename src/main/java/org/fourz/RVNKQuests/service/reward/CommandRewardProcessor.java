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
 * Processor for Command execution rewards.
 *
 * <p>Executes server commands as rewards. Supports placeholder
 * substitution for player-specific commands.</p>
 *
 * <h2>Reward Configuration</h2>
 * <ul>
 *   <li>value: The command to execute (without leading /)</li>
 *   <li>metadata.asConsole: "true" to run as console (default: true)</li>
 *   <li>metadata.asPlayer: "true" to run as player</li>
 * </ul>
 *
 * <h2>Placeholders</h2>
 * <ul>
 *   <li>{player} - Player name</li>
 *   <li>{uuid} - Player UUID</li>
 *   <li>{amount} - Reward amount</li>
 * </ul>
 *
 * <h2>Security Notes</h2>
 * <p>Commands are executed by the server console by default.
 * Only trusted commands should be configured as rewards.</p>
 *
 * @since 1.0
 */
public class CommandRewardProcessor implements RewardProcessor {

    @Override
    public RewardType getType() {
        return RewardType.COMMAND;
    }

    @Override
    public CompletableFuture<RewardDeliveryResult> deliver(UUID playerId, RewardDTO reward) {
        return CompletableFuture.supplyAsync(() -> {
            String command = reward.value();
            if (command == null || command.isEmpty()) {
                return RewardDeliveryResult.failure(
                    reward,
                    "No command specified",
                    "EMPTY_COMMAND"
                );
            }

            Player player = Bukkit.getPlayer(playerId);
            String playerName = player != null ? player.getName() : "Unknown";

            // Apply placeholders
            String processedCommand = applyPlaceholders(command, playerId, playerName, reward);

            // Remove leading slash if present
            if (processedCommand.startsWith("/")) {
                processedCommand = processedCommand.substring(1);
            }

            try {
                boolean asPlayer = "true".equalsIgnoreCase(reward.metadata().get("asPlayer"));
                final String finalCommand = processedCommand;

                if (asPlayer && player != null) {
                    // Execute as player (on main thread)
                    Bukkit.getScheduler().runTask(
                        Bukkit.getPluginManager().getPlugins()[0],
                        () -> player.performCommand(finalCommand)
                    );
                    return RewardDeliveryResult.success(
                        reward,
                        "Executed command as player: " + finalCommand,
                        Map.of(
                            "command", finalCommand,
                            "executor", "player",
                            "playerName", playerName
                        )
                    );
                } else {
                    // Execute as console (on main thread)
                    Bukkit.getScheduler().runTask(
                        Bukkit.getPluginManager().getPlugins()[0],
                        () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand)
                    );
                    return RewardDeliveryResult.success(
                        reward,
                        "Executed command as console: " + finalCommand,
                        Map.of(
                            "command", finalCommand,
                            "executor", "console",
                            "targetPlayer", playerName
                        )
                    );
                }
            } catch (Exception e) {
                return RewardDeliveryResult.failure(
                    reward,
                    "Failed to execute command: " + e.getMessage(),
                    "COMMAND_ERROR",
                    Map.of("exception", e.getClass().getSimpleName())
                );
            }
        });
    }

    @Override
    public CompletableFuture<RewardValidationResult> validate(UUID playerId, RewardDTO reward) {
        return CompletableFuture.supplyAsync(() -> {
            if (reward.type() != RewardType.COMMAND) {
                return RewardValidationResult.invalid(
                    reward,
                    "Wrong reward type",
                    "Expected COMMAND"
                );
            }

            if (reward.value() == null || reward.value().isEmpty()) {
                return RewardValidationResult.invalid(
                    reward,
                    "Command cannot be empty",
                    "Non-empty command string required"
                );
            }

            // Check for dangerous commands (basic safety)
            String cmd = reward.value().toLowerCase();
            if (cmd.contains("op ") || cmd.startsWith("op") ||
                cmd.contains("deop ") || cmd.startsWith("deop") ||
                cmd.contains("stop") || cmd.contains("restart")) {
                return RewardValidationResult.invalid(
                    reward,
                    "Potentially dangerous command",
                    "Command may be unsafe for reward execution"
                );
            }

            boolean asPlayer = "true".equalsIgnoreCase(reward.metadata().get("asPlayer"));
            if (asPlayer) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) {
                    return RewardValidationResult.invalid(
                        reward,
                        "Player must be online for player-executed commands",
                        "Player online required"
                    );
                }
            }

            return RewardValidationResult.valid(reward);
        });
    }

    @Override
    public boolean requiresOnlinePlayer() {
        return false; // Console commands can run with player UUID/name
    }

    @Override
    public boolean supportsOfflineQueue() {
        return true; // Commands can be queued
    }

    @Override
    public int getPriority() {
        return 10; // Lower priority - commands should run after items/xp
    }

    @Override
    public String getDisplayName() {
        return "Command";
    }

    @Override
    public String formatReward(RewardDTO reward) {
        if (reward.description() != null && !reward.description().isEmpty()) {
            return reward.description();
        }
        // Don't expose actual command in player-visible messages
        return "Special Reward";
    }

    /**
     * Apply placeholders to command string.
     *
     * @param command The command template
     * @param playerId The player's UUID
     * @param playerName The player's name
     * @param reward The reward DTO
     * @return Command with placeholders replaced
     */
    private String applyPlaceholders(String command, UUID playerId, String playerName, RewardDTO reward) {
        return command
            .replace("{player}", playerName)
            .replace("{uuid}", playerId.toString())
            .replace("{amount}", String.valueOf(reward.amount()))
            .replace("%player%", playerName)
            .replace("%uuid%", playerId.toString())
            .replace("%amount%", String.valueOf(reward.amount()));
    }
}
