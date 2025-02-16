package org.fourz.RVNKQuests.trigger;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.QuestFirstCityProphecy;
import org.fourz.RVNKQuests.quest.QuestState;

import java.util.Random;

public class ListenerProphecyVisions implements Listener {
    private final RVNKQuests plugin;
    private final QuestFirstCityProphecy quest;
    private final Random random = new Random();

    public ListenerProphecyVisions(RVNKQuests plugin, QuestFirstCityProphecy quest) {
        this.plugin = plugin;
        this.quest = quest;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (quest.getCurrentState() != QuestState.TRIGGER_FOUND) return;
        
        // ...existing particle and whisper logic...

        if (random.nextInt(1000) == 0) { // Rare chance to advance to next state
            quest.advanceState(QuestState.QUEST_ACTIVE);
            event.getPlayer().sendMessage("§5The visions have shown you the way. Find the prophesied location...");
        }
    }
}
