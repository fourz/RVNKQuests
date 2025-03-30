package org.fourz.RVNKQuests.reward;

import org.bukkit.inventory.ItemStack;
import java.util.List;

/**
 * Interface for quest loot generation
 */
@FunctionalInterface
public interface QuestLoot {
    /**
     * Generate loot items for a quest
     * @return List of ItemStacks to drop
     */
    List<ItemStack> generateLoot();
}
