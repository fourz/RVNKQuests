package org.fourz.RVNKQuests.objective;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.util.Debug;
import org.fourz.RVNKQuests.util.IntervalChecker;
import org.fourz.RVNKQuests.util.EnvironmentEffects;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public class ListenerForgottenSite implements Listener {
    private final Quest quest;
    private final List<Drowned> defenders = new ArrayList<>();
    private static final int TRIGGER_DISTANCE = 30;
    private boolean spawned = false;
    private final Debug debug;
    private final IntervalChecker moveChecker;
    private final Set<Material> ruinMaterials = new HashSet<>();

    public ListenerForgottenSite(Quest quest) {
        this.quest = quest;
        this.debug = Debug.createDebugger(quest.getPlugin(), "ForgottenSite", Level.FINE);
        this.moveChecker = new IntervalChecker(5, 5.0); // Check every 5 ticks, minimum 5.0 blocks moved
        
        // Initialize the ruin materials to detect
        ruinMaterials.add(Material.PRISMARINE);
        ruinMaterials.add(Material.PRISMARINE_BRICKS);
        ruinMaterials.add(Material.DARK_PRISMARINE);
        ruinMaterials.add(Material.SEA_LANTERN);
        ruinMaterials.add(Material.ANCIENT_DEBRIS);
        ruinMaterials.add(Material.CONDUIT);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (spawned) return;

        // Only check if player moved to a new block (use IntervalChecker)
        if (!moveChecker.shouldCheck(event.getPlayer().getUniqueId(), event.getTo())) {
            return;
        }
        
        Location playerLoc = event.getPlayer().getLocation();
        if (!isNearUnderwaterStructure(playerLoc)) return;

        // If the player is underwater, make sure they're actually underwater
        if (playerLoc.getBlock().getType() != Material.WATER && !playerLoc.getBlock().isLiquid()) {
            debug.debug("Found structure materials but player is not underwater");
            return;
        }

        debug.debug("Player found underwater ruin at: " + playerLoc);
        spawnDefenders(playerLoc);
        spawned = true;
        
        // Create dramatic underwater effect
        EnvironmentEffects.startDramaticLightningSequence(
            quest.getPlugin(),
            playerLoc,
            15,  // 15 block radius
            100, // 5 second duration (20 ticks/sec)
            3,   // 3 lightning strikes
            (v) -> {} // No additional action needed
        );
        
        quest.advanceState(QuestState.OBJECTIVE_FOUND);
    }

    private boolean isNearUnderwaterStructure(Location loc) {
        debug.debug("Checking for underwater structures around: " + loc);
        
        for (int x = -TRIGGER_DISTANCE; x <= TRIGGER_DISTANCE; x+= 4) {
            for (int y = -TRIGGER_DISTANCE; y <= TRIGGER_DISTANCE; y += 4) {
                for (int z = -TRIGGER_DISTANCE; z <= TRIGGER_DISTANCE; z += 4) {
                    Location checkLoc = loc.clone().add(x, y, z);
                    Material type = checkLoc.getBlock().getType();
                    if (ruinMaterials.contains(type)) {
                        debug.debug("Found structure material: " + type + " at " + checkLoc);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void spawnDefenders(Location center) {
        debug.debug("Spawning underwater defenders at: " + center);
        
        // Find a suitable underwater location
        Location spawnCenter = findSuitableLocation(center);
        
        for (int i = 0; i < 5; i++) {
            Location spawnLoc = spawnCenter.clone().add(
                Math.random() * 10 - 5,
                Math.random() * 4 - 2,
                Math.random() * 10 - 5
            );
            
            Drowned drowned = (Drowned) center.getWorld().spawnEntity(spawnLoc, EntityType.DROWNED);
            drowned.setCustomName("Ancient Defender");
            drowned.setCustomNameVisible(true);
            drowned.getEquipment().setItemInMainHand(new ItemStack(Material.TRIDENT));
            
            // Some defenders get special armor
            if (i < 2) {
                drowned.getEquipment().setHelmet(new ItemStack(Material.TURTLE_HELMET));
                drowned.getEquipment().setChestplate(new ItemStack(Material.GOLDEN_CHESTPLATE));
            }
            
            defenders.add(drowned);
            debug.debug("Spawned defender at: " + spawnLoc);
        }
        
        quest.getPlugin().getServer().broadcastMessage(
            "§b[Ancient Guardian] §fAncient defenders arise from the depths to protect their ruins!"
        );
    }
    
    private Location findSuitableLocation(Location center) {
        // Try to find a location that is in water
        Location result = center.clone();
        
        // If already in water, just use it
        if (result.getBlock().isLiquid()) {
            return result;
        }
        
        // Otherwise, try to find water nearby
        for (int y = 0; y > -20; y--) {
            Location check = center.clone().add(0, y, 0);
            if (check.getBlock().isLiquid()) {
                return check;
            }
        }
        
        // If no water found, just return the original location
        return result;
    }

    public List<Drowned> getDefenders() {
        return defenders;
    }
}
