package org.fourz.RVNKQuests.service.reward;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
 * Processor for {@link RewardType#LORE_ITEM} rewards — hands the player a specific
 * RVNKLore item (book, artifact, …) resolved by name.
 *
 * <p>Distinct from {@link LoreRewardProcessor} ({@link RewardType#LORE}), which unlocks
 * a lore <em>entry</em>: this delivers a physical {@link ItemStack}. It resolves the item
 * through {@link ILoreIntegration#spawnItemByName(String)} (backed by RVNKLore's item
 * service via {@code LoreServiceFacade}), so it needs only the single-token item name as
 * the reward value — sidestepping the reward-value tokenisation that blocks spaced
 * COMMAND give-books.</p>
 *
 * <h2>Reward Configuration</h2>
 * <ul>
 *   <li>value: RVNKLore item name (e.g. {@code "welcome_note_alphac"})</li>
 *   <li>amount: number of copies to give (default 1)</li>
 *   <li>metadata.dropOnFull: "false" to skip dropping overflow (default: true)</li>
 * </ul>
 *
 * @since 1.1
 */
public class LoreItemRewardProcessor implements RewardProcessor {

    private ILoreIntegration loreIntegration;

    public LoreItemRewardProcessor() {
        this(null);
    }

    public LoreItemRewardProcessor(ILoreIntegration loreIntegration) {
        this.loreIntegration = loreIntegration;
    }

    public void setLoreIntegration(ILoreIntegration loreIntegration) {
        this.loreIntegration = loreIntegration;
    }

    @Override
    public RewardType getType() {
        return RewardType.LORE_ITEM;
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
                        "RVNKLore unavailable - cannot deliver lore item", "LORE_UNAVAILABLE");
            }

            String itemName = reward.value();
            if (itemName == null || itemName.isEmpty()) {
                return RewardDeliveryResult.failure(reward,
                        "LORE_ITEM reward requires an item name value", "MISSING_ITEM_NAME");
            }

            int amount = Math.max(1, reward.amount());

            try {
                Optional<ItemStack> resolved = loreIntegration.spawnItemByName(itemName).join();
                if (resolved.isEmpty() || resolved.get() == null) {
                    return RewardDeliveryResult.failure(reward,
                            "Lore item not found: " + itemName, "LORE_ITEM_NOT_FOUND");
                }

                ItemStack template = resolved.get();
                PlayerInventory inventory = player.getInventory();
                boolean dropOnFull = shouldDropOnFull(reward);
                int delivered = 0;
                int dropped = 0;

                // Give one copy per requested amount — lore items (books, artifacts) are
                // typically non-stackable, so clone per copy rather than set the stack size.
                for (int i = 0; i < amount; i++) {
                    HashMap<Integer, ItemStack> leftover = inventory.addItem(template.clone());
                    if (leftover.isEmpty()) {
                        delivered++;
                    } else if (dropOnFull) {
                        for (ItemStack overflow : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
                        }
                        delivered++;
                        dropped++;
                    }
                    // else: inventory full and not dropping — remaining copies are skipped
                }

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("lore_item", itemName);
                metadata.put("requestedAmount", amount);
                metadata.put("delivered", delivered);
                metadata.put("droppedOnGround", dropped);

                if (delivered == 0) {
                    return RewardDeliveryResult.failure(reward,
                            "Inventory full - no copies of " + itemName + " delivered", "INVENTORY_FULL");
                }
                String note = dropped > 0
                        ? String.format("Delivered %d %s (%d dropped - inventory full)", delivered, itemName, dropped)
                        : String.format("Delivered %d %s", delivered, itemName);
                return RewardDeliveryResult.success(reward, note, metadata);

            } catch (Exception e) {
                return RewardDeliveryResult.failure(reward,
                        "Failed to deliver lore item: " + e.getMessage(), "DELIVERY_ERROR",
                        Map.of("exception", e.getClass().getSimpleName()));
            }
        });
    }

    @Override
    public CompletableFuture<RewardValidationResult> validate(UUID playerId, RewardDTO reward) {
        return CompletableFuture.supplyAsync(() -> {
            if (reward.type() != RewardType.LORE_ITEM) {
                return RewardValidationResult.invalid(reward, "Wrong reward type", "Expected LORE_ITEM");
            }
            if (reward.value() == null || reward.value().isEmpty()) {
                return RewardValidationResult.invalid(reward,
                        "LORE_ITEM requires an item name value", "Non-empty item name required");
            }
            if (loreIntegration == null || !loreIntegration.isLoreAvailable()) {
                return RewardValidationResult.invalid(reward,
                        "RVNKLore unavailable", "RVNKLore item service required");
            }
            if (Bukkit.getPlayer(playerId) == null) {
                return RewardValidationResult.invalid(reward, "Player must be online", "Player online required");
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
        return true; // can be queued and delivered when the player next comes online
    }

    @Override
    public int getPriority() {
        return 30;
    }

    @Override
    public String getDisplayName() {
        return "Lore Item";
    }

    @Override
    public String formatReward(RewardDTO reward) {
        int amount = Math.max(1, reward.amount());
        return amount + "x " + reward.value() + " (lore item)";
    }

    /** Whether overflow should be dropped when the inventory is full (default true). */
    private boolean shouldDropOnFull(RewardDTO reward) {
        String flag = reward.metadata().get("dropOnFull");
        return flag == null || !flag.equalsIgnoreCase("false");
    }
}
