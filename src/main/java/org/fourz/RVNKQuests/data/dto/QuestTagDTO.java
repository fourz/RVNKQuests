package org.fourz.RVNKQuests.data.dto;

import java.util.Objects;

/**
 * Data Transfer Object for quest tags.
 * Immutable and thread-safe for cross-boundary data transfer.
 *
 * <p>Represents a tag for flexible quest filtering and categorization.</p>
 */
public record QuestTagDTO(
    int id,
    String name,
    String displayName,
    String colorCode
) {
    /**
     * Compact constructor with validation.
     */
    public QuestTagDTO {
        Objects.requireNonNull(name, "name cannot be null");
    }

    /**
     * Creates a new tag with basic properties.
     *
     * @param name The unique tag name
     * @param displayName The display name for UI
     * @return A new QuestTagDTO
     */
    public static QuestTagDTO create(String name, String displayName) {
        return new QuestTagDTO(0, name, displayName, null);
    }

    /**
     * Creates a new tag with full properties.
     *
     * @param name The unique tag name
     * @param displayName The display name for UI
     * @param colorCode The color code (e.g., #FF5733 or §c)
     * @return A new QuestTagDTO
     */
    public static QuestTagDTO create(String name, String displayName, String colorCode) {
        return new QuestTagDTO(0, name, displayName, colorCode);
    }

    /**
     * Creates a copy with updated display name.
     *
     * @param newDisplayName The new display name
     * @return A new QuestTagDTO with updated display name
     */
    public QuestTagDTO withDisplayName(String newDisplayName) {
        return new QuestTagDTO(id, name, newDisplayName, colorCode);
    }

    /**
     * Creates a copy with updated color code.
     *
     * @param newColorCode The new color code
     * @return A new QuestTagDTO with updated color code
     */
    public QuestTagDTO withColorCode(String newColorCode) {
        return new QuestTagDTO(id, name, displayName, newColorCode);
    }

    /**
     * Checks if this tag has a color code.
     *
     * @return true if color code is present and not empty
     */
    public boolean hasColorCode() {
        return colorCode != null && !colorCode.isEmpty();
    }

    /**
     * Gets the display name or falls back to name if display name is null.
     *
     * @return The display name or name
     */
    public String getEffectiveDisplayName() {
        return displayName != null && !displayName.isEmpty() ? displayName : name;
    }

    /**
     * Builder for constructing QuestTagDTO.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for QuestTagDTO.
     */
    public static class Builder {
        private int id = 0;
        private String name;
        private String displayName;
        private String colorCode;

        public Builder id(int id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder colorCode(String colorCode) {
            this.colorCode = colorCode;
            return this;
        }

        public QuestTagDTO build() {
            return new QuestTagDTO(id, name, displayName, colorCode);
        }
    }
}
