package org.fourz.RVNKQuests.category;

import org.bukkit.ChatColor;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Quest tag model for flexible quest filtering and categorization.
 *
 * <p>Immutable record representing a tag with name, description, and color.
 * Provides predefined common tags and validation for custom tags.</p>
 *
 * <p>Predefined Tags:</p>
 * <ul>
 *   <li>PVE - Player vs Environment content</li>
 *   <li>PVP - Player vs Player content</li>
 *   <li>EXPLORATION - World exploration quests</li>
 *   <li>CRAFTING - Crafting and building quests</li>
 *   <li>SOCIAL - Social interaction quests</li>
 *   <li>COMBAT - Combat-focused quests</li>
 *   <li>PUZZLE - Puzzle and problem-solving quests</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * QuestTag tag = QuestTag.PVE;
 * QuestTag custom = QuestTag.create("Mining", "Mining-related quests", "YELLOW");
 * }</pre>
 */
public record QuestTag(
    String name,
    String description,
    String color
) {
    // Hex color pattern for validation (#RRGGBB or #RGB)
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$");

    // Predefined common tags
    public static final QuestTag PVE = new QuestTag("PvE", "Player vs Environment", "GREEN");
    public static final QuestTag PVP = new QuestTag("PvP", "Player vs Player", "RED");
    public static final QuestTag EXPLORATION = new QuestTag("Exploration", "World exploration", "BLUE");
    public static final QuestTag CRAFTING = new QuestTag("Crafting", "Crafting and building", "YELLOW");
    public static final QuestTag SOCIAL = new QuestTag("Social", "Social interaction", "LIGHT_PURPLE");
    public static final QuestTag COMBAT = new QuestTag("Combat", "Combat focused", "DARK_RED");
    public static final QuestTag PUZZLE = new QuestTag("Puzzle", "Puzzle and problem solving", "AQUA");

    /**
     * Compact constructor with validation.
     */
    public QuestTag {
        Objects.requireNonNull(name, "Tag name cannot be null");
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException("Tag name cannot be empty");
        }
        if (color != null && !isValidColor(color)) {
            throw new IllegalArgumentException("Invalid color format: " + color + ". Use ChatColor name or hex (#RRGGBB)");
        }
    }

    /**
     * Creates a new quest tag with basic properties.
     *
     * @param name The tag name
     * @param description The tag description
     * @param color The color (ChatColor name or hex code)
     * @return A new QuestTag
     */
    public static QuestTag create(String name, String description, String color) {
        return new QuestTag(name, description, color);
    }

    /**
     * Creates a new quest tag without color.
     *
     * @param name The tag name
     * @param description The tag description
     * @return A new QuestTag with default color
     */
    public static QuestTag create(String name, String description) {
        return new QuestTag(name, description, "WHITE");
    }

    /**
     * Validates a color string.
     *
     * @param color The color string to validate
     * @return true if color is valid (ChatColor name or hex format)
     */
    public static boolean isValidColor(String color) {
        if (color == null || color.isEmpty()) {
            return false;
        }

        // Check if it's a hex color
        if (HEX_COLOR_PATTERN.matcher(color).matches()) {
            return true;
        }

        // Check if it's a valid ChatColor name
        try {
            ChatColor.valueOf(color.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Gets the ChatColor for this tag.
     *
     * @return The ChatColor, or WHITE if color is invalid
     */
    public ChatColor getChatColor() {
        if (color == null || color.isEmpty()) {
            return ChatColor.WHITE;
        }

        // Try to parse as ChatColor name
        try {
            return ChatColor.valueOf(color.toUpperCase());
        } catch (IllegalArgumentException e) {
            // If it's a hex color, we can't convert directly to ChatColor
            // Return WHITE as fallback (hex colors would need RGB conversion)
            return ChatColor.WHITE;
        }
    }

    /**
     * Gets the colored display name.
     *
     * @return The name prefixed with color code
     */
    public String getColoredName() {
        return getChatColor() + name + ChatColor.RESET;
    }

    /**
     * Checks if this tag has a color defined.
     *
     * @return true if color is present and not empty
     */
    public boolean hasColor() {
        return color != null && !color.isEmpty();
    }

    /**
     * Checks if this tag uses a hex color.
     *
     * @return true if color is in hex format
     */
    public boolean isHexColor() {
        return color != null && HEX_COLOR_PATTERN.matcher(color).matches();
    }

    /**
     * Creates a copy with updated description.
     *
     * @param newDescription The new description
     * @return A new QuestTag with updated description
     */
    public QuestTag withDescription(String newDescription) {
        return new QuestTag(name, newDescription, color);
    }

    /**
     * Creates a copy with updated color.
     *
     * @param newColor The new color
     * @return A new QuestTag with updated color
     */
    public QuestTag withColor(String newColor) {
        return new QuestTag(name, description, newColor);
    }

    /**
     * Builder for constructing QuestTag.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for QuestTag.
     */
    public static class Builder {
        private String name;
        private String description;
        private String color = "WHITE";

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder color(String color) {
            this.color = color;
            return this;
        }

        public QuestTag build() {
            return new QuestTag(name, description, color);
        }
    }
}
