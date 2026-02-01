package org.fourz.RVNKQuests.data.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Data Transfer Object for quest journal entries.
 * Immutable and thread-safe for cross-boundary data transfer.
 *
 * <p>Represents a historical record of a player's quest action with timestamp and details.</p>
 */
public record JournalEntryDTO(
    long id,
    UUID playerUuid,
    String questId,
    JournalAction action,
    Instant timestamp,
    String details
) {
    /**
     * Quest journal action types.
     */
    public enum JournalAction {
        STARTED,
        COMPLETED,
        ABANDONED,
        OBJECTIVE_COMPLETE,
        FAILED,
        PATH_CHOSEN,
        REWARD_CLAIMED
    }

    /**
     * Compact constructor with validation.
     */
    public JournalEntryDTO {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(questId, "questId cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
    }

    /**
     * Creates a new journal entry with current timestamp.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param action The journal action
     * @return A new JournalEntryDTO
     */
    public static JournalEntryDTO create(UUID playerUuid, String questId, JournalAction action) {
        return new JournalEntryDTO(0, playerUuid, questId, action, Instant.now(), null);
    }

    /**
     * Creates a new journal entry with current timestamp and details.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param action The journal action
     * @param details Additional details about the action
     * @return A new JournalEntryDTO
     */
    public static JournalEntryDTO create(UUID playerUuid, String questId, JournalAction action, String details) {
        return new JournalEntryDTO(0, playerUuid, questId, action, Instant.now(), details);
    }

    /**
     * Creates a copy with updated details.
     *
     * @param newDetails The new details
     * @return A new JournalEntryDTO with updated details
     */
    public JournalEntryDTO withDetails(String newDetails) {
        return new JournalEntryDTO(id, playerUuid, questId, action, timestamp, newDetails);
    }

    /**
     * Checks if this entry has details.
     *
     * @return true if details are present and not empty
     */
    public boolean hasDetails() {
        return details != null && !details.isEmpty();
    }

    /**
     * Builder for constructing JournalEntryDTO.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for JournalEntryDTO.
     */
    public static class Builder {
        private long id = 0;
        private UUID playerUuid;
        private String questId;
        private JournalAction action;
        private Instant timestamp = Instant.now();
        private String details;

        public Builder id(long id) {
            this.id = id;
            return this;
        }

        public Builder playerUuid(UUID playerUuid) {
            this.playerUuid = playerUuid;
            return this;
        }

        public Builder questId(String questId) {
            this.questId = questId;
            return this;
        }

        public Builder action(JournalAction action) {
            this.action = action;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder details(String details) {
            this.details = details;
            return this;
        }

        public JournalEntryDTO build() {
            return new JournalEntryDTO(id, playerUuid, questId, action, timestamp, details);
        }
    }
}
