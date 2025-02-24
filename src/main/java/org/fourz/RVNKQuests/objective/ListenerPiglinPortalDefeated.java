package org.fourz.RVNKQuests.objective;

import org.bukkit.Material;
import org.bukkit.entity.Piglin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;

public class ListenerPiglinPortalDefeated implements Listener {
    private final Quest quest;
    private final ListenerPiglinPortal portalListener;

    public ListenerPiglinPortalDefeated(Quest quest, ListenerPiglinPortal portalListener) {
        this.quest = quest;
        this.portalListener = portalListener;
    }

    @EventHandler
    public void onPiglinDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Piglin)) return;
        
        if (portalListener.getSpawnedPiglins().contains(event.getEntity())) {
            portalListener.getSpawnedPiglins().remove(event.getEntity());
            
            if (portalListener.getSpawnedPiglins().isEmpty()) {
                dropLoot(event);
                quest.advanceState(QuestState.COMPLETED);
            }
        }
    }

    private void dropLoot(EntityDeathEvent event) {
        event.getDrops().add(new ItemStack(Material.GOLDEN_APPLE, 3));
        event.getDrops().add(new ItemStack(Material.NETHERITE_SCRAP, 1));
    }
}
