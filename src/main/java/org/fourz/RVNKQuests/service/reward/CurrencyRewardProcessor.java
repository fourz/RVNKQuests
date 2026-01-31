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
 * Processor for Currency/Token rewards.
 *
 * <p>Integrates with TokenEconomy plugin or other economy systems
 * to grant currency to players.</p>
 *
 * <h2>Reward Configuration</h2>
 * <ul>
 *   <li>value: Currency type (default: "tokens")</li>
 *   <li>amount: Amount to grant</li>
 *   <li>metadata.economy: Economy plugin name (default: "TokenEconomy")</li>
 * </ul>
 *
 * <h2>Integration</h2>
 * <p>Currently uses command-based integration via TokenEconomy's /tokens add command.
 * Future versions may support direct API integration.</p>
 *
 * @since 1.0
 */
public class CurrencyRewardProcessor implements RewardProcessor {

    private static final String DEFAULT_CURRENCY = "tokens";
    private static final String DEFAULT_ECONOMY = "TokenEconomy";

    // Cache economy availability check
    private volatile Boolean economyAvailable = null;
    private long lastCheck = 0;
    private static final long CHECK_INTERVAL = 60000; // 1 minute

    @Override
    public RewardType getType() {
        return RewardType.CURRENCY;
    }

    @Override
    public CompletableFuture<RewardDeliveryResult> deliver(UUID playerId, RewardDTO reward) {
        return CompletableFuture.supplyAsync(() -> {
            if (!checkEconomyAvailable()) {
                return RewardDeliveryResult.failure(
                    reward,
                    "Economy plugin not available",
                    "ECONOMY_UNAVAILABLE"
                );
            }

            Player player = Bukkit.getPlayer(playerId);
            String playerName = player != null ? player.getName() : getOfflinePlayerName(playerId);

            if (playerName == null) {
                return RewardDeliveryResult.failure(
                    reward,
                    "Could not resolve player name",
                    "PLAYER_NOT_FOUND"
                );
            }

            int amount = reward.amount();
            if (amount <= 0) {
                return RewardDeliveryResult.failure(
                    reward,
                    "Invalid currency amount: " + amount,
                    "INVALID_AMOUNT"
                );
            }

            String currency = reward.value() != null ? reward.value() : DEFAULT_CURRENCY;

            try {
                // Use command-based integration
                String command = buildCurrencyCommand(playerName, amount, currency, reward);
                
                // Execute on main thread
                Bukkit.getScheduler().runTask(
                    Bukkit.getPluginManager().getPlugins()[0],
                    () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                );

                return RewardDeliveryResult.success(
                    reward,
                    String.format("Granted %d %s to %s", amount, currency, playerName),
                    Map.of(
                        "amount", amount,
                        "currency", currency,
                        "playerName", playerName,
                        "command", command
                    )
                );
            } catch (Exception e) {
                return RewardDeliveryResult.failure(
                    reward,
                    "Failed to grant currency: " + e.getMessage(),
                    "DELIVERY_ERROR",
                    Map.of("exception", e.getClass().getSimpleName())
                );
            }
        });
    }

    @Override
    public CompletableFuture<RewardValidationResult> validate(UUID playerId, RewardDTO reward) {
        return CompletableFuture.supplyAsync(() -> {
            if (reward.type() != RewardType.CURRENCY) {
                return RewardValidationResult.invalid(
                    reward,
                    "Wrong reward type",
                    "Expected CURRENCY"
                );
            }

            if (reward.amount() <= 0) {
                return RewardValidationResult.invalid(
                    reward,
                    "Currency amount must be positive",
                    "amount > 0"
                );
            }

            if (!checkEconomyAvailable()) {
                return RewardValidationResult.invalid(
                    reward,
                    "Economy plugin not available",
                    "TokenEconomy or compatible economy plugin required"
                );
            }

            return RewardValidationResult.valid(reward);
        });
    }

    @Override
    public boolean requiresOnlinePlayer() {
        return false; // Currency can be added to offline players
    }

    @Override
    public boolean supportsOfflineQueue() {
        return true;
    }

    @Override
    public int getPriority() {
        return 40; // Medium priority
    }

    @Override
    public boolean isAvailable() {
        return checkEconomyAvailable();
    }

    @Override
    public String getDisplayName() {
        return "Currency";
    }

    @Override
    public String formatReward(RewardDTO reward) {
        String currency = reward.value() != null ? reward.value() : DEFAULT_CURRENCY;
        return reward.amount() + " " + capitalize(currency);
    }

    /**
     * Check if economy plugin is available.
     *
     * @return true if economy is available
     */
    private boolean checkEconomyAvailable() {
        long now = System.currentTimeMillis();
        if (economyAvailable != null && (now - lastCheck) < CHECK_INTERVAL) {
            return economyAvailable;
        }

        // Check for TokenEconomy
        economyAvailable = Bukkit.getPluginManager().isPluginEnabled("TokenEconomy");
        
        // Could also check for Vault here for broader compatibility
        if (!economyAvailable) {
            economyAvailable = Bukkit.getPluginManager().isPluginEnabled("Vault");
        }
        
        lastCheck = now;
        return economyAvailable;
    }

    /**
     * Build the command to grant currency.
     *
     * @param playerName The player name
     * @param amount The amount
     * @param currency The currency type
     * @param reward The reward DTO for additional config
     * @return The command string
     */
    private String buildCurrencyCommand(String playerName, int amount, String currency, RewardDTO reward) {
        String economy = reward.metadata().get("economy");
        if (economy == null) {
            economy = DEFAULT_ECONOMY;
        }

        // TokenEconomy command format
        if ("TokenEconomy".equalsIgnoreCase(economy)) {
            return String.format("tokens add %s %d", playerName, amount);
        }

        // Vault/EssentialsX format
        if ("Vault".equalsIgnoreCase(economy) || "Essentials".equalsIgnoreCase(economy)) {
            return String.format("eco give %s %d", playerName, amount);
        }

        // Default to TokenEconomy format
        return String.format("tokens add %s %d", playerName, amount);
    }

    /**
     * Get offline player name from UUID.
     *
     * @param playerId The player's UUID
     * @return The player name, or null if not found
     */
    private String getOfflinePlayerName(UUID playerId) {
        var offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        return offlinePlayer.getName();
    }

    /**
     * Capitalize first letter of string.
     *
     * @param str The string
     * @return Capitalized string
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
