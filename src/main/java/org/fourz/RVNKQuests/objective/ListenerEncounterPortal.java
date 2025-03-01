package org.fourz.RVNKQuests.objective;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.util.Debug;
import org.fourz.RVNKQuests.util.NameGenerator;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;

public class ListenerEncounterPortal implements Listener {
    public static final String QUEST_MOB_METADATA = "rvnkquests.questmob";
    
    private final Quest quest;
    private final Debug debug;
    private final List<Entity> spawnedMobs = new ArrayList<>();
    private final Set<String> spawnedMobNames = new HashSet<>();
    private static final int PORTAL_HEIGHT = 85;
    private static final int TRIGGER_DISTANCE = 30;
    private Location portalLocation;
    private boolean spawned = false;
    private final Map<EntityType, Integer> mobsToSpawn;
    private final Set<Player> playersInRange = new HashSet<>();
    private final Random random = new Random();
    private Map<Player, Location> lastCheckLocations = new HashMap<>();
    private static final double CHECK_DISTANCE = 5.0; // Check every 5 blocks of movement

    public ListenerEncounterPortal(Quest quest, Map<EntityType, Integer> mobsToSpawn) {
        this.quest = quest;
        this.mobsToSpawn = mobsToSpawn;
        this.debug = Debug.createDebugger(quest.getPlugin(), "EncounterPortal", Level.FINE);
        
        debug.debug("Initialized with mob config: " + mobsToSpawn.toString());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (spawned) return;

        Location to = event.getTo();
        Player player = event.getPlayer();

        // Height check
        if (to.getY() < PORTAL_HEIGHT) {
            playersInRange.remove(player);
            lastCheckLocations.remove(player);
            return;
        }

        // Distance check
        Location lastCheck = lastCheckLocations.get(player);
        if (lastCheck != null && lastCheck.distance(to) < CHECK_DISTANCE) {
            return;
        }

        // Perform portal check
        if (isNearLitPortal(to, TRIGGER_DISTANCE)) {
            spawnMobGroup(portalLocation);
            spawned = true;
            quest.advanceState(QuestState.OBJECTIVE_COMPLETE);
            cleanup();
            return;
        }

        lastCheckLocations.put(player, to.clone());
    }

    private boolean isNearLitPortal(Location loc, int distance) {
        debug.debug("Checking for portal within " + distance + " blocks");
        for (int x = -distance; x <= distance; x++) {
            for (int y = -distance; y <= distance; y++) {
                for (int z = -distance; z <= distance; z++) {
                    Location checkLoc = loc.clone().add(x, y, z);
                    if (checkLoc.getBlock().getType() == Material.NETHER_PORTAL) {
                        debug.debug("Found portal at: " + checkLoc.toString());
                        portalLocation = checkLoc;
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
                String mobName = NameGenerator.generateMobName(entityType);
                entity.setCustomName(mobName);
                entity.setCustomNameVisible(true);
                
                // Track both the entity and its name
                spawnedMobNames.add(mobName);
                spawnedMobs.add(entity);
                
                // Add metadata to identify this as a quest mob
                entity.setMetadata(QUEST_MOB_METADATA, 
                    new FixedMetadataValue(quest.getPlugin(), quest.getId()));
                
                debug.debug(String.format("Spawned %s at %s", 
                    mobName,
                    spawnLoc.toString()));
            }
        });

        // Register the portal prevention listener
        quest.getPlugin().getServer().getPluginManager().registerEvents(
            new ListenerPreventQuestMobPortal(quest.getPlugin()),
            quest.getPlugin()
        );

        debug.debug("Mob group spawn complete. Total mobs: " + spawnedMobs.size());
    }

    public Set<String> getSpawnedMobNames() {
        return spawnedMobNames;
    }

    public void removeMob(String mobName) {
        spawnedMobNames.remove(mobName);
        spawnedMobs.removeIf(entity -> entity.getCustomName() != null && 
                                      entity.getCustomName().equals(mobName));
        debug.debug("Removed mob: " + mobName + ", Remaining mobs: " + spawnedMobNames.size());
    }

    public List<Entity> getSpawnedMobs() {
        return spawnedMobs;
    }

    private void cleanup() {
        lastCheckLocations.clear();
        playersInRange.clear();
    }
}
