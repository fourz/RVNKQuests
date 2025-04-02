package org.fourz.RVNKQuests.quest;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.trigger.*;
import org.fourz.RVNKQuests.objective.*;
import org.fourz.RVNKQuests.reward.QuestLoot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuestPiglinFarFromHome extends AbstractQuest {
    private ListenerLonePiglin lonePiglinListener;
    private ListenerEncounterPortal portalListener;
    private Location spawnLocation;

    public QuestPiglinFarFromHome(RVNKQuests plugin) {
        // Call the parent constructor with the plugin, quest ID, and display name
        super(plugin, "piglin_far_from_home", "Piglin Far From Home");
    }

    @Override
    public void initialize() {
        debugger.debug("Initializing Piglin Far From Home quest");
        
        // Create the listener with custom location parameters from config
        String worldName = getPlugin().getConfigManager().getConfig()
                .getString("quests.piglin_far_from_home.world", "event");
        double spawnRadius = getPlugin().getConfigManager().getConfig()
                .getDouble("quests.piglin_far_from_home.spawn_radius", 30.0);
        
        // Initialize the listener with specific world and radius
        this.lonePiglinListener = new ListenerLonePiglin(this, getPlugin(), worldName, null, spawnRadius);

        // Set up portal encounter mobs
        Map<EntityType, Integer> portalMobs = new HashMap<>();
        portalMobs.put(EntityType.WITHER_SKELETON, 1);
        portalMobs.put(EntityType.SKELETON, 2);
        portalMobs.put(EntityType.HOGLIN, 2);        
        this.portalListener = new ListenerEncounterPortal(this, portalMobs);
        
        debugger.debug("Piglin Far From Home quest initialized");
    }

    @Override
    public void cleanup() {
        debugger.debug("Cleaning up Piglin Far From Home quest");
        
        // Clean up any remaining entities and listeners
        cleanupPortalPrevention();
        
        if (lonePiglinListener != null) {
            lonePiglinListener.cleanup();
        }
    }

    /**
     * Cleans up the portal prevention listener if it exists
     */
    private void cleanupPortalPrevention() {
        if (portalListener != null && portalListener.getPortalPreventionListener() != null) {
            portalListener.getPortalPreventionListener().unregister();
        }
    }

    @Override
    public Location getStartLocation() {
        return spawnLocation; // May be null until the piglin spawns
    }

    @Override
    public String getStartTrigger() {
        return "Lost Piglin";
    }

    // Add setter for spawnLocation
    public void setSpawnLocation(Location location) {
        this.spawnLocation = location;
    }

    private QuestLoot createPortalLoot() {
        return () -> Arrays.asList(
            new ItemStack(Material.GOLDEN_APPLE, 3),
            new ItemStack(Material.NETHERITE_SCRAP, 1),
            new ItemStack(Material.DIAMOND, 5),
            new ItemStack(Material.EMERALD, 10)            
        );
    }

    @Override
    public List<Listener> createListenersForState(QuestState state) {
        List<Listener> listeners = new ArrayList<>();
        
        switch (state) {
            case NOT_STARTED:
                listeners.add(lonePiglinListener);
                break;
            case TRIGGER_FOUND:
                listeners.add(new ListenerLonePiglinDeath(this, lonePiglinListener));
                break;
            case QUEST_ACTIVE:
                listeners.add(portalListener);
                break;
            case OBJECTIVE_FOUND:  
                listeners.add(new ListenerEncounterPortalDefeated(this, portalListener, createPortalLoot()));
                break;
        }
        
        return listeners;
    }
    
    @Override
    protected boolean onStart(Player player) {
        debugger.debug("Starting Piglin Far From Home quest for " + player.getName());
        
        // Any quest-specific start logic goes here
        // For this quest, we don't need special handling beyond what AbstractQuest provides
        
        return true;
    }
    
    @Override
    protected boolean onComplete(Player player) {
        debugger.debug("Completing Piglin Far From Home quest for " + player.getName());
        
        // Cleanup quest-specific resources
        cleanupPortalPrevention();
        
        // Award additional rewards if needed beyond the loot
        player.giveExp(500); // Give player some XP as completion reward
        
        return true;
    }
    
    @Override
    public boolean update(Player player) {
        // This quest doesn't need periodic updates, but we could implement progress tracking here
        debugger.debug("Update requested for player: " + player.getName() + ", state: " + getCurrentState());
        return false; // No updates performed
    }
}
