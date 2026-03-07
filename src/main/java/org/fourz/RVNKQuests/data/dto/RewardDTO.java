package org.fourz.RVNKQuests.data.dto;

import java.util.Map;
import java.util.Objects;

/**
 * Data Transfer Object for quest reward definitions.
 * Immutable and thread-safe for cross-boundary data transfer.
 *
 * <p>Represents the definition of a reward that can be given
 * upon quest completion or at specific milestones.</p>
 */
public record RewardDTO(
    String rewardId,
    RewardType type,
    String value,
    int amount,
    String description,
    Map<String, String> metadata
) {
    /**
     * Compact constructor with validation and defensive copies.
     */
    public RewardDTO {
        Objects.requireNonNull(rewardId, "rewardId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        // Ensure positive amount
        if (amount < 1) {
            amount = 1;
        }

        // Defensive copy for mutable collection
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * Creates a simple reward with minimal configuration.
     *
     * @param rewardId The unique reward identifier
     * @param type The reward type
     * @param value The reward value (item ID, command, etc.)
     * @param amount The amount to give
     * @return A new RewardDTO
     */
    public static RewardDTO create(String rewardId, RewardType type,
                                    String value, int amount) {
        return new RewardDTO(
            rewardId,
            type,
            value,
            amount,
            null,
            Map.of()
        );
    }

    /**
     * Creates an experience reward.
     *
     * @param rewardId The unique reward identifier
     * @param amount The amount of XP to give
     * @return A new RewardDTO for experience
     */
    public static RewardDTO experience(String rewardId, int amount) {
        return new RewardDTO(rewardId, RewardType.EXPERIENCE, String.valueOf(amount),
                            amount, "Experience Points", Map.of());
    }

    /**
     * Creates a currency/token reward.
     *
     * @param rewardId The unique reward identifier
     * @param amount The amount of currency to give
     * @return A new RewardDTO for currency
     */
    public static RewardDTO currency(String rewardId, int amount) {
        return new RewardDTO(rewardId, RewardType.CURRENCY, String.valueOf(amount),
                            amount, "Tokens", Map.of());
    }

    /**
     * Creates an item reward.
     *
     * @param rewardId The unique reward identifier
     * @param itemId The item type/ID
     * @param amount The amount of items to give
     * @return A new RewardDTO for items
     */
    public static RewardDTO item(String rewardId, String itemId, int amount) {
        return new RewardDTO(rewardId, RewardType.ITEM, itemId,
                            amount, null, Map.of());
    }

    /**
     * Creates a command reward.
     *
     * @param rewardId The unique reward identifier
     * @param command The command to execute (with placeholders)
     * @return A new RewardDTO for command execution
     */
    public static RewardDTO command(String rewardId, String command) {
        return new RewardDTO(rewardId, RewardType.COMMAND, command,
                            1, null, Map.of());
    }

    /**
     * Creates a copy with updated description.
     *
     * @param newDescription The new description
     * @return A new RewardDTO with the updated description
     */
    public RewardDTO withDescription(String newDescription) {
        return new RewardDTO(rewardId, type, value, amount, newDescription, metadata);
    }

    /**
     * Creates a copy with updated metadata.
     *
     * @param newMetadata The new metadata map
     * @return A new RewardDTO with the updated metadata
     */
    public RewardDTO withMetadata(Map<String, String> newMetadata) {
        return new RewardDTO(rewardId, type, value, amount, description, newMetadata);
    }

    /**
     * Builder for constructing RewardDTO with fluent API.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for RewardDTO.
     */
    public static class Builder {
        private String rewardId;
        private RewardType type;
        private String value;
        private int amount = 1;
        private String description;
        private Map<String, String> metadata = Map.of();

        public Builder rewardId(String rewardId) {
            this.rewardId = rewardId;
            return this;
        }

        public Builder type(RewardType type) {
            this.type = type;
            return this;
        }

        public Builder value(String value) {
            this.value = value;
            return this;
        }

        public Builder amount(int amount) {
            this.amount = amount;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public RewardDTO build() {
            return new RewardDTO(rewardId, type, value, amount, description, metadata);
        }
    }
}
