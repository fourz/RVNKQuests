package org.fourz.RVNKQuests.data.dto;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Enhanced objective definition with condition support.
 * Extends the concept of ObjectiveDTO to include activation conditions,
 * visibility conditions, and enhanced metadata for complex quest logic.
 *
 * <h3>Condition Types</h3>
 * <ul>
 *   <li><b>activationConditions</b> - Must be met for objective to count progress</li>
 *   <li><b>visibilityConditions</b> - Must be met for objective to be displayed</li>
 * </ul>
 *
 * <h3>Usage Examples</h3>
 * <pre>
 * // Night-only kill objective
 * EnhancedObjectiveDTO.builder()
 *     .objectiveId("kill_zombies_night")
 *     .type(ObjectiveType.KILL)
 *     .target("ZOMBIE")
 *     .requiredAmount(10)
 *     .activationCondition(ObjectiveCondition.timeRange("night", 13000, 23000))
 *     .build();
 *
 * // Location-based delivery
 * EnhancedObjectiveDTO.builder()
 *     .objectiveId("deliver_gems")
 *     .type(ObjectiveType.DELIVER)
 *     .target("DIAMOND")
 *     .requiredAmount(5)
 *     .activationCondition(ObjectiveCondition.location("at_merchant", "world", 100, 64, 200, 5))
 *     .build();
 * </pre>
 *
 * @param objectiveId Unique identifier
 * @param type Objective type
 * @param target Target identifier (entity, item, location, etc.)
 * @param requiredAmount Amount required for completion
 * @param description Human-readable description
 * @param order Display/processing order
 * @param activationConditions Conditions for progress to count
 * @param visibilityConditions Conditions for objective to be visible
 * @param optional If true, objective is not required for quest completion
 * @param hidden If true, objective is hidden until visibilityConditions met
 * @param metadata Additional key-value metadata
 */
