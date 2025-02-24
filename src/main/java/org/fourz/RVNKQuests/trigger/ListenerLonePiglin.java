package org.fourz.RVNKQuests.trigger;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Piglin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;

public class ListenerLonePiglin implements Listener {
    private final Quest quest;
    private Piglin lonePiglin;
    private static final String EVENT_WORLD = "event";
    private static final int REQUIRED_DISTANCE = 50;

    public ListenerLonePiglin(Quest quest) {
        this.quest = quest;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        World world = event.getPlayer().getWorld();
        if (!world.getName().equals(EVENT_WORLD)) return;
        
        Location spawnLoc = world.getSpawnLocation();
        if (world.getPlayers().stream()
                .allMatch(p -> p.getLocation().distance(spawnLoc) <= REQUIRED_DISTANCE) 
                && lonePiglin == null) {
            lonePiglin = (Piglin) world.spawnEntity(spawnLoc, EntityType.PIGLIN);
            lonePiglin.setCustomName("Lost Piglin");
            lonePiglin.setCustomNameVisible(true);
            quest.advanceState(QuestState.TRIGGER_FOUND);
        }
    }

    public Piglin getLonePiglin() {
        return lonePiglin;
    }
}
