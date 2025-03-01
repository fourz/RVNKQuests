package org.fourz.RVNKQuests.objective;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.reward.QuestLoot;

public class ListenerEncounterPortalDefeated implements Listener {
    private final Quest quest;
    private final ListenerEncounterPortal portalListener;
    private final QuestLoot questLoot;

    public ListenerEncounterPortalDefeated(Quest quest, ListenerEncounterPortal portalListener, QuestLoot questLoot) {
        this.quest = quest;
        this.portalListener = portalListener;
        this.questLoot = questLoot;
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        if (portalListener.getSpawnedMobs().contains(event.getEntity())) {
            portalListener.getSpawnedMobs().remove(event.getEntity());
            
            if (portalListener.getSpawnedMobs().isEmpty()) {
                event.getDrops().addAll(questLoot.generateLoot());
                quest.advanceState(QuestState.COMPLETED);
            }
        }
    }
}
