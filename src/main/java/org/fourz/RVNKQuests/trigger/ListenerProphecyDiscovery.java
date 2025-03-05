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
    private final Location lecternLocation;
    private boolean lightningTriggered = false;

    public ListenerProphecyDiscovery(QuestFirstCityProphecy quest, Location lecternLocation) {
        this.quest = quest;
        this.lecternLocation = lecternLocation;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (lightningTriggered || quest.getCurrentState() != QuestState.NOT_STARTED) return;

        Player player = event.getPlayer();
        if (lecternLocation == null) return;

        if (player.getWorld().equals(lecternLocation.getWorld()) 
            && player.getLocation().distance(lecternLocation) < 15) {

            player.getWorld().strikeLightningEffect(lecternLocation);
            player.sendMessage("§6A sudden lightning flash reveals an ancient lectern...");
            
            quest.advanceState(QuestState.TRIGGER_FOUND);
            lightningTriggered = true;
        }
    }

    public Location getLecternLocation() {
        return lecternLocation;
    }
}
