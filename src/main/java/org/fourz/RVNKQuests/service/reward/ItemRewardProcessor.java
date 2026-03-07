package org.fourz.RVNKQuests.service.reward;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.data.dto.RewardType;
import org.fourz.RVNKQuests.service.IRewardService.RewardDeliveryResult;
import org.fourz.RVNKQuests.service.IRewardService.RewardValidationResult;
import org.fourz.RVNKQuests.service.RewardProcessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Processor for Item rewards.
 *
 * <p>Delivers physical items to player inventories. Supports
 * material types, custom display names, and item overflow handling.</p>
 *
 * <h2>Reward Configuration</h2>
 * <ul>
 *   <li>value: Material name (e.g., "DIAMOND", "IRON_SWORD")</li>
 *   <li>amount: Number of items to give</li>
 *   <li>metadata.displayName: Custom display name</li>
 *   <li>metadata.lore: Custom lore lines (comma-separated)</li>
 *   <li>metadata.dropOnFull: "true" to drop if inventory full (default: true)</li>
 * </ul>
 *
 * @since 1.0
 */
public class ItemRewardProcessor implements RewardProcessor {

    @Override
    public RewardType getType() {
        return RewardType.ITEM;
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

            // Parse material
            Material material = parseMaterial(reward.value());
            if (material == null || material == Material.AIR) {
                return RewardDeliveryResult.failure(
                    reward,
                    "Invalid item type: " + reward.value(),
                    "INVALID_MATERIAL"
                );
            }

            int amount = reward.amount();
            if (amount <= 0) {
                return RewardDeliveryResult.failure(
                    reward,
                    "Invalid item amount: " + amount,
                    "INVALID_AMOUNT"
                );
            }

            try {
                // Create item stack
                ItemStack itemStack = createItemStack(material, amount, reward);

                // Try to add to inventory
                PlayerInventory inventory = player.getInventory();
                HashMap<Integer, ItemStack> leftover = inventory.addItem(itemStack);

                int delivered = amount;
                int dropped = 0;

                // Handle overflow
                if (!leftover.isEmpty()) {
                    boolean dropOnFull = shouldDropOnFull(reward);
                    
                    for (ItemStack overflow : leftover.values()) {
                        int overflowAmount = overflow.getAmount();
                        delivered -= overflowAmount;
                        
                        if (dropOnFull) {
                            // Drop at player's location
                            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
                            dropped += overflowAmount;
                            delivered += overflowAmount; // Count dropped as delivered
                        }
                    }
                }

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("material", material.name());
                metadata.put("requestedAmount", amount);
                metadata.put("deliveredToInventory", delivered - dropped);
                metadata.put("droppedOnGround", dropped);

                if (dropped > 0) {
                    return RewardDeliveryResult.success(
                        reward,
                        String.format("Delivered %d %s (%d dropped due to full inventory)",
                            delivered, formatMaterial(material), dropped),
                        metadata
                    );
                }

                return RewardDeliveryResult.success(
                    reward,
                    String.format("Delivered %d %s", delivered, formatMaterial(material)),
                    metadata
                );

            } catch (Exception e) {
                return RewardDeliveryResult.failure(
                    reward,
                    "Failed to deliver item: " + e.getMessage(),
                    "DELIVERY_ERROR",
                    Map.of("exception", e.getClass().getSimpleName())
                );
            }
        });
    }

    @Override
    public CompletableFuture<RewardValidationResult> validate(UUID playerId, RewardDTO reward) {
        return CompletableFuture.supplyAsync(() -> {
            if (reward.type() != RewardType.ITEM) {
                return RewardValidationResult.invalid(
                    reward,
                    "Wrong reward type",
                    "Expected ITEM"
                );
            }

            Material material = parseMaterial(reward.value());
            if (material == null || material == Material.AIR) {
                return RewardValidationResult.invalid(
                    reward,
                    "Invalid material: " + reward.value(),
                    "Valid Bukkit Material required"
                );
            }

            if (reward.amount() <= 0) {
                return RewardValidationResult.invalid(
                    reward,
                    "Item amount must be positive",
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

            // Check inventory space
            PlayerInventory inventory = player.getInventory();
            int emptySlots = 0;
            for (ItemStack item : inventory.getStorageContents()) {
                if (item == null || item.getType() == Material.AIR) {
                    emptySlots++;
                }
            }

            if (emptySlots == 0 && !shouldDropOnFull(reward)) {
                return RewardValidationResult.invalid(
                    reward,
                    "Inventory is full",
                    "At least 1 empty inventory slot"
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
        return true; // Items can be queued for offline players
    }

    @Override
    public int getPriority() {
        return 30; // Lower priority - inventory operations may be slow
    }

    @Override
    public String getDisplayName() {
        return "Item";
    }

    @Override
    public String formatReward(RewardDTO reward) {
        Material material = parseMaterial(reward.value());
        if (material != null) {
            return reward.amount() + "x " + formatMaterial(material);
        }
        return reward.amount() + "x " + reward.value();
    }

    /**
     * Parse a material from string.
     *
     * @param value The material name
     * @return The Material, or null if invalid
     */
    private Material parseMaterial(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Material.valueOf(value.toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            // Try with MINECRAFT namespace prefix
            try {
                return Material.valueOf("MINECRAFT_" + value.toUpperCase().replace(' ', '_'));
            } catch (IllegalArgumentException e2) {
                return null;
            }
        }
    }

    /**
     * Create an ItemStack from reward configuration.
     *
     * @param material The material
     * @param amount The amount
     * @param reward The reward DTO (for metadata)
     * @return The ItemStack
     */
    private ItemStack createItemStack(Material material, int amount, RewardDTO reward) {
        ItemStack itemStack = new ItemStack(material, amount);

        // Apply display name if specified
        String displayName = reward.metadata().get("displayName");
        if (displayName != null && !displayName.isEmpty()) {
            var meta = itemStack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(displayName);
                
                // Apply lore if specified
                String loreString = reward.metadata().get("lore");
                if (loreString != null && !loreString.isEmpty()) {
                    List<String> lore = new ArrayList<>();
                    for (String line : loreString.split(",")) {
                        lore.add(line.trim());
                    }
                    meta.setLore(lore);
                }
                
                itemStack.setItemMeta(meta);
            }
        }

        return itemStack;
    }

    /**
     * Check if items should be dropped when inventory is full.
     *
     * @param reward The reward DTO
     * @return true if should drop (default: true)
     */
    private boolean shouldDropOnFull(RewardDTO reward) {
        String dropOnFull = reward.metadata().get("dropOnFull");
        if (dropOnFull != null) {
            return Boolean.parseBoolean(dropOnFull);
        }
        return true; // Default: drop on ground
    }

    /**
     * Format a material name for display.
     *
     * @param material The material
     * @return Formatted name
     */
    private String formatMaterial(Material material) {
        String name = material.name().toLowerCase().replace('_', ' ');
        // Capitalize first letter of each word
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : name.toCharArray()) {
            if (c == ' ') {
                capitalizeNext = true;
                sb.append(c);
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
