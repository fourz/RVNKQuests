package org.fourz.RVNKQuests.data.dto;

import java.util.Map;
import java.util.Objects;

/**
 * Data Transfer Object for quest objective definitions.
 * Immutable and thread-safe for cross-boundary data transfer.
 *
 * <p>Represents the definition of an objective within a quest,
 * not the player's progress on it. For progress tracking, see
 * {@link QuestObjectiveProgressDTO}.</p>
 */
public record ObjectiveDTO(
    String objectiveId,
    ObjectiveType type,
    String target,
    int requiredAmount,
    String description,
    int order,
    Map<String, String> metadata
) {
    /**
     * Compact constructor with validation and defensive copies.
     */
    public ObjectiveDTO {
        Objects.requireNonNull(objectiveId, "objectiveId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        // Ensure positive required amount
        if (requiredAmount < 1) {
            requiredAmount = 1;
        }

        // Default order if not specified
        if (order < 0) {
            order = 0;
        }

        // Defensive copy for mutable collection
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * Creates a simple objective with minimal configuration.
     *
     * @param objectiveId The unique objective identifier
     * @param type The objective type
     * @param target The target (entity type, item type, etc.)
     * @param requiredAmount The amount required to complete
     * @return A new ObjectiveDTO
     */
    public static ObjectiveDTO create(String objectiveId, ObjectiveType type,
                                       String target, int requiredAmount) {
        return new ObjectiveDTO(
            objectiveId,
            type,
            target,
            requiredAmount,
            null,
            0,
            Map.of()
        );
    }

    /**
     * Creates a copy with updated description.
     *
     * @param newDescription The new description
     * @return A new ObjectiveDTO with the updated description
     */
    public ObjectiveDTO withDescription(String newDescription) {
        return new ObjectiveDTO(objectiveId, type, target, requiredAmount,
                                newDescription, order, metadata);
    }

    /**
     * Creates a copy with updated order.
     *
     * @param newOrder The new order value
     * @return A new ObjectiveDTO with the updated order
     */
    public ObjectiveDTO withOrder(int newOrder) {
        return new ObjectiveDTO(objectiveId, type, target, requiredAmount,
                                description, newOrder, metadata);
    }

    /**
     * Creates a copy with updated metadata.
     *
     * @param newMetadata The new metadata map
     * @return A new ObjectiveDTO with the updated metadata
     */
    public ObjectiveDTO withMetadata(Map<String, String> newMetadata) {
        return new ObjectiveDTO(objectiveId, type, target, requiredAmount,
                                description, order, newMetadata);
    }

    /**
     * Builder for constructing ObjectiveDTO with fluent API.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for ObjectiveDTO.
     */
    public static class Builder {
        private String objectiveId;
        private ObjectiveType type;
        private String target;
        private int requiredAmount = 1;
        private String description;
        private int order = 0;
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

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ObjectiveDTO build() {
            return new ObjectiveDTO(objectiveId, type, target, requiredAmount,
                                    description, order, metadata);
        }
    }
}
