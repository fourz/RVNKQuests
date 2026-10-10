package org.fourz.RVNKQuests.npc;

import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent;

import java.util.Locale;

/**
 * The {@code click} config key of the NPC quest components (#2214): which click on the NPC counts.
 */
public enum NpcClickFilter {
    /** Left or right click. */
    ANY,
    /** Right click only. The default: a left click is an attack, not a conversation. */
    RIGHT,
    /** Left click only. */
    LEFT;

    /**
     * Parses a config value ({@code any}, {@code right}, {@code left}, any case).
     *
     * @return the filter, or null when the value is not one of the three (the caller warns and
     *         falls back); {@link #RIGHT} for null or blank
     */
    public static NpcClickFilter parse(String value) {
        if (value == null || value.isBlank()) return RIGHT;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** True when this filter accepts the click. A null click never matches. */
    public boolean accepts(RvnkNpcInteractEvent.ClickType click) {
        if (click == null) return false;
        return switch (this) {
            case ANY -> true;
            case RIGHT -> click == RvnkNpcInteractEvent.ClickType.RIGHT;
            case LEFT -> click == RvnkNpcInteractEvent.ClickType.LEFT;
        };
    }
}
