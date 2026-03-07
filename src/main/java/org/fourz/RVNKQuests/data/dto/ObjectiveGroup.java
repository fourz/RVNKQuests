package org.fourz.RVNKQuests.data.dto;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a group of objectives with specific completion requirements.
 * Enables parallel execution, nested hierarchies, and flexible completion logic.
 *
 * <h3>Group Types</h3>
 * <ul>
 *   <li><b>ALL</b> - All objectives in group must be completed (sequential AND)</li>
 *   <li><b>ANY</b> - Any one objective must be completed (OR)</li>
 *   <li><b>COUNT</b> - A minimum number of objectives must be completed</li>
 *   <li><b>ORDERED</b> - All objectives must be completed in order</li>
 * </ul>
 *
 * <h3>Usage Examples</h3>
 * <pre>
 * // Parallel objectives (complete any 2 of 3)
 * ObjectiveGroup.builder()
 *     .groupId("gather_resources")
 *     .completionType(CompletionType.COUNT)
 *     .requiredCount(2)
 *     .objective(collectWoodObjective)
 *     .objective(collectStoneObjective)
 *     .objective(collectIronObjective)
 *     .build();
 *
 * // Nested hierarchy
 * ObjectiveGroup.builder()
 *     .groupId("main_quest_line")
 *     .completionType(CompletionType.ORDERED)
 *     .subGroup(introGroup)
 *     .subGroup(midGameGroup)
 *     .subGroup(endGameGroup)
 *     .build();
 * </pre>
 *
 * @param groupId Unique identifier for this group
 * @param name Display name for the group
 * @param description Human-readable description
 * @param completionType How completion is determined
 * @param requiredCount For COUNT type, minimum completions needed
 * @param objectives Direct objectives in this group
 * @param subGroups Nested objective groups
 * @param conditions Conditions that must be met for group to be active
 * @param metadata Additional group metadata
 */
