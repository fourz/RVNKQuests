package org.fourz.RVNKQuests.trigger;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Piglin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.util.Debug;

import java.util.logging.Level;

public class ListenerLonePiglin implements Listener {
    private final Quest quest;
    private final Debug debug;
    private Piglin lonePiglin;
    private String triggerWorld = "event";
    private static final String PIGLIN_NAME = "Lost Piglin";
    private static final int REQUIRED_DISTANCE = 50;

    public ListenerLonePiglin(Quest quest, JavaPlugin plugin) {
        this.quest = quest;
        this.debug = Debug.createDebugger(plugin, "LonePiglin", Level.FINE);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isInEventWorld(event.getPlayer().getWorld())) return;
        
        debug.debug(String.format("Player %s moved in event world at %s", 
            event.getPlayer().getName(),
            event.getTo().toString()));
        
        if (shouldSpawnPiglin(event.getPlayer().getWorld())) {
            spawnLonePiglin(event.getPlayer().getWorld());
        }
    }

    private boolean isInEventWorld(World world) {
        return world.getName().equals(triggerWorld);
    }

    private boolean shouldSpawnPiglin(World world) {
        Location spawnLoc = world.getSpawnLocation();
        
        // Check if there's already a piglin with this name nearby
        boolean piglinExists = world.getEntities().stream()
            .filter(e -> e instanceof Piglin)
            .anyMatch(e -> PIGLIN_NAME.equals(e.getCustomName()));

        if (piglinExists) {
            debug.debug("Found existing Lost Piglin in the world");
            return false;
        }

        boolean allPlayersInRange = world.getPlayers().stream()
                .allMatch(p -> p.getLocation().distance(spawnLoc) <= REQUIRED_DISTANCE);
        
        debug.debug(String.format("Checking spawn conditions: piglinExists=%s, allPlayersInRange=%s", 
            piglinExists,
            allPlayersInRange));
        
        return allPlayersInRange;
    }

    private void spawnLonePiglin(World world) {
        lonePiglin = (Piglin) world.spawnEntity(world.getSpawnLocation(), EntityType.PIGLIN);
        lonePiglin.setCustomName(PIGLIN_NAME);
        lonePiglin.setCustomNameVisible(true);
        quest.advanceState(QuestState.TRIGGER_FOUND);
    }

    public Piglin getLonePiglin() {
        return lonePiglin;
    }

    public void setTriggerWorld(String worldName) {
        this.triggerWorld = worldName;
    }
}
