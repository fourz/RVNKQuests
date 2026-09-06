package org.fourz.RVNKQuests.data.dto;

import org.fourz.RVNKQuests.quest.QuestState;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Data Transfer Object for player quest progress.
 * Immutable and thread-safe for cross-boundary data transfer.
 *
 * <p>Represents a player's progress on a specific quest, including state,
 * path choices, and timestamps.</p>
 */
public record QuestProgressDTO(
    UUID playerUuid,
    String questId,
    QuestState state,
    String pathChoice,
    Instant startedAt,
    Instant completedAt,
    Map<String, Object> metadata
) {
    /**
     * Compact constructor with validation and defensive copies.
     */
    public QuestProgressDTO {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(questId, "questId cannot be null");

        // Default state if null
        if (state == null) {
            state = QuestState.NOT_STARTED;
        }

        // Defensive copy for mutable collection
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * Creates a new progress record with NOT_STARTED state.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return A new QuestProgressDTO in NOT_STARTED state
     */
    public static QuestProgressDTO createNew(UUID playerUuid, String questId) {
        return new QuestProgressDTO(
            playerUuid,
            questId,
            QuestState.NOT_STARTED,
            null,
            null,
            null,
            Map.of()
        );
    }

    /**
     * Creates a copy with updated state.
     *
     * @param newState The new quest state
     * @return A new QuestProgressDTO with the updated state
     */
    public QuestProgressDTO withState(QuestState newState) {
        Instant newStartedAt = startedAt;
        Instant newCompletedAt = completedAt;

        // NOT_STARTED means "no run in progress", so it must carry no run timestamps (#2065).
        // This method only ever SET stamps and never cleared them, so a transition back to
        // NOT_STARTED produced a row that contradicts itself - state saying the quest was
        // never begun while startedAt and completedAt both say otherwise. Any consumer
        // trusting completedAt then disagrees with one trusting state.
        //
        // No caller reaches this branch today (nothing advances TO NOT_STARTED, and
        // resetQuestProgress deletes the row outright), so this is defensive rather than a
        // fix for an observed live path - see the issue for what is and is not proven about
        // the qa_reward_item row that prompted it.
        if (newState == QuestState.NOT_STARTED) {
            return new QuestProgressDTO(playerUuid, questId, newState, pathChoice, null, null, metadata);
        }

        // Set startedAt when transitioning from NOT_STARTED
        if (state == QuestState.NOT_STARTED && newState != QuestState.NOT_STARTED && newStartedAt == null) {
            newStartedAt = Instant.now();
        }

        // Set completedAt when transitioning to COMPLETED
        if (newState == QuestState.COMPLETED && newCompletedAt == null) {
            newCompletedAt = Instant.now();
        }

        return new QuestProgressDTO(playerUuid, questId, newState, pathChoice, newStartedAt, newCompletedAt, metadata);
    }

    /**
     * Creates a copy with updated path choice.
     *
     * @param newPathChoice The path chosen by the player
     * @return A new QuestProgressDTO with the updated path choice
     */
    public QuestProgressDTO withPathChoice(String newPathChoice) {
        return new QuestProgressDTO(playerUuid, questId, state, newPathChoice, startedAt, completedAt, metadata);
    }

    /**
     * Creates a copy with updated metadata.
     *
     * @param newMetadata The new metadata map
     * @return A new QuestProgressDTO with the updated metadata
     */
    public QuestProgressDTO withMetadata(Map<String, Object> newMetadata) {
        return new QuestProgressDTO(playerUuid, questId, state, pathChoice, startedAt, completedAt, newMetadata);
    }

    /**
     * Checks if the quest has been started.
     *
     * @return true if the quest state is not NOT_STARTED
     */
    public boolean isStarted() {
        return state != QuestState.NOT_STARTED;
    }

    /**
     * Checks if the quest is completed.
     *
     * @return true if the quest state is COMPLETED
     */
    public boolean isCompleted() {
        return state == QuestState.COMPLETED;
    }

    /**
     * Checks if the quest is actively in progress.
     *
     * @return true if started but not completed
     */
    public boolean isActive() {
        return isStarted() && !isCompleted();
    }

    /**
     * Builder for constructing QuestProgressDTO with optional fields.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for QuestProgressDTO.
     */
    public static class Builder {
        private UUID playerUuid;
        private String questId;
        private QuestState state = QuestState.NOT_STARTED;
        private String pathChoice;
        private Instant startedAt;
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

        public Builder state(QuestState state) {
            this.state = state;
            return this;
        }

        public Builder pathChoice(String pathChoice) {
            this.pathChoice = pathChoice;
            return this;
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
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

        public QuestProgressDTO build() {
            return new QuestProgressDTO(
                playerUuid, questId, state, pathChoice,
                startedAt, completedAt, metadata
            );
        }
    }
}
