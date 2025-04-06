package org.fourz.RVNKQuests.reward;

import org.bukkit.inventory.ItemStack;
import java.util.List;

/**
 * Interface for generating quest rewards and loot drops.
 * 
 * This functional interface provides a standardized way to generate
 * item rewards for quests. Implementations can use various strategies
 * including:
 * - Random loot tables
 * - Fixed reward sets
 * - Level-scaled rewards
 * - Condition-based rewards
 * 
 * Usage example:
 * QuestLoot basicLoot = () -> {
 *     List<ItemStack> items = new ArrayList<>();
 *     items.add(new ItemStack(Material.DIAMOND, 3));
 *     return items;
 * };
 */
@FunctionalInterface
public interface QuestLoot {
    /**
     * Generate loot items for a quest
     * @return List of ItemStacks to drop as rewards
     */
    List<ItemStack> generateLoot();
}
