package org.fourz.RVNKQuests.data.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Data Transfer Object for claimed quest rewards.
 * Immutable and thread-safe for cross-boundary data transfer.
 *
 * <p>Tracks which rewards have been claimed by players to prevent
 * double-claiming.</p>
 */
public record QuestRewardClaimedDTO(
    UUID playerUuid,
    String questId,
    String rewardId,
    Instant claimedAt
) {
    /**
     * Compact constructor with validation.
     */
    public QuestRewardClaimedDTO {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(questId, "questId cannot be null");
        Objects.requireNonNull(rewardId, "rewardId cannot be null");

        // Default claimedAt if null
        if (claimedAt == null) {
            claimedAt = Instant.now();
        }
    }

    /**
     * Creates a new reward claimed record with current timestamp.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param rewardId The reward identifier
     * @return A new QuestRewardClaimedDTO
     */
    public static QuestRewardClaimedDTO create(UUID playerUuid, String questId, String rewardId) {
        return new QuestRewardClaimedDTO(playerUuid, questId, rewardId, Instant.now());
    }

    /**
     * Builder for constructing QuestRewardClaimedDTO.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for QuestRewardClaimedDTO.
     */
    public static class Builder {
        private UUID playerUuid;
        private String questId;
        private String rewardId;
        private Instant claimedAt = Instant.now();

        public Builder playerUuid(UUID playerUuid) {
            this.playerUuid = playerUuid;
            return this;
        }

        public Builder questId(String questId) {
            this.questId = questId;
            return this;
        }

        public Builder rewardId(String rewardId) {
            this.rewardId = rewardId;
            return this;
        }

        public Builder claimedAt(Instant claimedAt) {
            this.claimedAt = claimedAt;
            return this;
        }

        public QuestRewardClaimedDTO build() {
            return new QuestRewardClaimedDTO(playerUuid, questId, rewardId, claimedAt);
        }
    }
}
