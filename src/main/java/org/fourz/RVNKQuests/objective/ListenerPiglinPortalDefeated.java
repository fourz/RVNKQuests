package org.fourz.RVNKQuests.objective;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;

public class ListenerPiglinPortalDefeated implements Listener {
    private final Quest quest;
    private final ListenerEncounterPortal portalListener;

    public ListenerPiglinPortalDefeated(Quest quest, ListenerEncounterPortal portalListener) {
        this.quest = quest;
        this.portalListener = portalListener;
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        if (portalListener.getSpawnedMobs().contains(event.getEntity())) {
            portalListener.getSpawnedMobs().remove(event.getEntity());
            
            if (portalListener.getSpawnedMobs().isEmpty()) {
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