public record ObjectiveGroup(
    String groupId,
    String name,
    String description,
    CompletionType completionType,
    int requiredCount,
    List<ObjectiveDTO> objectives,
    List<ObjectiveGroup> subGroups,
    List<ObjectiveCondition> conditions,
    Map<String, Object> metadata
) {
    /**
     * Completion type determining how group objectives are evaluated.
     */
    public enum CompletionType {
        /**
         * All objectives/subgroups must be completed.
         * Order does not matter unless combined with conditions.
         */
        ALL,

        /**
         * Any single objective/subgroup completion satisfies the group.
         */
        ANY,

        /**
         * At least requiredCount objectives/subgroups must complete.
         */
        COUNT,

        /**
         * All objectives/subgroups must complete in order defined.
         * Next objective only becomes active when previous completes.
         */
        ORDERED
    }

    /**
     * Compact constructor with validation.
     */
    public ObjectiveGroup {
        Objects.requireNonNull(groupId, "groupId cannot be null");
        Objects.requireNonNull(completionType, "completionType cannot be null");

        // Validate requiredCount for COUNT type
        if (completionType == CompletionType.COUNT && requiredCount < 1) {
            requiredCount = 1;
        }

        // Defensive copies
        objectives = objectives == null ? List.of() : List.copyOf(objectives);
        subGroups = subGroups == null ? List.of() : List.copyOf(subGroups);
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);

        // Validate group has content
        if (objectives.isEmpty() && subGroups.isEmpty()) {
            throw new IllegalArgumentException("ObjectiveGroup must have at least one objective or subgroup");
        }

        // For COUNT type, ensure requiredCount doesn't exceed total
        int totalItems = objectives.size() + subGroups.size();
        if (completionType == CompletionType.COUNT && requiredCount > totalItems) {
            requiredCount = totalItems;
        }
    }

    /**
     * Creates a simple ALL group with objectives.
     *
     * @param groupId The group identifier
     * @param objectives The objectives in the group
     * @return A new ALL-type group
     */
    public static ObjectiveGroup all(String groupId, ObjectiveDTO... objectives) {
        return new ObjectiveGroup(
            groupId,
            null,
            null,
            CompletionType.ALL,
            0,
            List.of(objectives),
            List.of(),
            List.of(),
            Map.of()
        );
    }

    /**
     * Creates a simple ANY group with objectives.
     *
     * @param groupId The group identifier
     * @param objectives The objectives in the group
     * @return A new ANY-type group
     */
    public static ObjectiveGroup any(String groupId, ObjectiveDTO... objectives) {
        return new ObjectiveGroup(
            groupId,
            null,
            null,
            CompletionType.ANY,
            0,
            List.of(objectives),
            List.of(),
            List.of(),
            Map.of()
        );
    }

    /**
     * Creates a COUNT group requiring minimum completions.
     *
     * @param groupId The group identifier
     * @param requiredCount Minimum objectives to complete
     * @param objectives The objectives in the group
     * @return A new COUNT-type group
     */
    public static ObjectiveGroup count(String groupId, int requiredCount, ObjectiveDTO... objectives) {
        return new ObjectiveGroup(
            groupId,
            null,
            null,
            CompletionType.COUNT,
            requiredCount,
            List.of(objectives),
            List.of(),
            List.of(),
            Map.of()
        );
    }

    /**
     * Creates an ORDERED group with sequential objectives.
     *
     * @param groupId The group identifier
     * @param objectives The objectives in order
     * @return A new ORDERED-type group
     */
    public static ObjectiveGroup ordered(String groupId, ObjectiveDTO... objectives) {
        return new ObjectiveGroup(
            groupId,
            null,
            null,
            CompletionType.ORDERED,
            0,
            List.of(objectives),
            List.of(),
            List.of(),
            Map.of()
        );
    }

    /**
     * Creates an ORDERED group with nested subgroups.
     *
     * @param groupId The group identifier
     * @param subGroups The subgroups in order
     * @return A new ORDERED-type group with subgroups
     */
    public static ObjectiveGroup orderedGroups(String groupId, ObjectiveGroup... subGroups) {
        return new ObjectiveGroup(
            groupId,
            null,
            null,
            CompletionType.ORDERED,
            0,
            List.of(),
            List.of(subGroups),
            List.of(),
            Map.of()
        );
    }

    /**
     * Gets the total number of completable items (objectives + subgroups).
     *
     * @return Total item count
     */
    public int getTotalItemCount() {
        return objectives.size() + subGroups.size();
    }

    /**
     * Gets the number of completions required for this group.
     * For ALL/ORDERED returns total, for ANY returns 1, for COUNT returns requiredCount.
     *
     * @return Required completions
     */
    public int getRequiredCompletions() {
        return switch (completionType) {
            case ALL, ORDERED -> getTotalItemCount();
            case ANY -> 1;
            case COUNT -> requiredCount;
        };
    }

    /**
     * Checks if this group has any conditions.
     *
     * @return true if conditions exist
     */
    public boolean hasConditions() {
        return !conditions.isEmpty();
    }

    /**
     * Checks if this group has nested subgroups.
     *
     * @return true if subgroups exist
     */
    public boolean hasSubGroups() {
        return !subGroups.isEmpty();
    }

    /**
     * Creates a copy with added objective.
     *
     * @param objective The objective to add
     * @return A new group with the objective added
     */
    public ObjectiveGroup withObjective(ObjectiveDTO objective) {
        List<ObjectiveDTO> newObjectives = new java.util.ArrayList<>(objectives);
        newObjectives.add(objective);
        return new ObjectiveGroup(groupId, name, description, completionType,
                                  requiredCount, newObjectives, subGroups, conditions, metadata);
    }

    /**
     * Creates a copy with added subgroup.
     *
     * @param subGroup The subgroup to add
     * @return A new group with the subgroup added
     */
    public ObjectiveGroup withSubGroup(ObjectiveGroup subGroup) {
        List<ObjectiveGroup> newSubGroups = new java.util.ArrayList<>(subGroups);
        newSubGroups.add(subGroup);
        return new ObjectiveGroup(groupId, name, description, completionType,
                                  requiredCount, objectives, newSubGroups, conditions, metadata);
    }

    /**
     * Creates a copy with added condition.
     *
     * @param condition The condition to add
     * @return A new group with the condition added
     */
    public ObjectiveGroup withCondition(ObjectiveCondition condition) {
        List<ObjectiveCondition> newConditions = new java.util.ArrayList<>(conditions);
        newConditions.add(condition);
        return new ObjectiveGroup(groupId, name, description, completionType,
                                  requiredCount, objectives, subGroups, newConditions, metadata);
    }

    /**
     * Creates a copy with updated name.
     *
     * @param newName The new name
     * @return A new group with updated name
     */
    public ObjectiveGroup withName(String newName) {
        return new ObjectiveGroup(groupId, newName, description, completionType,
                                  requiredCount, objectives, subGroups, conditions, metadata);
    }

    /**
     * Creates a copy with updated description.
     *
     * @param newDescription The new description
     * @return A new group with updated description
     */
    public ObjectiveGroup withDescription(String newDescription) {
        return new ObjectiveGroup(groupId, name, newDescription, completionType,
                                  requiredCount, objectives, subGroups, conditions, metadata);
    }

    /**
     * Builder for ObjectiveGroup.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for ObjectiveGroup.
     */
    public static class Builder {
        private String groupId;
        private String name;
        private String description;
        private CompletionType completionType = CompletionType.ALL;
        private int requiredCount = 0;
        private final java.util.List<ObjectiveDTO> objectives = new java.util.ArrayList<>();
        private final java.util.List<ObjectiveGroup> subGroups = new java.util.ArrayList<>();
        private final java.util.List<ObjectiveCondition> conditions = new java.util.ArrayList<>();
        private Map<String, Object> metadata = Map.of();

        public Builder groupId(String groupId) {
            this.groupId = groupId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder completionType(CompletionType completionType) {
            this.completionType = completionType;
            return this;
        }

        public Builder requiredCount(int requiredCount) {
            this.requiredCount = requiredCount;
            return this;
        }

        public Builder objective(ObjectiveDTO objective) {
            this.objectives.add(objective);
            return this;
        }

        public Builder objectives(List<ObjectiveDTO> objectives) {
            this.objectives.addAll(objectives);
            return this;
        }

        public Builder subGroup(ObjectiveGroup subGroup) {
            this.subGroups.add(subGroup);
            return this;
        }

        public Builder subGroups(List<ObjectiveGroup> subGroups) {
            this.subGroups.addAll(subGroups);
            return this;
        }

        public Builder condition(ObjectiveCondition condition) {
            this.conditions.add(condition);
            return this;
        }

        public Builder conditions(List<ObjectiveCondition> conditions) {
            this.conditions.addAll(conditions);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ObjectiveGroup build() {
            return new ObjectiveGroup(groupId, name, description, completionType,
                                      requiredCount, objectives, subGroups, conditions, metadata);
        }
    }
}
