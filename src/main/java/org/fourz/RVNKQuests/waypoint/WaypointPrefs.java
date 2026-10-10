package org.fourz.RVNKQuests.waypoint;

import java.util.Locale;
import java.util.Map;

/**
 * The per-player waypoint settings and how they are stored (#2264). Pure.
 *
 * <p>Both live in the RVNKQuests player preference table ({@code quest_player_preferences}), the
 * same store as {@code /quest prefs}. RVNKCore's PlayerPreferencesService has no free key/value
 * slot, so these two keys always use the local table, whichever store the notification prefs use.</p>
 */
public final class WaypointPrefs {

    /** {@code "true"} / {@code "false"}; absent = on. */
    public static final String KEY_ENABLED = "waypoints_enabled";
    /** The tracked quest id; absent or blank = nothing tracked. */
    public static final String KEY_TRACKED = "tracked_quest";

    private WaypointPrefs() {
    }

    /**
     * Parses {@code /quest prefs waypoints <on|off>}.
     *
     * @return TRUE for on, FALSE for off, null when the word is neither
     */
    public static Boolean parseToggle(String word) {
        if (word == null) return null;
        return switch (word.trim().toLowerCase(Locale.ROOT)) {
            case "on", "true", "enable", "enabled", "yes" -> Boolean.TRUE;
            case "off", "false", "disable", "disabled", "no" -> Boolean.FALSE;
            default -> null;
        };
    }

    /** Waypoints on unless the stored value is exactly {@code false}. Default on. */
    public static boolean enabledFrom(Map<String, String> stored) {
        if (stored == null) return true;
        String v = stored.get(KEY_ENABLED);
        return v == null || !v.trim().equalsIgnoreCase("false");
    }

    /** The stored tracked quest, or null. */
    public static String trackedFrom(Map<String, String> stored) {
        if (stored == null) return null;
        String v = stored.get(KEY_TRACKED);
        return v == null || v.isBlank() ? null : v.trim();
    }
}
