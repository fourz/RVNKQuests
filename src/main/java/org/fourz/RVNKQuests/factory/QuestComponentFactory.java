package org.fourz.RVNKQuests.factory;

import org.bukkit.event.Listener;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.dto.ObjectiveDTO;
import org.fourz.RVNKQuests.data.dto.ObjectiveType;
import org.fourz.RVNKQuests.data.dto.QuestDTO;
import org.fourz.RVNKQuests.data.dto.TriggerType;
import org.fourz.RVNKQuests.objective.generic.*;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.trigger.generic.*;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;

/**
 * Factory that creates trigger and objective listeners from quest metadata.
 * Reads state_mapping from QuestDTO metadata to determine which components
 * are active in each quest state.
 *
 * <h3>State mapping format (in QuestDTO.metadata):</h3>
 * <pre>
 * {
 *   "state_mapping": {
 *     "NOT_STARTED": ["trigger_mob_spawn"],
 *     "TRIGGER_FOUND": ["obj_escort", "obj_kill_mob"],
 *     "QUEST_ACTIVE": ["obj_reach_portal"],
 *     "OBJECTIVE_FOUND": ["obj_defeat_encounter"]
 *   },
 *   "components": {
 *     "trigger_mob_spawn": {
 *       "type": "PROXIMITY_MOB_SPAWN",
 *       "entity_type": "PIGLIN",
 *       "custom_name": "Lost Piglin",
 *       "world": "event",
 *       "radius": 50
 *     },
 *     "obj_escort": {
 *       "objective_type": "REACH",
 *       "entity_type": "PIGLIN",
 *       "destination": {"x": 100, "y": 64, "z": 200},
 *       "follow_distance": 3.0
 *     }
 *   }
 * }
 * </pre>
 */
public class QuestComponentFactory {

    private final RVNKQuests plugin;
    private final DataDrivenQuest quest;
    private final LogManager logger;

    /** Cached component listeners by component ID. */
    private final Map<String, Listener> componentCache = new HashMap<>();

    public QuestComponentFactory(RVNKQuests plugin, DataDrivenQuest quest) {
        this.plugin = plugin;
        this.quest = quest;
        this.logger = LogManager.getInstance(plugin, "QuestComponentFactory");
    }

    /**
     * Creates listeners for a given quest state based on the state_mapping metadata.
     *
     * @param state The quest state
     * @param definition The quest definition containing metadata
     * @return List of listeners for this state
     */
    @SuppressWarnings("unchecked")
    public List<Listener> createListenersForState(QuestState state, QuestDTO definition) {
        Map<String, Object> metadata = definition.metadata();
        if (metadata == null || metadata.isEmpty()) {
            return List.of();
        }

        // Get state_mapping
        Object stateMappingObj = metadata.get("state_mapping");
        if (!(stateMappingObj instanceof Map)) {
            return List.of();
        }

        Map<String, Object> stateMapping = (Map<String, Object>) stateMappingObj;
        Object componentIdsObj = stateMapping.get(state.name());
        if (!(componentIdsObj instanceof List)) {
            return List.of();
        }

        List<String> componentIds = (List<String>) componentIdsObj;

        // Get components config
        Object componentsObj = metadata.get("components");
        if (!(componentsObj instanceof Map)) {
            logger.warning("Quest " + definition.questId() + " has state_mapping but no components config");
            return List.of();
        }

        Map<String, Object> components = (Map<String, Object>) componentsObj;

        List<Listener> listeners = new ArrayList<>();
        for (String componentId : componentIds) {
            Listener listener = getOrCreateComponent(componentId, components, definition);
            if (listener != null) {
                listeners.add(listener);
            }
        }

        return listeners;
    }

    @SuppressWarnings("unchecked")
    private Listener getOrCreateComponent(String componentId, Map<String, Object> components, QuestDTO definition) {
        // Return cached component if already created
        if (componentCache.containsKey(componentId)) {
            return componentCache.get(componentId);
        }

        Object configObj = components.get(componentId);
        if (!(configObj instanceof Map)) {
            logger.warning("Component config not found for: " + componentId + " in quest " + definition.questId());
            return null;
        }

        Map<String, Object> config = (Map<String, Object>) configObj;
        Listener listener = null;

        // Determine if this is a trigger or objective component
        String typeStr = getStringConfig(config, "type", null);
        String objectiveTypeStr = getStringConfig(config, "objective_type", null);

        if (typeStr != null) {
            // This is a trigger component
            listener = createTriggerComponent(componentId, typeStr, config);
        } else if (objectiveTypeStr != null) {
            // This is an objective component
            listener = createObjectiveComponent(componentId, objectiveTypeStr, config);
        } else {
            logger.warning("Component " + componentId + " has neither 'type' (trigger) nor 'objective_type' (objective)");
        }

        if (listener != null) {
            componentCache.put(componentId, listener);
        }

        return listener;
    }

    private Listener createTriggerComponent(String componentId, String typeStr, Map<String, Object> config) {
        TriggerType type;
        try {
            type = TriggerType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown trigger type: " + typeStr + " for component " + componentId);
            return null;
        }

        return switch (type) {
            case PROXIMITY_MOB_SPAWN -> new GenericMobSpawnTrigger(plugin, quest, config);
            case STRUCTURE_INTERACT -> new GenericStructureInteractTrigger(plugin, quest, config);
            case ENTITY_PROXIMITY -> new GenericEntityProximityTrigger(plugin, quest, config);
            case ITEM_DISCOVERY -> new GenericItemDiscoveryTrigger(plugin, quest, config);
            case COMMAND, WORLD_EVENT, CUSTOM -> {
                logger.debug("Trigger type " + type + " not yet implemented for component " + componentId);
                yield null;
            }
        };
    }

    private Listener createObjectiveComponent(String componentId, String objectiveTypeStr, Map<String, Object> config) {
        ObjectiveType type;
        try {
            type = ObjectiveType.valueOf(objectiveTypeStr);
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown objective type: " + objectiveTypeStr + " for component " + componentId);
            return null;
        }

        return switch (type) {
            case KILL -> new GenericKillObjective(plugin, quest, config);
            case REACH -> new GenericReachObjective(plugin, quest, config);
            case DISCOVER -> new GenericDiscoverObjective(plugin, quest, config);
            case INTERACT -> new GenericInteractObjective(plugin, quest, config);
            default -> {
                logger.debug("Objective type " + type + " not yet implemented for component " + componentId);
                yield null;
            }
        };
    }

    // ==================== Config Helper Methods ====================

    /**
     * Gets a string from a config map, with a default value.
     */
    public static String getStringConfig(Map<String, Object> config, String key, String defaultValue) {
        Object val = config.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    /**
     * Gets an int from a config map (handles Number and String values).
     */
    public static int getIntConfig(Map<String, Object> config, String key, int defaultValue) {
        Object val = config.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        if (val instanceof String) {
            try {
                return Integer.parseInt((String) val);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Gets a double from a config map.
     */
    public static double getDoubleConfig(Map<String, Object> config, String key, double defaultValue) {
        Object val = config.get(key);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        if (val instanceof String) {
            try {
                return Double.parseDouble((String) val);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Gets a boolean from a config map.
     */
    public static boolean getBoolConfig(Map<String, Object> config, String key, boolean defaultValue) {
        Object val = config.get(key);
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        if (val instanceof String) {
            return Boolean.parseBoolean((String) val);
        }
        return defaultValue;
    }
}
