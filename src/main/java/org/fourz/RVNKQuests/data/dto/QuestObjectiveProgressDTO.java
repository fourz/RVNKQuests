package org.fourz.RVNKQuests.data.dto;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Data Transfer Object for quest objective progress.
 * Immutable and thread-safe for cross-boundary data transfer.
 *
 * <p>Tracks individual objective completion within a quest.</p>
 */
public record QuestObjectiveProgressDTO(
    UUID playerUuid,
    String questId,
    String objectiveId,
    int progressCount,
    int targetCount,
    boolean completed,
    Instant completedAt,
    Map<String, Object> metadata
) {
    /**
     * Compact constructor with validation and defensive copies.
     */
    public QuestObjectiveProgressDTO {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(questId, "questId cannot be null");
        Objects.requireNonNull(objectiveId, "objectiveId cannot be null");

        // Ensure non-negative counts
        if (progressCount < 0) {
            progressCount = 0;
        }
        if (targetCount < 1) {
            targetCount = 1;
        }

        // Defensive copy for mutable collection
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * Creates a new objective progress with zero progress.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param objectiveId The objective identifier
     * @param targetCount The target count to complete
     * @return A new QuestObjectiveProgressDTO with zero progress
     */
    public static QuestObjectiveProgressDTO createNew(UUID playerUuid, String questId,
                                                       String objectiveId, int targetCount) {
        return new QuestObjectiveProgressDTO(
            playerUuid,
            questId,
            objectiveId,
            0,
            targetCount,
            false,
            null,
            Map.of()
        );
    }

    /**
     * Creates a copy with incremented progress.
     *
     * @param amount The amount to increment by
     * @return A new QuestObjectiveProgressDTO with updated progress
     */
    public QuestObjectiveProgressDTO incrementProgress(int amount) {
        int newProgress = Math.min(progressCount + amount, targetCount);
        boolean nowCompleted = newProgress >= targetCount;
        Instant newCompletedAt = (nowCompleted && !completed) ? Instant.now() : completedAt;

        return new QuestObjectiveProgressDTO(
            playerUuid, questId, objectiveId,
            newProgress, targetCount, nowCompleted, newCompletedAt, metadata
        );
    }

    /**
     * Creates a copy with updated progress count.
     *
     * @param newProgressCount The new progress count
     * @return A new QuestObjectiveProgressDTO with updated progress
     */
    public QuestObjectiveProgressDTO withProgressCount(int newProgressCount) {
        int clamped = Math.max(0, Math.min(newProgressCount, targetCount));
        boolean nowCompleted = clamped >= targetCount;
        Instant newCompletedAt = (nowCompleted && !completed) ? Instant.now() : completedAt;

        return new QuestObjectiveProgressDTO(
            playerUuid, questId, objectiveId,
            clamped, targetCount, nowCompleted, newCompletedAt, metadata
        );
    }

    /**
     * Creates a copy marked as completed.
     *
     * @return A new QuestObjectiveProgressDTO marked complete
     */
    public QuestObjectiveProgressDTO markCompleted() {
        return new QuestObjectiveProgressDTO(
            playerUuid, questId, objectiveId,
            targetCount, targetCount, true,
            completedAt != null ? completedAt : Instant.now(),
            metadata
        );
    }

    /**
     * Gets the completion percentage (0.0 to 1.0).
     *
     * @return The completion percentage
     */
    public double getCompletionPercentage() {
        if (targetCount == 0) return 1.0;
        return (double) progressCount / targetCount;
    }

    /**
     * Gets remaining count to complete.
     *
     * @return The remaining count
     */
    public int getRemainingCount() {
        return Math.max(0, targetCount - progressCount);
    }

    /**
     * Builder for constructing QuestObjectiveProgressDTO.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for QuestObjectiveProgressDTO.
     */
    public static class Builder {
        private UUID playerUuid;
        private String questId;
        private String objectiveId;
        private int progressCount = 0;
        private int targetCount = 1;
        private boolean completed = false;
        private Instant completedAt;
        private Map<String, Object> metadata = Map.of();

        public Builder playerUuid(UUID playerUuid) {
            this.playerUuid = playerUuid;
            return this;
        }

        public Builder questId(String questId) {
            this.questId = questId;
            return this;
        }

        public Builder objectiveId(String objectiveId) {
            this.objectiveId = objectiveId;
            return this;
        }

        public Builder progressCount(int progressCount) {
            this.progressCount = progressCount;
            return this;
        }

        public Builder targetCount(int targetCount) {
            this.targetCount = targetCount;
            return this;
        }

        public Builder completed(boolean completed) {
            this.completed = completed;
            return this;
        }

        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public QuestObjectiveProgressDTO build() {
            return new QuestObjectiveProgressDTO(
                playerUuid, questId, objectiveId,
                progressCount, targetCount, completed, completedAt, metadata
            );
        }
    }
}
