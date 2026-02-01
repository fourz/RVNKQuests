package org.fourz.RVNKQuests.data.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Data Transfer Object for player quest repeat tracking.
 * Tracks per-player completion count and cooldown state.
 *
 * <p>Immutable and thread-safe for cross-boundary data transfer.</p>
 */
public record PlayerQuestRepeatDTO(
    UUID playerUuid,
    String questId,
    int completionCount,
    Instant lastCompletion,
    Instant nextAvailable
) {
    /**
     * Compact constructor with validation.
     */
    public PlayerQuestRepeatDTO {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(questId, "questId cannot be null");

        if (completionCount < 0) {
            throw new IllegalArgumentException("completionCount cannot be negative");
        }
    }

    /**
     * Creates a new tracking record for a player who has never completed the quest.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return A new tracking record with zero completions
     */
    public static PlayerQuestRepeatDTO createNew(UUID playerUuid, String questId) {
        return new PlayerQuestRepeatDTO(playerUuid, questId, 0, null, null);
    }

    /**
     * Creates a copy with incremented completion count.
     *
     * @param completionTime The time of completion
     * @param nextAvailableTime The next time the quest is available
     * @return A new tracking record with updated values
     */
    public PlayerQuestRepeatDTO recordCompletion(Instant completionTime, Instant nextAvailableTime) {
        return new PlayerQuestRepeatDTO(
            playerUuid,
            questId,
            completionCount + 1,
            completionTime,
            nextAvailableTime
        );
    }

    /**
     * Checks if the quest is currently available for the player.
     *
     * @return true if the quest is available now
     */
    public boolean isAvailable() {
        if (nextAvailable == null) {
            return true; // Never completed, so available
        }
        return Instant.now().isAfter(nextAvailable);
    }

    /**
     * Checks if the quest is on cooldown.
     *
     * @return true if the quest is on cooldown
     */
    public boolean isOnCooldown() {
        return !isAvailable();
    }

    /**
     * Gets the remaining cooldown time in seconds.
     *
     * @return Remaining cooldown in seconds, or 0 if available
     */
    public long getRemainingCooldownSeconds() {
        if (isAvailable()) {
            return 0;
        }
        return nextAvailable.getEpochSecond() - Instant.now().getEpochSecond();
    }

    /**
     * Checks if the player has completed the quest at least once.
     *
     * @return true if completion count > 0
     */
    public boolean hasCompleted() {
        return completionCount > 0;
    }

    /**
     * Creates a copy with reset completion data (for quest reset).
     *
     * @return A new tracking record with zeroed completion data
     */
    public PlayerQuestRepeatDTO reset() {
        return createNew(playerUuid, questId);
    }

    /**
     * Builder for constructing PlayerQuestRepeatDTO.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for PlayerQuestRepeatDTO.
     */
    public static class Builder {
        private UUID playerUuid;
        private String questId;
        private int completionCount = 0;
        private Instant lastCompletion;
        private Instant nextAvailable;

        public Builder playerUuid(UUID playerUuid) {
            this.playerUuid = playerUuid;
            return this;
        }

        public Builder questId(String questId) {
            this.questId = questId;
            return this;
        }

        public Builder completionCount(int completionCount) {
            this.completionCount = completionCount;
            return this;
        }

        public Builder lastCompletion(Instant lastCompletion) {
            this.lastCompletion = lastCompletion;
            return this;
        }

        public Builder nextAvailable(Instant nextAvailable) {
            this.nextAvailable = nextAvailable;
            return this;
        }

        public PlayerQuestRepeatDTO build() {
            return new PlayerQuestRepeatDTO(
                playerUuid, questId, completionCount,
                lastCompletion, nextAvailable
            );
        }
    }
}
