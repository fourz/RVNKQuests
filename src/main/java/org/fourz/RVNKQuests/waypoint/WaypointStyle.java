package org.fourz.RVNKQuests.waypoint;

import java.util.Locale;

/**
 * How a waypoint is shown (#2264).
 *
 * <ul>
 *   <li>{@link #BOSSBAR} (default): label, distance and an arrow from the player's facing. Any world.</li>
 *   <li>{@link #COMPASS}: points the player's compass at the target while they hold one, same world
 *       only. Anywhere else it shows the bossbar.</li>
 *   <li>{@link #PARTICLES}: shows the bossbar; the author marks the trail ({@code /quest track <id> --trail})
 *       as the intended aid. The trail itself runs only on request, for any style.</li>
 * </ul>
 */
public enum WaypointStyle {
    BOSSBAR,
    COMPASS,
    PARTICLES;

    /**
     * Parses an authored style, any case.
     *
     * @return the style, or null when the value is not a known style
     */
    public static WaypointStyle parse(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).trim().toUpperCase(Locale.ROOT);
        for (WaypointStyle style : values()) {
            if (style.name().equals(s)) return style;
        }
        return null;
    }

    /** The authored spelling, lower case. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
