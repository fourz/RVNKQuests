package org.fourz.RVNKQuests.service.reward;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.data.dto.RewardType;
import org.fourz.RVNKQuests.integration.ILoreIntegration;
import org.fourz.RVNKQuests.service.IRewardService.RewardDeliveryResult;
import org.fourz.RVNKQuests.service.IRewardService.RewardValidationResult;
import org.fourz.RVNKQuests.service.RewardProcessor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Delivers RNG_ITEM rewards — rolls a rarity-weighted random lore item from a pool.
 *
 * <h2>Reward Metadata Schema</h2>
 * <ul>
 *   <li>{@code pool_id} (required): lore_item_rng_pool.pool_id to draw from</li>
 *   <li>{@code rarity_tier} (optional): COMMON, UNCOMMON, RARE, EPIC, LEGENDARY — filters pool entries</li>
 *   <li>{@code dropOnFull} (optional, default true): drop rolled item if inventory full</li>
 * </ul>
 */
public class RngItemRewardProcessor implements RewardProcessor {

    private ILoreIntegration loreIntegration;

    public RngItemRewardProcessor() {
        this(null);
    }

    public RngItemRewardProcessor(ILoreIntegration loreIntegration) {
        this.loreIntegration = loreIntegration;
    }

    public void setLoreIntegration(ILoreIntegration loreIntegration) {
        this.loreIntegration = loreIntegration;
    }

    @Override
    public RewardType getType() {
        return RewardType.RNG_ITEM;
    }

    @Override
    public CompletableFuture<RewardDeliveryResult> deliver(UUID playerId, RewardDTO reward) {
        return CompletableFuture.supplyAsync(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                return RewardDeliveryResult.failure(reward, "Player is offline", "PLAYER_OFFLINE");
            }

            if (loreIntegration == null || !loreIntegration.isLoreAvailable()) {
                return RewardDeliveryResult.failure(reward,
                    "RVNKLore unavailable - cannot deliver RNG item", "LORE_UNAVAILABLE");
            }

            String poolId = reward.metadata().get("pool_id");
            if (poolId == null || poolId.isEmpty()) {
                return RewardDeliveryResult.failure(reward,
                    "RNG_ITEM reward requires metadata.pool_id", "MISSING_POOL_ID");
            }

            String rarityTier = reward.metadata().get("rarity_tier"); // nullable

            Optional<ItemStack> rolled = loreIntegration.rollRngItem(poolId, rarityTier).join();
            if (rolled.isEmpty()) {
                return RewardDeliveryResult.failure(reward,
                    "RNG pool returned no item - pool '" + poolId + "' may be empty", "POOL_EMPTY");
            }

            ItemStack item = rolled.get();
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            int dropped = 0;

            if (!leftover.isEmpty() && shouldDropOnFull(reward)) {
                for (ItemStack overflow : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), overflow);
                    dropped++;
                }
            }

            Map<String, Object> meta = new HashMap<>();
            meta.put("poolId", poolId);
            meta.put("rarityTier", rarityTier != null ? rarityTier : "ANY");
            meta.put("droppedOnGround", dropped);

            return RewardDeliveryResult.success(reward,
                String.format("Rolled RNG item from pool '%s' (tier: %s)",
                    poolId, rarityTier != null ? rarityTier : "ANY"),
                meta);
        });
    }

    @Override
    public CompletableFuture<RewardValidationResult> validate(UUID playerId, RewardDTO reward) {
        return CompletableFuture.supplyAsync(() -> {
            if (reward.type() != RewardType.RNG_ITEM) {
                return RewardValidationResult.invalid(reward, "Wrong reward type", "Expected RNG_ITEM");
            }
            String poolId = reward.metadata().get("pool_id");
            if (poolId == null || poolId.isEmpty()) {
                return RewardValidationResult.invalid(reward,
                    "pool_id metadata is required", "metadata.pool_id must be set");
            }
            if (loreIntegration == null || !loreIntegration.isLoreAvailable()) {
                return RewardValidationResult.invalid(reward,
                    "RVNKLore unavailable", "RVNKLore plugin must be active");
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
        return false;
    }

    @Override
    public int getPriority() {
        return 25;
    }

    @Override
    public String getDisplayName() {
        return "RNG Item";
    }

    @Override
    public String formatReward(RewardDTO reward) {
        String poolId = reward.metadata().getOrDefault("pool_id", "unknown");
        String tier = reward.metadata().get("rarity_tier");
        return tier != null ? tier + " item from pool '" + poolId + "'" : "Random item from pool '" + poolId + "'";
    }

    private boolean shouldDropOnFull(RewardDTO reward) {
        String v = reward.metadata().get("dropOnFull");
        return v == null || Boolean.parseBoolean(v);
    }
}
