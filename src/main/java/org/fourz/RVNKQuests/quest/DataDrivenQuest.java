package org.fourz.RVNKQuests.quest;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.dto.QuestDTO;
import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.service.IRewardService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A quest driven entirely by metadata from a QuestDTO definition.
 * Replaces hardcoded quest classes with a generic, data-driven implementation.
 *
 * <p>Components (triggers, objectives) are created by {@link QuestComponentFactory}
 * based on the quest's metadata. Inter-component communication happens via the
 * shared {@code runtimeContext} map.</p>
 */
public class DataDrivenQuest extends AbstractQuest {

    private final QuestDTO definition;
    private final QuestComponentFactory componentFactory;

    /** Shared runtime context for inter-component state (e.g., spawned entity refs). */
    private final Map<String, Object> runtimeContext = new ConcurrentHashMap<>();

    /** Cached listeners per state, created during initialize(). */
    private final Map<QuestState, List<Listener>> stateListeners = new EnumMap<>(QuestState.class);

    public DataDrivenQuest(RVNKQuests plugin, QuestDTO definition) {
        super(plugin, definition.questId(), definition.name());
        this.definition = definition;
        this.componentFactory = new QuestComponentFactory(plugin, this);
    }

    /**
     * Gets the quest definition DTO.
     * @return The QuestDTO that defines this quest
     */
    public QuestDTO getDefinition() {
        return definition;
    }

    /**
     * Gets the shared runtime context for inter-component communication.
     * Components can store/retrieve runtime state (e.g., spawned entity references).
     * @return The mutable runtime context map
     */
    public Map<String, Object> getRuntimeContext() {
        return runtimeContext;
    }

    /**
     * Convenience method to put a value in the runtime context.
     */
    public void setContext(String key, Object value) {
        runtimeContext.put(key, value);
    }

    /**
     * Convenience method to get a typed value from the runtime context.
     */
    @SuppressWarnings("unchecked")
    public <T> T getContext(String key, Class<T> type) {
        Object val = runtimeContext.get(key);
        if (val != null && type.isInstance(val)) {
            return (T) val;
        }
        return null;
    }

    @Override
    public void initialize() {
        logger.debug("Initializing data-driven quest: " + questId);

        // Build listeners for each state from the state_mapping metadata
        for (QuestState state : QuestState.values()) {
            List<Listener> listeners = componentFactory.createListenersForState(state, definition);
            stateListeners.put(state, listeners);
        }

        logger.debug("Data-driven quest initialized: " + questId +
            " (states with listeners: " + stateListeners.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .count() + ")");
    }

    @Override
    public void cleanup() {
        logger.debug("Cleaning up data-driven quest: " + questId);
        stateListeners.clear();
        runtimeContext.clear();
    }

    @Override
    public List<Listener> createListenersForState(QuestState state) {
        List<Listener> listeners = stateListeners.get(state);
        return listeners != null ? new ArrayList<>(listeners) : List.of();
    }

    @Override
    protected boolean onStart(Player player) {
        logger.debug("Quest started for " + player.getName() + ": " + questId);
        return true;
    }

    @Override
    protected boolean onComplete(Player player) {
        logger.debug("Quest completed for " + player.getName() + ": " + questId);

        // Deliver rewards from the quest definition
        List<RewardDTO> rewards = definition.rewards();
        if (!rewards.isEmpty()) {
            IRewardService rewardService = plugin.getRewardService();
            if (rewardService != null) {
                rewardService.deliverRewards(player.getUniqueId(), questId, rewards, true)
                    .thenAccept(result -> {
                        logger.debug("Rewards delivered for " + player.getName() + " on quest " + questId +
                            ": " + result.successCount() + " successful, " + result.failureCount() + " failed");
                    })
                    .exceptionally(ex -> {
                        logger.error("Failed to deliver rewards for " + player.getName() + " on quest " + questId, (Exception) ex);
                        return null;
                    });
            }
        }

        return true;
    }

    @Override
    public boolean update(Player player) {
        // Data-driven quests handle progress via their component listeners
        return true;
    }

    @Override
    public Location getStartLocation() {
        // Read from metadata if defined
        Map<String, Object> meta = definition.metadata();
        if (meta.containsKey("start_location")) {
            Object loc = meta.get("start_location");
            if (loc instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> locMap = (Map<String, Object>) loc;
                try {
                    String worldName = (String) locMap.getOrDefault("world", "world");
                    double x = ((Number) locMap.get("x")).doubleValue();
                    double y = ((Number) locMap.get("y")).doubleValue();
                    double z = ((Number) locMap.get("z")).doubleValue();
                    org.bukkit.World world = plugin.getServer().getWorld(worldName);
                    if (world != null) {
                        return new Location(world, x, y, z);
                    }
                } catch (Exception e) {
                    logger.debug("Failed to parse start_location from metadata: " + e.getMessage());
                }
            }
        }
        return null;
    }

    @Override
    public String getStartTrigger() {
        Object trigger = definition.metadata().get("start_trigger");
        return trigger != null ? trigger.toString() : definition.name();
    }
}
