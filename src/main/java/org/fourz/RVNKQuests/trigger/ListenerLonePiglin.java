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

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;

public class ListenerLonePiglin implements Listener {
    private static final double DETECTION_RADIUS = 30.0;
    private static final long CHECK_INTERVAL = 20L; // Check every second (20 ticks)
    private static final double MIN_MOVEMENT_CHECK = 5.0; // Minimum movement before recheck
    
    private final Quest quest;
    private final JavaPlugin plugin;
    private final Debug debug;
    private final String taskId;
    private final Random random = new Random();
    private Entity spawnedPiglin = null;
    private Map<Player, Location> lastCheckLocations = new HashMap<>();
    private boolean isScheduled = false;

    public ListenerLonePiglin(Quest quest, JavaPlugin plugin) {
        this.quest = quest;
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "LonePiglin", Level.FINE);
        this.taskId = "lone_piglin_check_" + quest.getId();
        
        // Schedule the initial check when the listener is created
        scheduleChecks();
    }

    /**
     * Schedules the periodic piglin check task
     */
    public void scheduleChecks() {
        if (isScheduled) {
            debug.debug("Check task already scheduled, skipping");
            return;
        }
        
        debug.debug("Setting up scheduled piglin check every " + CHECK_INTERVAL + " ticks");
        quest.getPlugin().getQuestManager().scheduleRepeatingTask(
            taskId,
            this::checkPlayersForPiglinSpawn,
            CHECK_INTERVAL
        );
        
        isScheduled = true;
    }

    /**
     * Stops the scheduled checks
     */
    public void stopChecks() {
        if (!isScheduled) {
            return;
        }
        
        debug.debug("Stopping scheduled piglin checks");
        quest.getPlugin().getQuestManager().cancelTask(taskId);
        isScheduled = false;
    }

    private void checkPlayersForPiglinSpawn() {
        if (spawnedPiglin != null && !spawnedPiglin.isDead()) {
            return;
        }

        debug.debug("Running scheduled piglin check for " + plugin.getServer().getOnlinePlayers().size() + " players");
        
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Location currentLoc = player.getLocation();
            Location lastCheck = lastCheckLocations.get(player);

            // Only check if player has moved significantly
            if (lastCheck != null && lastCheck.distance(currentLoc) < MIN_MOVEMENT_CHECK) {
                continue;
            }

            debug.debug("Checking for piglin spawn conditions near player: " + player.getName());
            if (shouldSpawnPiglin(currentLoc)) {
                spawnLonePiglin(currentLoc);
                break;
            }

            lastCheckLocations.put(player, currentLoc.clone());
        }
    }

    private boolean shouldSpawnPiglin(Location location) {
                
        // Add more conditions as needed
        debug.debug("Piglin spawn conditions met at location: " + location);
        return true;
    }

    private void spawnLonePiglin(Location near) {
        debug.debug("Spawning a lone piglin near: " + near);
        
        // Find a suitable spawn location nearby
        Location spawnLoc = near.clone().add(
            random.nextInt(10) - 5,
            0,
            random.nextInt(10) - 5
        );
        
        // Ensure the spawn location is safe
        spawnLoc.setY(near.getWorld().getHighestBlockYAt(spawnLoc));
        
        // Spawn the piglin
        spawnedPiglin = near.getWorld().spawnEntity(spawnLoc, EntityType.PIGLIN);
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
        stopChecks();
        lastCheckLocations.clear();
    }
}
