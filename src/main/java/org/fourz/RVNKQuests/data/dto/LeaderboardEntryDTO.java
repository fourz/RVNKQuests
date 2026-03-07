package org.fourz.RVNKQuests.data.dto;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Data Transfer Object for quest leaderboard entries.
 * Immutable and thread-safe for cross-boundary data transfer.
 *
 * <p>Represents a player's standing on a specific leaderboard with score and metadata.</p>
 */
public record LeaderboardEntryDTO(
    UUID playerUuid,
    LeaderboardType leaderboardType,
    long score,
    Instant lastUpdated,
    Map<String, Object> metadata
) {
    /**
     * Leaderboard types for different competitive metrics.
     */
    public enum LeaderboardType {
        TOTAL_QUESTS_COMPLETED("total_quests_completed"),
        DAILY_QUESTS("daily_quests"),
        WEEKLY_QUESTS("weekly_quests"),
        MONTHLY_QUESTS("monthly_quests"),
        QUEST_POINTS("quest_points"),
        CATEGORY_SPECIFIC("category_specific"),
        SPEED_RUN("speed_run");

        private final String identifier;

        LeaderboardType(String identifier) {
            this.identifier = identifier;
        }

        public String getIdentifier() {
            return identifier;
        }

        public static LeaderboardType fromIdentifier(String identifier) {
            for (LeaderboardType type : values()) {
                if (type.identifier.equalsIgnoreCase(identifier)) {
                    return type;
                }
            }
            return TOTAL_QUESTS_COMPLETED; // Default fallback
        }
    }

    /**
     * Compact constructor with validation and defensive copies.
     */
    public LeaderboardEntryDTO {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(leaderboardType, "leaderboardType cannot be null");
        Objects.requireNonNull(lastUpdated, "lastUpdated cannot be null");

        // Ensure non-negative score
        if (score < 0) {
            score = 0;
        }

        // Defensive copy for mutable collection
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * Creates a new leaderboard entry with zero score.
     *
     * @param playerUuid The player's UUID
     * @param leaderboardType The leaderboard type
     * @return A new LeaderboardEntryDTO with zero score
     */
    public static LeaderboardEntryDTO create(UUID playerUuid, LeaderboardType leaderboardType) {
        return new LeaderboardEntryDTO(playerUuid, leaderboardType, 0, Instant.now(), Map.of());
    }

    /**
     * Creates a new leaderboard entry with specified score.
     *
     * @param playerUuid The player's UUID
     * @param leaderboardType The leaderboard type
     * @param score The initial score
     * @return A new LeaderboardEntryDTO
     */
    public static LeaderboardEntryDTO create(UUID playerUuid, LeaderboardType leaderboardType, long score) {
        return new LeaderboardEntryDTO(playerUuid, leaderboardType, score, Instant.now(), Map.of());
    }

    /**
     * Creates a copy with updated score.
     *
     * @param newScore The new score
     * @return A new LeaderboardEntryDTO with updated score and timestamp
     */
    public LeaderboardEntryDTO withScore(long newScore) {
        return new LeaderboardEntryDTO(playerUuid, leaderboardType, newScore, Instant.now(), metadata);
    }

    /**
     * Creates a copy with incremented score.
     *
     * @param amount The amount to add to the score
     * @return A new LeaderboardEntryDTO with updated score and timestamp
     */
    public LeaderboardEntryDTO incrementScore(long amount) {
        return new LeaderboardEntryDTO(playerUuid, leaderboardType, score + amount, Instant.now(), metadata);
    }

    /**
     * Creates a copy with updated metadata.
     *
     * @param newMetadata The new metadata map
     * @return A new LeaderboardEntryDTO with updated metadata
     */
    public LeaderboardEntryDTO withMetadata(Map<String, Object> newMetadata) {
        return new LeaderboardEntryDTO(playerUuid, leaderboardType, score, lastUpdated, newMetadata);
    }

    /**
     * Checks if this entry has metadata.
     *
     * @return true if metadata is present and not empty
     */
    public boolean hasMetadata() {
        return !metadata.isEmpty();
    }

    /**
     * Gets the time since last update.
     *
     * @return Duration since last update
     */
    public java.time.Duration getTimeSinceUpdate() {
        return java.time.Duration.between(lastUpdated, Instant.now());
    }

    /**
     * Builder for constructing LeaderboardEntryDTO.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for LeaderboardEntryDTO.
     */
    public static class Builder {
        private UUID playerUuid;
        private LeaderboardType leaderboardType;
        private long score = 0;
        private Instant lastUpdated = Instant.now();
        private Map<String, Object> metadata = Map.of();

        public Builder playerUuid(UUID playerUuid) {
            this.playerUuid = playerUuid;
            return this;
        }

        public Builder leaderboardType(LeaderboardType leaderboardType) {
            this.leaderboardType = leaderboardType;
            return this;
        }

        public Builder score(long score) {
            this.score = score;
            return this;
        }

        public Builder lastUpdated(Instant lastUpdated) {
            this.lastUpdated = lastUpdated;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public LeaderboardEntryDTO build() {
            return new LeaderboardEntryDTO(playerUuid, leaderboardType, score, lastUpdated, metadata);
        }
    }
}
