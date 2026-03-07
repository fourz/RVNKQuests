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
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.RVNKQuests.util.EnvironmentEffects;
import org.fourz.RVNKQuests.util.NameGenerator;
import org.fourz.RVNKQuests.util.IntervalChecker;
import org.bukkit.metadata.FixedMetadataValue;
import org.fourz.RVNKQuests.util.PlayerAwareListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ListenerEncounterPortal implements PlayerAwareListener {
    public static final String QUEST_MOB_METADATA = "rvnkquests.questmob";
    
    private final Quest quest;
        private final LogManager logger;
    private final List<Entity> spawnedMobs = new ArrayList<>();
    private final Set<String> spawnedMobNames = new HashSet<>();
    private static final int PORTAL_HEIGHT = 160;
    private static final int TRIGGER_DISTANCE = 30;
    private Location portalLocation;
    private boolean spawned = false;
    private final Map<EntityType, Integer> mobsToSpawn;
    private final Set<Player> playersInRange = new HashSet<>();
    private final IntervalChecker moveChecker;
    private ListenerPreventMobInfighting infightingPreventionListener;
    private ListenerPreventPortalUse portalPreventionListener;

    public ListenerEncounterPortal(Quest quest, Map<EntityType, Integer> mobsToSpawn) {
        this.quest = quest;
        this.mobsToSpawn = mobsToSpawn;
        // Updated to use quest.getPlugin().getDebugger().getLogLevel()
        this.logger = LogManager.getInstance(quest.getPlugin(), getClass());
        this.moveChecker = new IntervalChecker(5, 5.0); // Check every 5 ticks, minimum 5.0 blocks moved
        
    logger.debug("Initialized with mob config: " + mobsToSpawn.toString());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (spawned) return;

        Location to = event.getTo();
        Player player = event.getPlayer();

        // Height check
        if (to.getY() < PORTAL_HEIGHT) {
            playersInRange.remove(player);
            moveChecker.clearEntity(player.getUniqueId());
            return;
        }

        // Use IntervalChecker instead of manual distance check
        if (!moveChecker.shouldCheck(player.getUniqueId(), to)) {
            return;
        }

        // Perform portal check
        if (isNearLitPortal(to, TRIGGER_DISTANCE)) {
            
            quest.advanceStateForPlayer(player.getUniqueId(), QuestState.OBJECTIVE_FOUND);
            
            // Capture world here to ensure it's available for the lambda
            final Location portalLoc = portalLocation.clone();
            final String worldName = portalLoc.getWorld().getName();
            
                logger.debug("Starting dramatic lightning sequence in world: " + worldName);
            
            EnvironmentEffects.startDramaticLightningSequence(
                quest.getPlugin(),
                portalLocation,
                25,  // 20 block radius
                200, // 10 second duration (20 ticks/sec)
                5,   // 5 lightning strikes
                (v) -> {                                        
                    spawnMobGroup(portalLocation);
                    cleanup();
                }
            );
            
            spawned = true;
            return;
        }
    }

    private boolean isNearLitPortal(Location loc, int distance) {
            logger.debug("Checking for portal within " + distance + " blocks");
        for (int x = -distance; x <= distance; x++) {
            for (int y = -distance; y <= distance; y++) {
                for (int z = -distance; z <= distance; z++) {
                    Location checkLoc = loc.clone().add(x, y, z);
                    if (checkLoc.getBlock().getType() == Material.NETHER_PORTAL) {
                            logger.debug("Found portal at: " + checkLoc.toString());
                        portalLocation = checkLoc;
                        return true;
                    }
                }
            }
        }
            logger.debug("No portal found in range");
        return false;
    }

    private void spawnMobGroup(Location near) {
            logger.debug("Spawning mob group near: " + near.toString());
        mobsToSpawn.forEach((entityType, count) -> {
                logger.debug("Spawning " + count + " x " + entityType);
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
                
                    logger.debug("Spawned " + mobName + " at " + spawnLoc.toString());
            }
        });

        // Register the mob infighting prevention listener
        infightingPreventionListener = new ListenerPreventMobInfighting(quest.getPlugin());
        quest.getPlugin().getServer().getPluginManager().registerEvents(
            infightingPreventionListener,
            quest.getPlugin()
        );
        
        // Register the portal use prevention listener
        portalPreventionListener = new ListenerPreventPortalUse(quest.getPlugin());
        quest.getPlugin().getServer().getPluginManager().registerEvents(
            portalPreventionListener,
            quest.getPlugin()
        );

            logger.debug("Mob group spawn complete. Total mobs: " + spawnedMobs.size());
    }

    public Set<String> getSpawnedMobNames() {
        return spawnedMobNames;
    }

    public void removeMob(String mobName) {
        spawnedMobNames.remove(mobName);
        spawnedMobs.removeIf(entity -> entity.getCustomName() != null && 
                                      entity.getCustomName().equals(mobName));
            logger.debug("Removed mob: " + mobName + ", Remaining mobs: " + spawnedMobNames.size());
    }

    public List<Entity> getSpawnedMobs() {
        return spawnedMobs;
    }

    private void cleanup() {
        moveChecker.reset();
        playersInRange.clear();
    }

    /**
     * Gets the mob infighting prevention listener
     * @return The mob infighting prevention listener or null if not yet created
     */
    public ListenerPreventMobInfighting getInfightingPreventionListener() {
        return infightingPreventionListener;
    }
    
    /**
     * Gets the portal use prevention listener
     * @return The portal use prevention listener or null if not yet created
     */
    public ListenerPreventPortalUse getPortalPreventionListener() {
        return portalPreventionListener;
    }
    
    /**
     * Gets the location of the portal
     * @return The portal location or null if not yet found
     */
    public Location getPortalLocation() {
        return portalLocation;
    }

    @Override
    public void clearPlayerData(UUID playerUuid) {
        moveChecker.clearEntity(playerUuid);
    }
}
