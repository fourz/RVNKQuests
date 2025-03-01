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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ListenerEncounterPortal implements Listener {
    private final Quest quest;
    private final List<Entity> spawnedMobs = new ArrayList<>();
    private static final int PORTAL_HEIGHT = 85;
    private static final int TRIGGER_DISTANCE = 30;
    private boolean spawned = false;
    private final Map<EntityType, Integer> mobsToSpawn;
    private final String mobNamePrefix;

    public ListenerEncounterPortal(Quest quest, Map<EntityType, Integer> mobsToSpawn, String mobNamePrefix) {
        this.quest = quest;
        this.mobsToSpawn = mobsToSpawn;
        this.mobNamePrefix = mobNamePrefix;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (spawned) return;
        
        // Only check if player moved to a new block (ignore rotation changes)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        
        Location playerLoc = event.getPlayer().getLocation();
        if (playerLoc.getY() < PORTAL_HEIGHT) return;

        if (isNearLitPortal(playerLoc, TRIGGER_DISTANCE)) {
            spawnMobGroup(playerLoc);
            spawned = true;
            quest.advanceState(QuestState.OBJECTIVE_COMPLETE);
        }
    }

    private boolean isNearLitPortal(Location loc, int distance) {
        for (int x = -distance; x <= distance; x++) {
            for (int y = -distance; y <= distance; y++) {
                for (int z = -distance; z <= distance; z++) {
                    Location checkLoc = loc.clone().add(x, y, z);
                    if (checkLoc.getBlock().getType() == Material.NETHER_PORTAL) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void spawnMobGroup(Location near) {
        mobsToSpawn.forEach((entityType, count) -> {
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
            }
        });
    }

    public List<Entity> getSpawnedMobs() {
        return spawnedMobs;
    }
}
