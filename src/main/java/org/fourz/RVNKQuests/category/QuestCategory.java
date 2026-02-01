package org.fourz.RVNKQuests.category;

import org.bukkit.ChatColor;
import org.bukkit.Material;

/**
 * Quest category enumeration for organizing quests.
 *
 * <p>Provides predefined categories with display properties for quest organization
 * and filtering. Each category includes display name, color, and icon material for
 * consistent UI presentation.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * QuestCategory category = QuestCategory.MAIN_STORY;
 * String display = category.getDisplayName(); // "Main Story"
 * ChatColor color = category.getColor();      // GOLD
 * Material icon = category.getIconMaterial(); // ENCHANTED_BOOK
 * }</pre>
 */
public enum QuestCategory {

    /**
     * Main story quests that drive the primary narrative.
     */
    MAIN_STORY("Main Story", ChatColor.GOLD, Material.ENCHANTED_BOOK),

    /**
     * Optional side quests that provide additional content.
     */
    SIDE_QUEST("Side Quest", ChatColor.AQUA, Material.BOOK),

    /**
     * Daily repeatable quests that reset each day.
     */
    DAILY("Daily", ChatColor.GREEN, Material.CLOCK),

    /**
     * Weekly repeatable quests that reset each week.
     */
    WEEKLY("Weekly", ChatColor.YELLOW, Material.PAPER),

    /**
     * Time-limited event quests.
     */
    EVENT("Event", ChatColor.LIGHT_PURPLE, Material.FIREWORK_ROCKET),

    /**
     * Difficult challenge quests for experienced players.
     */
    CHALLENGE("Challenge", ChatColor.RED, Material.DIAMOND_SWORD);

    private final String displayName;
    private final ChatColor color;
    private final Material iconMaterial;

    /**
     * Constructs a quest category with display properties.
     *
     * @param displayName The human-readable display name
     * @param color The ChatColor for UI presentation
     * @param iconMaterial The Material icon for visual representation
     */
    QuestCategory(String displayName, ChatColor color, Material iconMaterial) {
        this.displayName = displayName;
        this.color = color;
        this.iconMaterial = iconMaterial;
    }

    /**
     * Gets the display name for this category.
     *
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the ChatColor for this category.
     *
     * @return The ChatColor
     */
    public ChatColor getColor() {
        return color;
    }

    /**
     * Gets the icon Material for this category.
     *
     * @return The Material icon
     */
    public Material getIconMaterial() {
        return iconMaterial;
    }

    /**
     * Gets the colored display name.
     *
     * @return The display name prefixed with color code
     */
    public String getColoredDisplayName() {
        return color + displayName + ChatColor.RESET;
    }

    /**
     * Parses a category from string name (case-insensitive).
     *
     * @param name The category name (enum constant or display name)
     * @return The matching QuestCategory, or null if not found
     */
    public static QuestCategory fromString(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        // Try exact enum match first
        try {
            return valueOf(name.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            // Try display name match
            for (QuestCategory category : values()) {
                if (category.displayName.equalsIgnoreCase(name)) {
                    return category;
                }
            }
            return null;
        }
    }

    /**
     * Checks if a string is a valid category name.
     *
     * @param name The category name to validate
     * @return true if the name matches a valid category
     */
    public static boolean isValid(String name) {
        return fromString(name) != null;
    }
}
