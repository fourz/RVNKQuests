package org.fourz.RVNKQuests.quest;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.objective.ListenerQuestPillarStart;
import org.fourz.RVNKQuests.trigger.ListenerProphecyDiscovery;
import org.fourz.RVNKQuests.trigger.ListenerProphecyVisions;
import org.fourz.RVNKQuests.trigger.ListenerEventPopulated;
import org.fourz.RVNKQuests.objective.ListenerFirstCityChoice;
import org.fourz.RVNKQuests.objective.ListenerQuestBookPlacer;
import org.fourz.RVNKQuests.util.Debug;

import java.util.ArrayList;
import java.util.List;

public class QuestFirstCityProphecy implements Quest {
    private static final String CLASS_NAME = "QuestFirstCityProphecy";
    private final RVNKQuests plugin;
    private final Debug debugger;
    private QuestState currentState = QuestState.NOT_STARTED;
    private Location lecternLocation;

    public QuestFirstCityProphecy(RVNKQuests plugin) {
        this.plugin = plugin;
        this.debugger = new Debug(plugin, CLASS_NAME, plugin.getDebugger().getLogLevel()) {};
    }

    @Override
    public String getId() {
        return "first_city_prophecy";
    }

    @Override
    public String getName() {
        return "The First City Prophecy";
    }

    @Override
    public RVNKQuests getPlugin() {
        return plugin;
    }

    public void buildQuestBeacon() {
        debugger.debug("Building quest beacon");
        ListenerQuestPillarStart pillarStarter = new ListenerQuestPillarStart(plugin);
        this.lecternLocation = pillarStarter.buildQuestBeacon();

        
        // Register the book placer listener
        plugin.getServer().getPluginManager().registerEvents(
            new ListenerQuestBookPlacer(plugin, this.lecternLocation),
            plugin
        );
    }

    @Override
    public void initialize() {
        debugger.debug("Initializing First City Prophecy quest");
        // No longer creating pillar here - will be triggered by EventPopulated
    }

    @Override
    public void cleanup() {
        debugger.debug("Cleaning up First City Prophecy quest");
        if (lecternLocation != null) {
            lecternLocation.getBlock().setType(Material.AIR);
        }
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
        debugger.debug("Advancing quest state from " + currentState + " to " + newState);
        this.currentState = newState;
        plugin.getQuestManager().updateQuestListeners(this);
    }

    @Override
    public Location getLecternLocation() {
        return lecternLocation;
    }

    public boolean isValidSettlementLocation(Location loc) {
        debugger.debug("Checking settlement location validity at: " + loc);
        World world = loc.getWorld();
        int highestY = world.getHighestBlockYAt(loc);
        
        if (highestY < 100) {
            debugger.debug("Location rejected: Height " + highestY + " is too low");
            return false;
        }
        
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; x <= 16; z++) {
                Location check = loc.clone().add(x, 0, z);
                if (check.getBlock().getType() == Material.WATER) {
                    debugger.debug("Location accepted: Found water source nearby");
                    return true;
                }
            }
        }
        debugger.debug("Location rejected: No water source found nearby");
        return false;
    }

    public List<Listener> createListenersForState(QuestState state) {
        List<Listener> listeners = new ArrayList<>();
        switch (state) {
            case NOT_STARTED:
                listeners.add(new ListenerEventPopulated(this));
                break;
            case TRIGGER_FOUND:
                listeners.add(new ListenerProphecyDiscovery(this));
                break;
            case QUEST_ACTIVE:
                listeners.add(new ListenerProphecyVisions(plugin, this));
                break;
            case OBJECTIVE_COMPLETE:
                listeners.add(new ListenerFirstCityChoice(plugin, this));
                break;
        }
        return listeners;
    }
}
