package org.fourz.RVNKQuests.trigger;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.fourz.RVNKQuests.quest.QuestFirstCityProphecy;
import org.fourz.RVNKQuests.quest.QuestState;

public class ListenerProphecyDiscovery implements Listener {
    private final QuestFirstCityProphecy quest;
    private boolean lightningTriggered = false;

    public ListenerProphecyDiscovery(QuestFirstCityProphecy quest) {
        this.quest = quest;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (lightningTriggered || quest.getCurrentState() != QuestState.NOT_STARTED) return;

        Player player = event.getPlayer();
        Location lecternLoc = quest.getLecternLocation();
        if (lecternLoc == null) return;

        if (player.getWorld().equals(lecternLoc.getWorld()) 
            && player.getLocation().distance(lecternLoc) < 15) {

            player.getWorld().strikeLightningEffect(lecternLoc);
            player.sendMessage("§6A sudden lightning flash reveals an ancient lectern...");
            
            quest.advanceState(QuestState.TRIGGER_FOUND);
            lightningTriggered = true;
        }
    }
}
