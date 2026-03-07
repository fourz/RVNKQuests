package org.fourz.RVNKQuests.data.dto;

import java.util.Objects;

/**
 * Data Transfer Object for quest categories.
 * Immutable and thread-safe for cross-boundary data transfer.
 *
 * <p>Represents a category for organizing quests with display properties.</p>
 */
public record QuestCategoryDTO(
    int id,
    String name,
    String displayName,
    String colorCode,
    String icon,
    String description,
    int sortOrder
) {
    /**
     * Compact constructor with validation.
     */
    public QuestCategoryDTO {
        Objects.requireNonNull(name, "name cannot be null");

        // Ensure non-negative sort order
        if (sortOrder < 0) {
            sortOrder = 0;
        }
    }

    /**
     * Creates a new category with basic properties.
     *
     * @param name The unique category name
     * @param displayName The display name for UI
     * @return A new QuestCategoryDTO
     */
    public static QuestCategoryDTO create(String name, String displayName) {
        return new QuestCategoryDTO(0, name, displayName, null, null, null, 0);
    }

    /**
     * Creates a new category with full properties.
     *
     * @param name The unique category name
     * @param displayName The display name for UI
     * @param colorCode The color code (e.g., #FF5733 or §c)
     * @param icon The icon identifier
     * @param description The category description
     * @return A new QuestCategoryDTO
     */
    public static QuestCategoryDTO create(String name, String displayName, String colorCode,
                                         String icon, String description) {
        return new QuestCategoryDTO(0, name, displayName, colorCode, icon, description, 0);
    }

    /**
     * Creates a copy with updated display name.
     *
     * @param newDisplayName The new display name
     * @return A new QuestCategoryDTO with updated display name
     */
    public QuestCategoryDTO withDisplayName(String newDisplayName) {
        return new QuestCategoryDTO(id, name, newDisplayName, colorCode, icon, description, sortOrder);
    }

    /**
     * Creates a copy with updated color code.
     *
     * @param newColorCode The new color code
     * @return A new QuestCategoryDTO with updated color code
     */
    public QuestCategoryDTO withColorCode(String newColorCode) {
        return new QuestCategoryDTO(id, name, displayName, newColorCode, icon, description, sortOrder);
    }

    /**
     * Creates a copy with updated sort order.
     *
     * @param newSortOrder The new sort order
     * @return A new QuestCategoryDTO with updated sort order
     */
    public QuestCategoryDTO withSortOrder(int newSortOrder) {
        return new QuestCategoryDTO(id, name, displayName, colorCode, icon, description, newSortOrder);
    }

    /**
     * Creates a copy with updated description.
     *
     * @param newDescription The new description
     * @return A new QuestCategoryDTO with updated description
     */
    public QuestCategoryDTO withDescription(String newDescription) {
        return new QuestCategoryDTO(id, name, displayName, colorCode, icon, newDescription, sortOrder);
    }

    /**
     * Checks if this category has a color code.
     *
     * @return true if color code is present and not empty
     */
    public boolean hasColorCode() {
        return colorCode != null && !colorCode.isEmpty();
    }

    /**
     * Checks if this category has an icon.
     *
     * @return true if icon is present and not empty
     */
    public boolean hasIcon() {
        return icon != null && !icon.isEmpty();
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
     * Builder for constructing QuestCategoryDTO.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for QuestCategoryDTO.
     */
    public static class Builder {
        private int id = 0;
        private String name;
        private String displayName;
        private String colorCode;
        private String icon;
        private String description;
        private int sortOrder = 0;

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

        public Builder icon(String icon) {
            this.icon = icon;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder sortOrder(int sortOrder) {
            this.sortOrder = sortOrder;
            return this;
        }

        public QuestCategoryDTO build() {
            return new QuestCategoryDTO(id, name, displayName, colorCode, icon, description, sortOrder);
        }
    }
}
