package org.fourz.RVNKQuests.trigger;

import org.bukkit.Material;
import org.bukkit.entity.Piglin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.reward.QuestItem;
import org.fourz.RVNKQuests.util.Debug;

import java.util.logging.Level;

public class ListenerLonePiglinDeath implements Listener {
    private final Quest quest;
    private final ListenerLonePiglin lonePiglinListener;
    private final Debug debug;

    public ListenerLonePiglinDeath(Quest quest, ListenerLonePiglin lonePiglinListener) {
        this.quest = quest;
        this.lonePiglinListener = lonePiglinListener;
        this.debug = Debug.createDebugger(quest.getPlugin(), "LonePiglinDeath", Level.FINE);
    }

    @EventHandler
    public void onPiglinDeath(EntityDeathEvent event) {     
        if (!event.getEntity().getName().equals("Lost Piglin")) {
            debug.debug("Piglin death: not the quest Piglin");
            return;
        }

        debug.debug("Quest Piglin died, preparing to drop journal");
        ItemStack book = QuestItem.getQuestItem("grotsnouts_journal");
        
        if (book == null) {
            debug.warning("Failed to retrieve quest item!");
            return;
        }

        debug.debug("Clearing existing drops and adding quest journal");
        event.getDrops().clear();
        event.getDrops().add(book);
        
        debug.debug("Advancing quest state to QUEST_ACTIVE");
        quest.advanceState(QuestState.QUEST_ACTIVE);
    }
}
