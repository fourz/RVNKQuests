package org.fourz.RVNKQuests.quest;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.service.IQuestProgressService;
import org.fourz.RVNKQuests.trigger.ListenerProphecyDiscovery;
import org.fourz.RVNKQuests.trigger.ListenerProphecyVisions;
import org.fourz.RVNKQuests.trigger.ListenerQuestPillarStart;
import org.fourz.RVNKQuests.trigger.ListenerEventPopulated;
import org.fourz.RVNKQuests.objective.ListenerFirstCityChoice;
import org.fourz.RVNKQuests.objective.ListenerQuestBookPlacer;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Quest: The First City Prophecy
 *
 * <p>Note: This quest has been updated to support per-player state tracking.
 * The currentState field is deprecated; use {@link #getStateForPlayer(UUID)} instead.</p>
 */
public class QuestFirstCityProphecy implements Quest {
    private final RVNKQuests plugin;
    private final LogManager logger;
    private Location lecternLocation;
    private ListenerProphecyDiscovery prophecyDiscovery;

    public QuestFirstCityProphecy(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
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
        logger.debug("Building quest beacon");
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
        logger.debug("Initializing First City Prophecy quest");
        // No longer creating pillar here - will be triggered by EventPopulated
    }

    @Override
    public void cleanup() {
        logger.debug("Cleaning up First City Prophecy quest");
        if (lecternLocation != null) {
            lecternLocation.getBlock().setType(Material.AIR);
        }
    }

    @Override
    public boolean isCompleted(Player player) {
        if (player == null) return false;
        return getStateForPlayer(player) == QuestState.COMPLETED;
    }

    @Override
    public CompletableFuture<QuestState> getStateForPlayer(UUID playerUuid) {
        IQuestProgressService service = plugin.getQuestProgressService();
        if (service == null) {
            logger.warning("QuestProgressService not available - returning NOT_STARTED");
            return CompletableFuture.completedFuture(QuestState.NOT_STARTED);
        }
        return service.getQuestState(playerUuid, getId());
    }

    @Override
    public QuestState getStateForPlayer(Player player) {
        if (player == null) {
            return QuestState.NOT_STARTED;
        }
        try {
            return getStateForPlayer(player.getUniqueId()).join();
        } catch (Exception e) {
            logger.warning("Failed to get state for player " + player.getName() + ": " + e.getMessage());
            return QuestState.NOT_STARTED;
        }
    }

    @Override
    @Deprecated
    public QuestState getCurrentState() {
        logger.debug("getCurrentState() called - use getStateForPlayer() instead");
        return QuestState.NOT_STARTED;
    }

    @Override
    public CompletableFuture<Void> advanceStateForPlayer(UUID playerUuid, QuestState newState) {
        IQuestProgressService service = plugin.getQuestProgressService();
        if (service == null) {
            logger.warning("QuestProgressService not available - cannot advance state");
            return CompletableFuture.completedFuture(null);
        }

        return getStateForPlayer(playerUuid)
            .thenCompose(currentState -> {
                logger.debug("Advancing state for " + playerUuid + " from " + currentState + " to " + newState);
                return service.updateQuestState(playerUuid, getId(), newState);
            })
            .thenAccept(progress -> {
                plugin.getQuestManager().updateQuestListenersForPlayer(this, playerUuid);
            });
    }

    @Override
    @Deprecated
    public void advanceState(QuestState newState) {
        logger.warning("advanceState() called without player - use advanceStateForPlayer() instead");
    }

    @Override
    public Location getStartLocation() {
        return prophecyDiscovery != null ? prophecyDiscovery.getLecternLocation() : lecternLocation;
    }

    @Override
    public String getStartTrigger() {
        return "Prophecy Lectern";
    }

    public boolean isValidSettlementLocation(Location loc) {
        logger.debug("Checking settlement location validity at: " + loc);
        World world = loc.getWorld();
        int highestY = world.getHighestBlockYAt(loc);

        if (highestY < 100) {
            logger.debug("Location rejected: Height " + highestY + " is too low");
            return false;
        }

        for (int x = -16; x <= 16; x++) {
            for (int z = -16; x <= 16; z++) {
                Location check = loc.clone().add(x, 0, z);
                if (check.getBlock().getType() == Material.WATER) {
                    logger.debug("Location accepted: Found water source nearby");
                    return true;
                }
            }
        }
        logger.debug("Location rejected: No water source found nearby");
        return false;
    }

    @Override
    public List<Listener> createListenersForState(QuestState state) {
        List<Listener> listeners = new ArrayList<>();
        switch (state) {
            case NOT_STARTED:
                listeners.add(new ListenerEventPopulated(this));
                break;
            case TRIGGER_FOUND:
                prophecyDiscovery = new ListenerProphecyDiscovery(this, lecternLocation);
                listeners.add(prophecyDiscovery);
                break;
            case QUEST_ACTIVE:
                listeners.add(new ListenerProphecyVisions(plugin, this));
                break;
            case OBJECTIVE_FOUND:
                listeners.add(new ListenerFirstCityChoice(plugin, this));
                break;
            case COMPLETED:
                // No listeners needed for completed state
                break;
        }
        return listeners;
    }
}
