package org.fourz.RVNKQuests.objective;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Drowned;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;

public class ListenerForgottenSiteDefeated implements Listener {
    private final Quest quest;
    private final ListenerForgottenSite siteListener;

    public ListenerForgottenSiteDefeated(Quest quest, ListenerForgottenSite siteListener) {
        this.quest = quest;
        this.siteListener = siteListener;
    }

    @EventHandler
    public void onDrownedDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Drowned)) return;
        
        if (siteListener.getDefenders().contains(event.getEntity())) {
            siteListener.getDefenders().remove(event.getEntity());
            
            if (siteListener.getDefenders().isEmpty()) {
                spawnTreasureChest(event.getEntity().getLocation());
                quest.getPlugin().getServer().broadcastMessage(
                    "§6The ancient defenders have fallen, revealing their treasured secrets!"
                );
                quest.advanceState(QuestState.COMPLETED);
            }
        }
    }

    private void spawnTreasureChest(Location location) {
        Location chestLoc = location.clone();
        Block block = chestLoc.getBlock();
        block.setType(Material.CHEST);
        
        Chest chest = (Chest) block.getState();
        ItemStack trident = new ItemStack(Material.TRIDENT);
        trident.addEnchantment(Enchantment.LOYALTY, 3);
        chest.getInventory().addItem(trident);
        
        ItemStack heartOfTheSea = new ItemStack(Material.HEART_OF_THE_SEA);
        chest.getInventory().addItem(heartOfTheSea);
        
        chest.update();
    }
}
