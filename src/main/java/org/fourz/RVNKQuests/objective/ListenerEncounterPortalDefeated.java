package org.fourz.RVNKQuests.objective;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.reward.QuestLoot;
import org.fourz.RVNKQuests.util.Debug;
import java.util.logging.Level;

public class ListenerEncounterPortalDefeated implements Listener {
    private final Quest quest;
    private final ListenerEncounterPortal portalListener;
    private final QuestLoot questLoot;
    private final Debug debug;

    public ListenerEncounterPortalDefeated(Quest quest, ListenerEncounterPortal portalListener, QuestLoot questLoot) {
        this.quest = quest;
        this.portalListener = portalListener;
        this.questLoot = questLoot;
        this.debug = Debug.createDebugger(quest.getPlugin(), "EncounterPortalDefeated", Level.FINE);
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        String mobName = entity.getCustomName();
        
        if (mobName != null && portalListener.getSpawnedMobNames().contains(mobName)) {
            debug.debug(String.format("Quest mob died: %s (Type: %s)", 
                mobName,
                event.getEntityType()));
                
            portalListener.removeMob(mobName);
            
            if (portalListener.getSpawnedMobNames().isEmpty()) {
                debug.debug("All quest mobs defeated, generating loot and completing quest");
                event.getDrops().addAll(questLoot.generateLoot());
                quest.advanceState(QuestState.COMPLETED);
            }
        }
    }
}
