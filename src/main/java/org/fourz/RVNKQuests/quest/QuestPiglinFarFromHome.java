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

public class QuestPiglinFarFromHome implements Quest {
    private final RVNKQuests plugin;
    private QuestState currentState = QuestState.NOT_STARTED;
    private final ListenerLonePiglin lonePiglinListener;
    private final ListenerEncounterPortal portalListener;

    public QuestPiglinFarFromHome(RVNKQuests plugin) {
        this.plugin = plugin;
        
        // Create the listener with custom location parameters
        // You can customize these values or read them from config
        String worldName = plugin.getConfigManager().getConfig().getString("quests.piglin_far_from_home.world", "event");
        double spawnRadius = plugin.getConfigManager().getConfig().getDouble("quests.piglin_far_from_home.spawn_radius", 30.0);
        
        // Initialize the listener with specific world and radius
        this.lonePiglinListener = new ListenerLonePiglin(this, plugin, worldName, null, spawnRadius);  

        Map<EntityType, Integer> portalMobs = new HashMap<>();
        portalMobs.put(EntityType.WITHER_SKELETON, 1);
        portalMobs.put(EntityType.SKELETON, 2);
        portalMobs.put(EntityType.HOGLIN, 2);        
        this.portalListener = new ListenerEncounterPortal(this, portalMobs);
    }

    @Override
    public String getId() {
        return "piglin_far_from_home";
    }

    @Override
    public String getName() {
        return "Piglin Far From Home";
    }

    @Override
    public void initialize() {
        // No initialization needed
    }

    @Override
    public void cleanup() {
        // Remove any remaining entities if needed
    }

    @Override
    public boolean isCompleted(Player player) {
        return currentState == QuestState.COMPLETED;
    }

    @Override
    public QuestState getCurrentState() {
        return currentState;
    }

    @Override
    public void advanceState(QuestState newState) {
        this.currentState = newState;
        
        // Handle state-specific logic
        if (newState == QuestState.QUEST_ACTIVE) {
            lonePiglinListener.cleanup();
        }
        
        plugin.getQuestManager().updateQuestListeners(this);
    }

    @Override
    public Location getLecternLocation() {
        return null; // Not used in this quest
    }

    @Override
    public RVNKQuests getPlugin() {
        return plugin;
    }

    private QuestLoot createPortalLoot() {
        return () -> Arrays.asList(
            new ItemStack(Material.GOLDEN_APPLE, 3),
            new ItemStack(Material.NETHERITE_SCRAP, 1),
            new ItemStack(Material.DIAMOND, 5),
            new ItemStack(Material.EMERALD, 10)            
        );
    }

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
            case OBJECTIVE_FOUND:  // Changed from OBJECTIVE_COMPLETE
                listeners.add(new ListenerEncounterPortalDefeated(this, portalListener, createPortalLoot()));
                break;
        }
        
        return listeners;
    }
}
