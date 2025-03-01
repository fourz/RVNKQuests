package org.fourz.RVNKQuests.objective;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.util.Debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class ListenerEncounterPortal implements Listener {
    private final Quest quest;
    private final Debug debug;
    private final List<Entity> spawnedMobs = new ArrayList<>();
    private static final int PORTAL_HEIGHT = 85;
    private static final int TRIGGER_DISTANCE = 30;
    private boolean spawned = false;
    private final Map<EntityType, Integer> mobsToSpawn;
    private final String mobNamePrefix;
    private Location lastCheckedLocation = null;
    private static final int CHECK_INTERVAL = 8; // blocks

    public ListenerEncounterPortal(Quest quest, Map<EntityType, Integer> mobsToSpawn, String mobNamePrefix) {
        this.quest = quest;
        this.mobsToSpawn = mobsToSpawn;
        this.mobNamePrefix = mobNamePrefix;
        this.debug = Debug.createDebugger(quest.getPlugin(), "EncounterPortal", Level.FINE);
        
        debug.debug("Initialized with mob config: " + mobsToSpawn.toString());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (spawned) return;
        
        Location playerLoc = event.getTo();

        // Skip if below portal height
        if (playerLoc.getY() < PORTAL_HEIGHT) {
            lastCheckedLocation = null; // Reset when below threshold
            return;
        }

        // Only check if moved significant distance from last check
        if (lastCheckedLocation != null && 
            lastCheckedLocation.distance(playerLoc) < CHECK_INTERVAL) {
            return;
        }

        debug.debug(String.format("Player %s moved to Y=%d (Portal height: %d)", 
            event.getPlayer().getName(), 
            (int)playerLoc.getY(), 
            PORTAL_HEIGHT));

        if (isNearLitPortal(playerLoc, TRIGGER_DISTANCE)) {
            debug.debug("Player found portal - spawning mob group");
            spawnMobGroup(playerLoc);
            spawned = true;
            quest.advanceState(QuestState.OBJECTIVE_COMPLETE);
        }

        lastCheckedLocation = playerLoc.clone();
    }

    private boolean isNearLitPortal(Location loc, int distance) {
        debug.debug("Checking for portal within " + distance + " blocks");
        for (int x = -distance; x <= distance; x++) {
            for (int y = -distance; y <= distance; y++) {
                for (int z = -distance; z <= distance; z++) {
                    Location checkLoc = loc.clone().add(x, y, z);
                    if (checkLoc.getBlock().getType() == Material.NETHER_PORTAL) {
                        debug.debug("Found portal at: " + checkLoc.toString());
                        return true;
                    }
                }
            }
        }
        debug.debug("No portal found in range");
        return false;
    }

    private void spawnMobGroup(Location near) {
        debug.debug("Spawning mob group near: " + near.toString());
        mobsToSpawn.forEach((entityType, count) -> {
            debug.debug(String.format("Spawning %d x %s", count, entityType));
            for (int i = 0; i < count; i++) {
                Location spawnLoc = near.clone().add(
                    Math.random() * 10 - 5,
                    0,
                    Math.random() * 10 - 5
                );
                Entity entity = near.getWorld().spawnEntity(spawnLoc, entityType);
                entity.setCustomName(mobNamePrefix);
                entity.setCustomNameVisible(true);
                spawnedMobs.add(entity);
                debug.debug(String.format("Spawned %s at %s", 
                    entity.getCustomName(),
                    spawnLoc.toString()));
            }
        });
        debug.debug("Mob group spawn complete. Total mobs: " + spawnedMobs.size());
    }

    public List<Entity> getSpawnedMobs() {
        return spawnedMobs;
    }
}
