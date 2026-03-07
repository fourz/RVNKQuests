package org.fourz.RVNKQuests.data.dto;

import java.time.Instant;
import java.util.Objects;

/**
 * Data Transfer Object for quest repeat configuration.
 * Defines repeatability settings for a quest.
 *
 * <p>Immutable and thread-safe for cross-boundary data transfer.</p>
 */
public record QuestRepeatConfigDTO(
    String questId,
    RepeatType repeatType,
    int cooldownSeconds,
    int maxCompletions
) {
    /**
     * Types of quest repeatability.
     */
    public enum RepeatType {
        /** Quest can only be completed once, never repeatable */
        ONE_TIME,
        /** Repeatable with custom cooldown period */
        COOLDOWN,
        /** Repeatable once per day (24-hour cooldown) */
        DAILY,
        /** Repeatable once per week (7-day cooldown) */
        WEEKLY,
        /** Repeatable immediately after completion */
        UNLIMITED
    }

    /**
     * Compact constructor with validation.
     */
    public QuestRepeatConfigDTO {
        Objects.requireNonNull(questId, "questId cannot be null");
        Objects.requireNonNull(repeatType, "repeatType cannot be null");

        if (cooldownSeconds < 0) {
            throw new IllegalArgumentException("cooldownSeconds cannot be negative");
        }
        if (maxCompletions < 0) {
            throw new IllegalArgumentException("maxCompletions cannot be negative");
        }
    }

    /**
     * Creates a one-time (non-repeatable) quest configuration.
     *
     * @param questId The quest identifier
     * @return A new config for a one-time quest
     */
    public static QuestRepeatConfigDTO createOneTime(String questId) {
        return new QuestRepeatConfigDTO(questId, RepeatType.ONE_TIME, 0, 1);
    }

    /**
     * Creates a daily repeatable quest configuration.
     *
     * @param questId The quest identifier
     * @return A new config for a daily quest
     */
    public static QuestRepeatConfigDTO createDaily(String questId) {
        return new QuestRepeatConfigDTO(questId, RepeatType.DAILY, 86400, 0); // 24 hours
    }

    /**
     * Creates a weekly repeatable quest configuration.
     *
     * @param questId The quest identifier
     * @return A new config for a weekly quest
     */
    public static QuestRepeatConfigDTO createWeekly(String questId) {
        return new QuestRepeatConfigDTO(questId, RepeatType.WEEKLY, 604800, 0); // 7 days
    }

    /**
     * Creates an unlimited repeatable quest configuration.
     *
     * @param questId The quest identifier
     * @return A new config for an unlimited quest
     */
    public static QuestRepeatConfigDTO createUnlimited(String questId) {
        return new QuestRepeatConfigDTO(questId, RepeatType.UNLIMITED, 0, 0);
    }

    /**
     * Creates a custom cooldown repeatable quest configuration.
     *
     * @param questId The quest identifier
     * @param cooldownSeconds Cooldown period in seconds
     * @return A new config for a cooldown-based quest
     */
    public static QuestRepeatConfigDTO createCooldown(String questId, int cooldownSeconds) {
        return new QuestRepeatConfigDTO(questId, RepeatType.COOLDOWN, cooldownSeconds, 0);
    }

    /**
     * Checks if this quest is repeatable.
     *
     * @return true if the quest can be repeated
     */
    public boolean isRepeatable() {
        return repeatType != RepeatType.ONE_TIME;
    }

    /**
     * Checks if this quest has a maximum completion limit.
     *
     * @return true if there is a completion limit
     */
    public boolean hasCompletionLimit() {
        return maxCompletions > 0;
    }

    /**
     * Checks if this quest has a cooldown period.
     *
     * @return true if there is a cooldown period
     */
    public boolean hasCooldown() {
        return cooldownSeconds > 0;
    }

    /**
     * Gets the cooldown duration as an Instant offset from completion.
     *
     * @param completionTime The completion time
     * @return The next available time
     */
    public Instant getNextAvailableTime(Instant completionTime) {
        if (!hasCooldown()) {
            return completionTime;
        }
        return completionTime.plusSeconds(cooldownSeconds);
    }

    /**
     * Builder for constructing QuestRepeatConfigDTO.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for QuestRepeatConfigDTO.
     */
    public static class Builder {
        private String questId;
        private RepeatType repeatType = RepeatType.ONE_TIME;
        private int cooldownSeconds = 0;
        private int maxCompletions = 1;

        public Builder questId(String questId) {
            this.questId = questId;
            return this;
        }

        public Builder repeatType(RepeatType repeatType) {
            this.repeatType = repeatType;
            return this;
        }

        public Builder cooldownSeconds(int cooldownSeconds) {
            this.cooldownSeconds = cooldownSeconds;
            return this;
        }

        public Builder maxCompletions(int maxCompletions) {
            this.maxCompletions = maxCompletions;
            return this;
        }

        public QuestRepeatConfigDTO build() {
            return new QuestRepeatConfigDTO(questId, repeatType, cooldownSeconds, maxCompletions);
        }
    }
}
