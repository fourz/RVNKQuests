package org.fourz.RVNKQuests.data.dto;

import java.util.Map;
import java.util.Objects;

/**
 * Represents a condition that must be met for an objective to be available or count.
 * Conditions enable complex quest logic like time-based, location-based, or
 * state-dependent objectives.
 *
 * <h3>Condition Types</h3>
 * <ul>
 *   <li><b>TIME_RANGE</b> - Active during specific time windows</li>
 *   <li><b>LOCATION</b> - Must be in specific area/world</li>
 *   <li><b>QUEST_STATE</b> - Another quest must be in specific state</li>
 *   <li><b>OBJECTIVE_COMPLETE</b> - Another objective must be complete</li>
 *   <li><b>PERMISSION</b> - Player must have permission</li>
 *   <li><b>ITEM_IN_INVENTORY</b> - Player must possess item</li>
 *   <li><b>CUSTOM</b> - Plugin-defined condition</li>
 * </ul>
 *
 * @param conditionId Unique identifier for this condition
 * @param type The condition type
 * @param parameters Type-specific parameters (e.g., location coords, time range)
 * @param description Human-readable description of the condition
 * @param inverted If true, condition passes when underlying check fails
 */
public record ObjectiveCondition(
    String conditionId,
    ConditionType type,
    Map<String, String> parameters,
    String description,
    boolean inverted
) {
    /**
     * Enumeration of supported condition types.
     */
    public enum ConditionType {
        /**
         * Condition based on in-game time or real-world time.
         * Parameters: startTime, endTime, isRealTime
         */
        TIME_RANGE,

        /**
         * Condition based on player location.
         * Parameters: world, x, y, z, radius (or region name)
         */
        LOCATION,

        /**
         * Condition based on another quest's state.
         * Parameters: questId, requiredState
         */
        QUEST_STATE,

        /**
         * Condition based on another objective's completion.
         * Parameters: questId (optional), objectiveId
         */
        OBJECTIVE_COMPLETE,

        /**
         * Condition based on player permission.
         * Parameters: permission
         */
        PERMISSION,

        /**
         * Condition based on item in player inventory.
         * Parameters: itemType, minAmount (optional)
         */
        ITEM_IN_INVENTORY,

        /**
         * Condition based on player level.
         * Parameters: minLevel (optional), maxLevel (optional)
         */
        PLAYER_LEVEL,

        /**
         * Condition based on weather.
         * Parameters: weather (CLEAR, RAIN, THUNDER)
         */
        WEATHER,

        /**
         * Condition requiring specific tool/equipment.
         * Parameters: equipmentSlot, itemType
         */
        EQUIPMENT,

        /**
         * Custom condition with plugin-defined evaluation.
         * Parameters: evaluatorClass, custom params
         */
        CUSTOM
    }

    /**
     * Compact constructor with validation.
     */
    public ObjectiveCondition {
        Objects.requireNonNull(conditionId, "conditionId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    /**
     * Creates a time-based condition.
     *
     * @param conditionId The condition identifier
     * @param startTime Start of valid time window (0-24000 for game ticks)
     * @param endTime End of valid time window
     * @return A new TIME_RANGE condition
     */
    public static ObjectiveCondition timeRange(String conditionId, int startTime, int endTime) {
        return new ObjectiveCondition(
            conditionId,
            ConditionType.TIME_RANGE,
            Map.of("startTime", String.valueOf(startTime), "endTime", String.valueOf(endTime)),
            "Active from time " + startTime + " to " + endTime,
            false
        );
    }

    /**
     * Creates a location-based condition.
     *
     * @param conditionId The condition identifier
     * @param world The world name
     * @param x X coordinate of center
     * @param y Y coordinate of center
     * @param z Z coordinate of center
     * @param radius Radius around center point
     * @return A new LOCATION condition
     */
    public static ObjectiveCondition location(String conditionId, String world,
                                               double x, double y, double z, double radius) {
        return new ObjectiveCondition(
            conditionId,
            ConditionType.LOCATION,
            Map.of(
                "world", world,
                "x", String.valueOf(x),
                "y", String.valueOf(y),
                "z", String.valueOf(z),
                "radius", String.valueOf(radius)
            ),
            "Must be within " + radius + " blocks of location",
            false
        );
    }

    /**
     * Creates a quest-state condition.
     *
     * @param conditionId The condition identifier
     * @param questId The quest that must be in required state
     * @param requiredState The required state (e.g., "COMPLETED")
     * @return A new QUEST_STATE condition
     */
    public static ObjectiveCondition questState(String conditionId, String questId, String requiredState) {
        return new ObjectiveCondition(
            conditionId,
            ConditionType.QUEST_STATE,
            Map.of("questId", questId, "requiredState", requiredState),
            "Requires quest '" + questId + "' to be " + requiredState,
            false
        );
    }

    /**
     * Creates an objective-complete condition.
     *
     * @param conditionId The condition identifier
     * @param objectiveId The objective that must be complete
     * @return A new OBJECTIVE_COMPLETE condition
     */
    public static ObjectiveCondition objectiveComplete(String conditionId, String objectiveId) {
        return new ObjectiveCondition(
            conditionId,
            ConditionType.OBJECTIVE_COMPLETE,
            Map.of("objectiveId", objectiveId),
            "Requires objective '" + objectiveId + "' to be complete",
            false
        );
    }

    /**
     * Creates an item-in-inventory condition.
     *
     * @param conditionId The condition identifier
     * @param itemType The item type required
     * @param minAmount Minimum amount required
     * @return A new ITEM_IN_INVENTORY condition
     */
    public static ObjectiveCondition itemRequired(String conditionId, String itemType, int minAmount) {
        return new ObjectiveCondition(
            conditionId,
            ConditionType.ITEM_IN_INVENTORY,
            Map.of("itemType", itemType, "minAmount", String.valueOf(minAmount)),
            "Requires " + minAmount + "x " + itemType,
            false
        );
    }

    /**
     * Creates an inverted copy of this condition.
     * Inverted conditions pass when the underlying check fails.
     *
     * @return A new condition that is the logical inverse
     */
    public ObjectiveCondition negate() {
        return new ObjectiveCondition(conditionId, type, parameters, description, !inverted);
    }

    /**
     * Checks if this condition is inverted.
     *
     * @return true if condition logic is inverted
     */
    public boolean isInverted() {
        return inverted;
    }

    /**
     * Creates a copy with updated description.
     *
     * @param newDescription The new description
     * @return A new condition with updated description
     */
    public ObjectiveCondition withDescription(String newDescription) {
        return new ObjectiveCondition(conditionId, type, parameters, newDescription, inverted);
    }

    /**
     * Gets a parameter value, or default if not present.
     *
     * @param key The parameter key
     * @param defaultValue The default value if key not found
     * @return The parameter value or default
     */
    public String getParameter(String key, String defaultValue) {
        return parameters.getOrDefault(key, defaultValue);
    }

    /**
     * Gets a parameter value as integer.
     *
     * @param key The parameter key
     * @param defaultValue The default value if key not found or not a number
     * @return The parameter value as int
     */
    public int getIntParameter(String key, int defaultValue) {
        String value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Gets a parameter value as double.
     *
     * @param key The parameter key
     * @param defaultValue The default value if key not found or not a number
     * @return The parameter value as double
     */
    public double getDoubleParameter(String key, double defaultValue) {
        String value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Builder for creating ObjectiveCondition instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for ObjectiveCondition.
     */
    public static class Builder {
        private String conditionId;
        private ConditionType type;
        private Map<String, String> parameters = Map.of();
        private String description;
        private boolean inverted = false;

        public Builder conditionId(String conditionId) {
            this.conditionId = conditionId;
            return this;
        }

        public Builder type(ConditionType type) {
            this.type = type;
            return this;
        }

        public Builder parameters(Map<String, String> parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder parameter(String key, String value) {
            this.parameters = new java.util.HashMap<>(this.parameters);
            this.parameters.put(key, value);
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder inverted(boolean inverted) {
            this.inverted = inverted;
            return this;
        }

        public ObjectiveCondition build() {
            return new ObjectiveCondition(conditionId, type, parameters, description, inverted);
        }
    }
}
