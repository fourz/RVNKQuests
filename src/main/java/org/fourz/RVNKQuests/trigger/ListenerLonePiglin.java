package org.fourz.RVNKQuests.trigger;

import org.bukkit.Bukkit;
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
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.util.Debug;
import org.fourz.RVNKQuests.util.IntervalChecker;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;

public class ListenerLonePiglin implements Listener {
    private static final double DETECTION_RADIUS = 30.0;
    private static final double MIN_MOVEMENT_CHECK = 5.0;
    private static final int CHECK_FREQUENCY = 20;
    
    private final Quest quest;
    private final JavaPlugin plugin;
    private final Debug debug;
    private final Random random = new Random();
    private Entity spawnedPiglin = null;
    private final IntervalChecker intervalChecker;
    
    private final String targetWorld;
    private final Location targetLocation;
    private final double spawnRadius;

    public ListenerLonePiglin(Quest quest, JavaPlugin plugin) {
        this(quest, plugin, "event", null, DETECTION_RADIUS);
    }
    
    public ListenerLonePiglin(Quest quest, JavaPlugin plugin, String worldName, Location location, double radius) {
        this.quest = quest;
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "LonePiglin", Level.FINE);
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
                debug.warning("Target world '" + worldName + "' not found. Piglin may not spawn correctly.");
            }
        }
        
        debug.debug("Initialized with target world: " + targetWorld + 
                    ", location: " + (targetLocation != null ? targetLocation.toString() : "N/A") +
                    ", radius: " + spawnRadius);
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

        debug.debug("Checking for piglin spawn conditions near player: " + player.getName());
        if (shouldSpawnPiglin(currentLoc)) {
            spawnLonePiglin(currentLoc);
        }
    }

    private boolean shouldSpawnPiglin(Location location) {
        
        // Check if we have a valid target location
        if (targetLocation == null) {
            debug.debug("No valid target location for spawning");
            return false;
        }
        
        // Check if player is within the required distance of world spawn
        double distance = location.distance(targetLocation);
        if (distance > spawnRadius) {
            debug.debug("Player too far from spawn: " + distance + " blocks (max: " + spawnRadius + ")");
            return false;
        }
        
        debug.debug("All spawn conditions met at location: " + location);
        return true;
    }

    private void spawnLonePiglin(Location playerLocation) {
        debug.debug("Spawning a lone piglin at world spawn");
        
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
        spawnedPiglin.setCustomName("Lost Piglin");
        spawnedPiglin.setCustomNameVisible(true);
        
        // Give the piglin special equipment if needed
        Piglin piglin = (Piglin) spawnedPiglin;
        piglin.setImmuneToZombification(true);
        piglin.getEquipment().setItemInMainHand(new ItemStack(Material.GOLDEN_SWORD));
        
        debug.debug("Piglin spawned successfully at: " + spawnLoc);
        
        // Advance quest state
        quest.advanceState(QuestState.TRIGGER_FOUND);
    }

    public void cleanup() {
        debug.debug("Cleaning up ListenerLonePiglin");
        if (spawnedPiglin != null && !spawnedPiglin.isDead()) {
            spawnedPiglin.remove();
        }
        intervalChecker.reset();
    }
}