public record EnhancedObjectiveDTO(
    String objectiveId,
    ObjectiveType type,
    String target,
    int requiredAmount,
    String description,
    int order,
    List<ObjectiveCondition> activationConditions,
    List<ObjectiveCondition> visibilityConditions,
    boolean optional,
    boolean hidden,
    Map<String, String> metadata
) {
    /**
     * Compact constructor with validation.
     */
    public EnhancedObjectiveDTO {
        Objects.requireNonNull(objectiveId, "objectiveId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        if (requiredAmount < 1) {
            requiredAmount = 1;
        }
        if (order < 0) {
            order = 0;
        }

        // Defensive copies
        activationConditions = activationConditions == null ? List.of() : List.copyOf(activationConditions);
        visibilityConditions = visibilityConditions == null ? List.of() : List.copyOf(visibilityConditions);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * Creates from a basic ObjectiveDTO.
     *
     * @param dto The basic objective DTO
     * @return An enhanced version with no conditions
     */
    public static EnhancedObjectiveDTO from(ObjectiveDTO dto) {
        return new EnhancedObjectiveDTO(
            dto.objectiveId(),
            dto.type(),
            dto.target(),
            dto.requiredAmount(),
            dto.description(),
            dto.order(),
            List.of(),
            List.of(),
            false,
            false,
            dto.metadata()
        );
    }

    /**
     * Converts to a basic ObjectiveDTO (loses conditions).
     *
     * @return A basic ObjectiveDTO
     */
    public ObjectiveDTO toBasic() {
        return new ObjectiveDTO(
            objectiveId,
            type,
            target,
            requiredAmount,
            description,
            order,
            metadata
        );
    }

    /**
     * Checks if this objective has activation conditions.
     *
     * @return true if activation conditions exist
     */
    public boolean hasActivationConditions() {
        return !activationConditions.isEmpty();
    }

    /**
     * Checks if this objective has visibility conditions.
     *
     * @return true if visibility conditions exist
     */
    public boolean hasVisibilityConditions() {
        return !visibilityConditions.isEmpty();
    }

    /**
     * Creates a copy with added activation condition.
     *
     * @param condition The condition to add
     * @return A new DTO with the condition added
     */
    public EnhancedObjectiveDTO withActivationCondition(ObjectiveCondition condition) {
        List<ObjectiveCondition> newConditions = new java.util.ArrayList<>(activationConditions);
        newConditions.add(condition);
        return new EnhancedObjectiveDTO(objectiveId, type, target, requiredAmount, description,
            order, newConditions, visibilityConditions, optional, hidden, metadata);
    }

    /**
     * Creates a copy with added visibility condition.
     *
     * @param condition The condition to add
     * @return A new DTO with the condition added
     */
    public EnhancedObjectiveDTO withVisibilityCondition(ObjectiveCondition condition) {
        List<ObjectiveCondition> newConditions = new java.util.ArrayList<>(visibilityConditions);
        newConditions.add(condition);
        return new EnhancedObjectiveDTO(objectiveId, type, target, requiredAmount, description,
            order, activationConditions, newConditions, optional, hidden, metadata);
    }

    /**
     * Creates a copy marked as optional.
     *
     * @return A new optional objective
     */
    public EnhancedObjectiveDTO asOptional() {
        return new EnhancedObjectiveDTO(objectiveId, type, target, requiredAmount, description,
            order, activationConditions, visibilityConditions, true, hidden, metadata);
    }

    /**
     * Creates a copy marked as hidden.
     *
     * @return A new hidden objective
     */
    public EnhancedObjectiveDTO asHidden() {
        return new EnhancedObjectiveDTO(objectiveId, type, target, requiredAmount, description,
            order, activationConditions, visibilityConditions, optional, true, metadata);
    }

    /**
     * Creates a copy with updated description.
     *
     * @param newDescription The new description
     * @return A new DTO with updated description
     */
    public EnhancedObjectiveDTO withDescription(String newDescription) {
        return new EnhancedObjectiveDTO(objectiveId, type, target, requiredAmount, newDescription,
            order, activationConditions, visibilityConditions, optional, hidden, metadata);
    }

    /**
     * Builder for EnhancedObjectiveDTO.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for EnhancedObjectiveDTO.
     */
    public static class Builder {
        private String objectiveId;
        private ObjectiveType type;
        private String target;
        private int requiredAmount = 1;
        private String description;
        private int order = 0;
        private final List<ObjectiveCondition> activationConditions = new java.util.ArrayList<>();
        private final List<ObjectiveCondition> visibilityConditions = new java.util.ArrayList<>();
        private boolean optional = false;
        private boolean hidden = false;
        private Map<String, String> metadata = Map.of();

        public Builder objectiveId(String objectiveId) {
            this.objectiveId = objectiveId;
            return this;
        }

        public Builder type(ObjectiveType type) {
            this.type = type;
            return this;
        }

        public Builder target(String target) {
            this.target = target;
            return this;
        }

        public Builder requiredAmount(int requiredAmount) {
            this.requiredAmount = requiredAmount;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public Builder activationCondition(ObjectiveCondition condition) {
            this.activationConditions.add(condition);
            return this;
        }

        public Builder activationConditions(List<ObjectiveCondition> conditions) {
            this.activationConditions.addAll(conditions);
            return this;
        }

        public Builder visibilityCondition(ObjectiveCondition condition) {
            this.visibilityConditions.add(condition);
            return this;
        }

        public Builder visibilityConditions(List<ObjectiveCondition> conditions) {
            this.visibilityConditions.addAll(conditions);
            return this;
        }

        public Builder optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        public Builder hidden(boolean hidden) {
            this.hidden = hidden;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public EnhancedObjectiveDTO build() {
            return new EnhancedObjectiveDTO(objectiveId, type, target, requiredAmount, description,
                order, activationConditions, visibilityConditions, optional, hidden, metadata);
        }
    }
}
