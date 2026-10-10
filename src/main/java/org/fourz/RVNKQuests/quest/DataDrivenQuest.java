package org.fourz.RVNKQuests.quest;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.dto.QuestDTO;
import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.service.IJournalService;
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

    /** Parsed from the {@code notify} metadata block (#2266); the definition is immutable. */
    private final QuestNotifyPolicy notifyPolicy;

    /** Each component's {@code on_advance} rewards, sorted by reward_id (#2267). Absent = none. */
    private final Map<String, List<RewardDTO>> onAdvanceRewards;

    /** Problems found while reading notify / on_advance / once, logged at initialize. */
    private final List<String> engineKeyProblems = new ArrayList<>();

    public DataDrivenQuest(RVNKQuests plugin, QuestDTO definition) {
        super(plugin, definition.questId(), definition.name());
        this.definition = definition;
        this.componentFactory = new QuestComponentFactory(plugin, this);
        this.notifyPolicy = QuestNotifyPolicy.fromMetadata(definition.metadata());
        this.onAdvanceRewards = parseOnAdvance(definition.metadata(), engineKeyProblems);
        engineKeyProblems.addAll(QuestNotifyPolicy.problems(definition.metadata()));
        for (RewardDTO reward : definition.rewards()) {
            String once = reward.metadata().get(org.fourz.RVNKQuests.service.OnceRewards.METADATA_KEY);
            if (once != null && !org.fourz.RVNKQuests.service.OnceRewards.isOnceServer(reward)) {
                engineKeyProblems.add("reward '" + reward.rewardId() + "' once '" + once
                    + "' is not 'server' - ignored, fires on every completion");
            }
        }
    }

    /** Reads every component's {@code on_advance} list. */
    private static Map<String, List<RewardDTO>> parseOnAdvance(Map<String, Object> metadata, List<String> problems) {
        Map<String, List<RewardDTO>> out = new LinkedHashMap<>();
        if (metadata == null || !(metadata.get("components") instanceof Map<?, ?> components)) {
            return out;
        }
        for (Map.Entry<?, ?> e : components.entrySet()) {
            if (!(e.getValue() instanceof Map<?, ?> config)) continue;
            Object raw = config.get(org.fourz.RVNKQuests.reward.OnAdvanceRewards.CONFIG_KEY);
            if (raw == null) continue;
            String componentId = String.valueOf(e.getKey());
            List<RewardDTO> rewards = org.fourz.RVNKQuests.reward.OnAdvanceRewards.parse(componentId, raw, problems);
            if (!rewards.isEmpty()) {
                out.put(componentId, List.copyOf(rewards));
            }
        }
        return out;
    }

    @Override
    public QuestNotifyPolicy getNotifyPolicy() {
        return notifyPolicy;
    }

    /**
     * Author-facing problems with {@code notify}, {@code on_advance} and {@code once} (#2266-#2268).
     * None of them stops the quest loading; each falls back to the old behaviour.
     */
    public List<String> getEngineKeyProblems() {
        return List.copyOf(engineKeyProblems);
    }

    /**
     * The {@code on_advance} rewards of a component, sorted by reward_id (#2267).
     *
     * @param componentId the component id, any case
     * @return the rewards; empty when the component has none
     */
    public List<RewardDTO> getOnAdvanceRewards(String componentId) {
        if (componentId == null) return List.of();
        List<RewardDTO> exact = onAdvanceRewards.get(componentId);
        if (exact != null) return exact;
        for (Map.Entry<String, List<RewardDTO>> e : onAdvanceRewards.entrySet()) {
            if (e.getKey().equalsIgnoreCase(componentId)) return e.getValue();
        }
        return List.of();
    }

    /**
     * The commit hook for a live component listener, or null when it has no {@code on_advance}
     * (#2267). Components pass it to
     * {@link #tryAdvanceStateForPlayer(UUID, QuestState, org.fourz.RVNKQuests.party.PartyBeatContext, java.util.function.Consumer)}
     * through {@link ComponentAdvance}.
     */
    public java.util.function.Consumer<UUID> onAdvanceHook(Listener component) {
        return onAdvanceHook(componentFactory.componentIdOf(component));
    }

    /**
     * The commit hook for a component id, or null when it has no {@code on_advance} (#2267).
     *
     * <p>The hook runs once for each player whose advance on this component committed. It hops to
     * the main thread, claims any {@code once: server} entries, and delivers the rest through the
     * reward service, in reward_id order. An offline player gets nothing, and it is logged.</p>
     */
    public java.util.function.Consumer<UUID> onAdvanceHook(String componentId) {
        List<RewardDTO> rewards = getOnAdvanceRewards(componentId);
        if (rewards.isEmpty()) return null;
        String id = componentId;
        return playerUuid -> runOnMain(() -> deliverOnAdvance(id, playerUuid, rewards));
    }

    private void runOnMain(Runnable task) {
        try {
            plugin.getServer().getScheduler().runTask(plugin, task);
        } catch (RuntimeException e) {
            // The scheduler refuses tasks while the plugin disables.
            logger.warning("on_advance for quest " + questId + " not run - scheduler unavailable: " + e.getMessage());
        }
    }

    private void deliverOnAdvance(String componentId, UUID playerUuid, List<RewardDTO> rewards) {
        Player player = plugin.getServer().getPlayer(playerUuid);
        if (player == null) {
            logger.warning("on_advance for quest " + questId + " component " + componentId
                + " skipped - player " + playerUuid + " went offline after the advance committed");
            return;
        }
        IRewardService rewardService = plugin.getRewardService();
        if (rewardService == null) {
            logger.warning("on_advance for quest " + questId + " component " + componentId
                + " skipped - reward service unavailable");
            return;
        }
        org.fourz.RVNKQuests.service.OnceRewards.claimAndFilter(plugin.getOnceRewardStore(), questId,
                componentId, playerUuid, rewards, logger::warning)
            .thenAccept(toDeliver -> {
                if (toDeliver.isEmpty()) return;
                runOnMain(() -> rewardService.deliverRewards(playerUuid, questId, toDeliver, true)
                    .thenAccept(result -> {
                        logger.debug("on_advance " + questId + "/" + componentId + " for " + player.getName()
                            + ": " + result.successCount() + " delivered, " + result.failureCount() + " failed");
                        if (result.successCount() > 0) {
                            IJournalService journal = plugin.getJournalService();
                            if (journal != null && journal.isAvailable()) {
                                journal.recordRewardClaimed(playerUuid, questId,
                                    "on_advance " + componentId + ": " + result.successCount() + " reward(s) delivered");
                            }
                        }
                    })
                    .exceptionally(ex -> {
                        logger.error("on_advance delivery failed for " + player.getName() + " on quest "
                            + questId + " component " + componentId,
                            ex instanceof Exception x ? x : new RuntimeException(ex));
                        return null;
                    }));
            });
    }

    /**
     * Gets the quest definition DTO.
     * @return The QuestDTO that defines this quest
     */
    public QuestDTO getDefinition() {
        return definition;
    }

    /** Built on first use; the definition is immutable, so the model never goes stale (#2214). */
    private volatile org.fourz.RVNKQuests.placeholder.QuestStepModel stepModel;

    /**
     * The quest's objective steps for {@code %rvnkquests_*%} placeholders (#2214). Immutable and
     * safe to read from any thread.
     */
    public org.fourz.RVNKQuests.placeholder.QuestStepModel getStepModel() {
        org.fourz.RVNKQuests.placeholder.QuestStepModel model = stepModel;
        if (model == null) {
            model = org.fourz.RVNKQuests.placeholder.QuestStepModel.from(definition);
            stepModel = model;
        }
        return model;
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

        // Validate state-name references up front — an invalid name (e.g. "TRIGGERED")
        // silently soft-locks the quest: advance_state falls back to TRIGGER_FOUND and
        // a bad state_mapping key never registers its listeners.
        for (String issue : validateStateNames()) {
            logger.warning("Quest " + questId + " has invalid state reference: " + issue);
        }
        for (String issue : engineKeyProblems) {
            logger.warning("Quest " + questId + ": " + issue);
        }

        // Build listeners for each state from the state_mapping metadata
        for (QuestState state : QuestState.values()) {
            try {
                List<Listener> listeners = componentFactory.createListenersForState(state, definition);
                stateListeners.put(state, listeners);
            } catch (Exception e) {
                logger.error("Failed to create listeners for quest " + questId + " state " + state + " - state will have no listeners", e);
                stateListeners.put(state, List.of());
            }
        }

        long statesWithListeners = stateListeners.entrySet().stream()
            .filter(e -> !e.getValue().isEmpty())
            .count();
        logger.debug("Data-driven quest initialized: " + questId +
            " (states with listeners: " + statesWithListeners + ")");
        if (statesWithListeners == 0 && !definition.metadata().isEmpty()) {
            logger.warning("Quest " + questId + " loaded with zero listeners despite having metadata - check component configs and logs above for errors");
        }
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
            if (rewardService != null && org.fourz.RVNKQuests.service.OnceRewards.anyOnceServer(rewards)) {
                // once: server (#2268): claim first, then deliver what this completion won, in the
                // same order. A quest with no once reward takes the unchanged path below.
                UUID playerUuid = player.getUniqueId();
                org.fourz.RVNKQuests.service.OnceRewards.claimAndFilter(plugin.getOnceRewardStore(), questId,
                        null, playerUuid, rewards, logger::warning)
                    .thenAccept(toDeliver -> {
                        for (RewardDTO r : rewards) {
                            if (org.fourz.RVNKQuests.service.OnceRewards.isOnceServer(r)) {
                                logger.info("once:server reward " + questId + " " + r.rewardId()
                                    + (toDeliver.contains(r) ? " fired by " + player.getName()
                                        : " already fired on this server - skipped for " + player.getName()));
                            }
                        }
                        if (!toDeliver.isEmpty()) {
                            runOnMain(() -> deliverCompletionRewards(player, rewardService, toDeliver));
                        }
                    });
                return true;
            }
            if (rewardService != null) {
                deliverCompletionRewards(player, rewardService, rewards);
            }
        }

        return true;
    }

    /** The unchanged completion delivery: batch, journal on success. */
    private void deliverCompletionRewards(Player player, IRewardService rewardService, List<RewardDTO> rewards) {
        rewardService.deliverRewards(player.getUniqueId(), questId, rewards, true)
            .thenAccept(result -> {
                logger.debug("Rewards delivered for " + player.getName() + " on quest " + questId +
                    ": " + result.successCount() + " successful, " + result.failureCount() + " failed");
                if (result.successCount() > 0) {
                    IJournalService journal = plugin.getJournalService();
                    if (journal != null && journal.isAvailable()) {
                        journal.recordRewardClaimed(player.getUniqueId(), questId,
                            result.successCount() + " reward(s) delivered");
                    }
                }
            })
            .exceptionally(ex -> {
                logger.error("Failed to deliver rewards for " + player.getName() + " on quest " + questId, (Exception) ex);
                return null;
            });
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

    @Override
    protected List<String> getPrerequisiteQuestIds() {
        return definition.prerequisites();
    }

    /**
     * Components that threw during listener construction at initialize() time,
     * keyed by component ID → error message. Surfaced by {@code quest validate}.
     */
    public Map<String, String> getComponentFailures() {
        return componentFactory.getComponentFailures();
    }

    /**
     * Validates that every {@code state_mapping} key and every component
     * {@code advance_state} / {@code required_state} value is a real
     * {@link QuestState} name. Invalid names do not error at runtime —
     * {@code advance_state} falls back to TRIGGER_FOUND and unknown state_mapping
     * keys never register listeners — so they must be caught explicitly.
     *
     * @return list of human-readable problems; empty if all state references are valid
     */
    public List<String> validateStateNames() {
        List<String> issues = new ArrayList<>();
        Map<String, Object> metadata = definition.metadata();
        if (metadata == null || metadata.isEmpty()) {
            return issues;
        }

        Object stateMappingObj = metadata.get("state_mapping");
        if (stateMappingObj instanceof Map<?, ?> stateMapping) {
            for (Object key : stateMapping.keySet()) {
                if (!isValidState(String.valueOf(key))) {
                    issues.add("state_mapping key '" + key + "' is not a valid QuestState");
                }
            }
        }

        Object componentsObj = metadata.get("components");
        if (componentsObj instanceof Map<?, ?> components) {
            for (Map.Entry<?, ?> entry : components.entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> config) {
                    checkStateField(config, "advance_state", String.valueOf(entry.getKey()), issues);
                    checkStateField(config, "required_state", String.valueOf(entry.getKey()), issues);
                }
            }
        }

        return issues;
    }

    private void checkStateField(Map<?, ?> config, String field, String componentId, List<String> issues) {
        Object value = config.get(field);
        if (value != null && !isValidState(String.valueOf(value))) {
            issues.add("component '" + componentId + "' " + field + " '" + value + "' is not a valid QuestState");
        }
    }

    private static boolean isValidState(String name) {
        for (QuestState state : QuestState.values()) {
            if (state.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
