package org.fourz.RVNKQuests.trigger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestPiglinFarFromHome;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.RVNKQuests.util.IntervalChecker;
import org.fourz.RVNKQuests.util.PlayerAwareListener;

import java.util.Random;
import java.util.UUID;

/**
 * Trigger for spawning a lone piglin when a player enters a specific area
 */
public class ListenerLonePiglinTrigger implements PlayerAwareListener {
    private static final double DETECTION_RADIUS = 30.0;
    private static final double MIN_MOVEMENT_CHECK = 5.0;
    private static final int CHECK_FREQUENCY = 20;

    private final Quest quest;
    private final LogManager logger;
    private final Random random = new Random();
    private Entity spawnedPiglin = null;
    private final IntervalChecker intervalChecker;

    private final String targetWorld;
    private final Location targetLocation;
    private final double spawnRadius;
    private String piglinName = "Lost Piglin";

    public ListenerLonePiglinTrigger(Quest quest, JavaPlugin plugin) {
    this(quest, plugin, "event", null, DETECTION_RADIUS);
    }
    
    public ListenerLonePiglinTrigger(Quest quest, JavaPlugin plugin, String worldName, Location location, double radius) {
        this.quest = quest;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.spawnRadius = radius;
        this.targetWorld = worldName;
        this.intervalChecker = new IntervalChecker(CHECK_FREQUENCY, MIN_MOVEMENT_CHECK);

        // If a specific location is provided, use it; otherwise use the world's spawn point
        if (location != null) {
            this.targetLocation = location.clone();
        } else {
            World world = plugin.getServer().getWorld(worldName);
            if (world != null) {
                this.targetLocation = world.getSpawnLocation().clone();
            } else {
                this.targetLocation = null;
                logger.warning("Target world '" + worldName + "' not found. Piglin may not spawn correctly.");
            }
        }

    logger.debug("Initialized with target world: " + targetWorld + " | location: " + (targetLocation != null ? targetLocation.toString() : "N/A") + " | radius: " + spawnRadius);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (spawnedPiglin != null && !spawnedPiglin.isDead()) {
            return;
        }

        Player player = event.getPlayer();

        // Check world first
        if (!player.getWorld().getName().equalsIgnoreCase(targetWorld)) {
            return;
        }

        Location currentLoc = player.getLocation();

        // Use the interval checker to determine if we should process this movement
        if (!intervalChecker.shouldCheck(player.getUniqueId(), currentLoc)) {
            return;
        }

    logger.debug("Checking for piglin spawn conditions near player: " + player.getName());
        if (shouldSpawnPiglin(currentLoc)) {
            spawnLonePiglin(player, currentLoc);
        }
    }

    private boolean shouldSpawnPiglin(Location location) {
        // Check if we have a valid target location
        if (targetLocation == null) {
            logger.debug("No valid target location for spawning");
            return false;
        }

        // Check if player is within the required distance of world spawn
        double distance = location.distance(targetLocation);
        if (distance > spawnRadius) {
            logger.debug("Player too far from spawn: " + distance + " blocks (max: " + spawnRadius + ")");
            return false;
        }

        logger.debug("All spawn conditions met at location: " + location + "");
        return true;
    }

    private void spawnLonePiglin(Player player, Location playerLocation) {
        logger.debug("Spawning a lone piglin at world spawn");

        // Use the target location (world spawn) as the base spawn point
        Location spawnLoc = targetLocation.clone();

        // Add small random offset from spawn point
        spawnLoc.add(
            random.nextInt(6) - 3,  // ±3 blocks X
            0,                      // No Y offset initially
            random.nextInt(6) - 3   // ±3 blocks Z
        );

        // Find safe Y position at the spawn location
        spawnLoc.setY(spawnLoc.getWorld().getHighestBlockYAt(spawnLoc));

        // Spawn the piglin
        spawnedPiglin = spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.PIGLIN);
        spawnedPiglin.setCustomName(piglinName);
        spawnedPiglin.setCustomNameVisible(true);

        // Give the piglin special equipment if needed
        Piglin piglin = (Piglin) spawnedPiglin;
        piglin.setImmuneToZombification(true);
        piglin.getEquipment().setItemInMainHand(new ItemStack(Material.GOLDEN_SWORD));

        // Update the spawn location in the quest if it's a QuestPiglinFarFromHome
        if (quest instanceof QuestPiglinFarFromHome) {
            ((QuestPiglinFarFromHome) quest).setSpawnLocation(spawnLoc.clone());
        }

        logger.debug("Piglin spawned successfully at: " + spawnLoc + "");

        // Advance quest state
        quest.advanceStateForPlayer(player.getUniqueId(), QuestState.TRIGGER_FOUND);
    }

    public void cleanup() {
        logger.debug("Cleaning up TriggerLonePiglin");
        if (spawnedPiglin != null && !spawnedPiglin.isDead()) {
            spawnedPiglin.remove();
        }
        intervalChecker.reset();
    }

    /**
     * Sets the custom name for the quest piglin
     * @param name The name to give the spawned piglin
     */
    public void setPiglinName(String name) {
        this.piglinName = name;
        if (spawnedPiglin != null && !spawnedPiglin.isDead()) {
            spawnedPiglin.setCustomName(name);
        }
    }

    /**
     * Checks if an entity is the quest piglin spawned by this trigger
     * @param entity The entity to check
     * @return true if this is the quest piglin
     */
    public boolean isQuestPiglin(Entity entity) {
        if (entity == null) return false;

        // Direct reference check first (most reliable)
        if (entity.equals(spawnedPiglin)) {
            return true;
        }

        // Fallback to checking name and type
        if (entity.getType() == EntityType.PIGLIN) {
            // Check custom name
            String customName = entity.getCustomName();
            if (customName != null && (customName.equals(piglinName) || customName.equals("Lost Piglin"))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Gets the spawned piglin entity
     * @return The piglin entity or null if not spawned
     */
    public Entity getSpawnedPiglin() {
    return spawnedPiglin;
    }

    @Override
    public void clearPlayerData(UUID playerUuid) {
        intervalChecker.clearEntity(playerUuid);
    }
}