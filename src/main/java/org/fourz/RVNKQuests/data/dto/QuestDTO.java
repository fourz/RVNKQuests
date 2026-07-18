package org.fourz.RVNKQuests.data.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Data Transfer Object for quest definitions.
 * Immutable and thread-safe for cross-boundary data transfer.
 *
 * <p>Represents the complete definition of a quest including its
 * objectives and rewards. This is the quest itself, not player
 * progress on the quest.</p>
 */
public record QuestDTO(
    String questId,
    String name,
    String description,
    String category,
    boolean repeatable,
    int cooldownMinutes,
    List<ObjectiveDTO> objectives,
    List<RewardDTO> rewards,
    List<String> prerequisites,
    Instant createdAt,
    Map<String, Object> metadata
) {
    /**
     * Compact constructor with validation and defensive copies.
     */
    public QuestDTO {
        Objects.requireNonNull(questId, "questId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");

        // Ensure non-negative cooldown
        if (cooldownMinutes < 0) {
            cooldownMinutes = 0;
        }

        // Defensive copies for mutable collections
        objectives = objectives == null ? List.of() : List.copyOf(objectives);
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
        prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * Creates a simple quest with minimal configuration.
     *
     * @param questId The unique quest identifier
     * @param name The display name
     * @param description The quest description
     * @return A new QuestDTO
     */
    public static QuestDTO create(String questId, String name, String description) {
        return new QuestDTO(
            questId,
            name,
            description,
            null,
            false,
            0,
            List.of(),
            List.of(),
            List.of(),
            Instant.now(),
            Map.of()
        );
    }

    /**
     * Creates a copy with added objective.
     *
     * @param objective The objective to add
     * @return A new QuestDTO with the objective added
     */
    public QuestDTO withObjective(ObjectiveDTO objective) {
        List<ObjectiveDTO> newObjectives = new java.util.ArrayList<>(objectives);
        newObjectives.add(objective);
        return new QuestDTO(questId, name, description, category, repeatable,
                           cooldownMinutes, newObjectives, rewards, prerequisites,
                           createdAt, metadata);
    }

    /**
     * Creates a copy with added reward.
     *
     * @param reward The reward to add
     * @return A new QuestDTO with the reward added
     */
    public QuestDTO withReward(RewardDTO reward) {
        List<RewardDTO> newRewards = new java.util.ArrayList<>(rewards);
        newRewards.add(reward);
        return new QuestDTO(questId, name, description, category, repeatable,
                           cooldownMinutes, objectives, newRewards, prerequisites,
                           createdAt, metadata);
    }

    /**
     * Creates a copy with updated objectives.
     *
     * @param newObjectives The new objectives list
     * @return A new QuestDTO with updated objectives
     */
    public QuestDTO withObjectives(List<ObjectiveDTO> newObjectives) {
        return new QuestDTO(questId, name, description, category, repeatable,
                           cooldownMinutes, newObjectives, rewards, prerequisites,
                           createdAt, metadata);
    }

    /**
     * Creates a copy with updated rewards.
     *
     * @param newRewards The new rewards list
     * @return A new QuestDTO with updated rewards
     */
    public QuestDTO withRewards(List<RewardDTO> newRewards) {
        return new QuestDTO(questId, name, description, category, repeatable,
                           cooldownMinutes, objectives, newRewards, prerequisites,
                           createdAt, metadata);
    }

    /**
     * Creates a copy marked as repeatable.
     *
     * @param cooldownMinutes Minutes before quest can be repeated
     * @return A new QuestDTO that is repeatable
     */
    public QuestDTO asRepeatable(int cooldownMinutes) {
        return new QuestDTO(questId, name, description, category, true,
                           cooldownMinutes, objectives, rewards, prerequisites,
                           createdAt, metadata);
    }

    /**
     * Creates a copy with updated category.
     *
     * @param newCategory The new category
     * @return A new QuestDTO with updated category
     */
    public QuestDTO withCategory(String newCategory) {
        return new QuestDTO(questId, name, description, newCategory, repeatable,
                           cooldownMinutes, objectives, rewards, prerequisites,
                           createdAt, metadata);
    }

    /**
     * Creates a copy with added prerequisite.
     *
     * @param prerequisiteQuestId The quest ID that must be completed first
     * @return A new QuestDTO with the prerequisite added
     */
    public QuestDTO withPrerequisite(String prerequisiteQuestId) {
        List<String> newPrerequisites = new java.util.ArrayList<>(prerequisites);
        newPrerequisites.add(prerequisiteQuestId);
        return new QuestDTO(questId, name, description, category, repeatable,
                           cooldownMinutes, objectives, rewards, newPrerequisites,
                           createdAt, metadata);
    }

    /**
     * Returns a copy of this quest with its prerequisites replaced wholesale.
     *
     * @param newPrerequisites The full prerequisite list (null is treated as empty)
     * @return A new QuestDTO with the given prerequisites
     */
    public QuestDTO withPrerequisites(List<String> newPrerequisites) {
        return new QuestDTO(questId, name, description, category, repeatable,
                           cooldownMinutes, objectives, rewards, newPrerequisites,
                           createdAt, metadata);
    }

    /**
     * Builder for constructing QuestDTO with fluent API.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for QuestDTO.
     */
    public static class Builder {
        private String questId;
        private String name;
        private String description;
        private String category;
        private boolean repeatable = false;
        private int cooldownMinutes = 0;
        private List<ObjectiveDTO> objectives = List.of();
        private List<RewardDTO> rewards = List.of();
        private List<String> prerequisites = List.of();
        private Instant createdAt = Instant.now();
        private Map<String, Object> metadata = Map.of();

        public Builder questId(String questId) {
            this.questId = questId;
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

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder repeatable(boolean repeatable) {
            this.repeatable = repeatable;
            return this;
        }

        public Builder cooldownMinutes(int cooldownMinutes) {
            this.cooldownMinutes = cooldownMinutes;
            return this;
        }

        public Builder objectives(List<ObjectiveDTO> objectives) {
            this.objectives = objectives;
            return this;
        }

        public Builder rewards(List<RewardDTO> rewards) {
            this.rewards = rewards;
            return this;
        }

        public Builder prerequisites(List<String> prerequisites) {
            this.prerequisites = prerequisites;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public QuestDTO build() {
            return new QuestDTO(questId, name, description, category, repeatable,
                               cooldownMinutes, objectives, rewards, prerequisites,
                               createdAt, metadata);
        }
    }
}
