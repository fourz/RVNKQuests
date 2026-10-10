package org.fourz.RVNKQuests.waypoint;

import java.util.Locale;

/**
 * A parsed {@code /quest track} (#2264). Pure, so the grammar is unit-tested.
 *
 * <pre>
 * /quest track                       show what is tracked
 * /quest track off [player]          stop tracking
 * /quest track &lt;quest_id&gt; [--trail] [player]
 * </pre>
 *
 * <p>{@code --trail} may sit anywhere after the subcommand. The first other token is the quest id
 * (or {@code off}), the second is a player name (staff and console).</p>
 *
 * @param action  what to do
 * @param questId the quest to track; null unless {@link Action#TRACK}
 * @param trail   true when {@code --trail} was given
 * @param player  a target player name, or null for the sender
 * @param error   a usage error, or null when the input parsed
 */
public record TrackRequest(Action action, String questId, boolean trail, String player, String error) {

    public enum Action { SHOW, OFF, TRACK }

    public static final String TRAIL_FLAG = "--trail";

    public static TrackRequest parse(String[] args) {
        String first = null;
        String second = null;
        boolean trail = false;
        if (args != null) {
            for (String raw : args) {
                if (raw == null || raw.isBlank()) continue;
                String a = raw.trim();
                if (a.equalsIgnoreCase(TRAIL_FLAG)) {
                    trail = true;
                } else if (a.startsWith("--")) {
                    return error("Unknown option " + a + " - the only option is " + TRAIL_FLAG);
                } else if (first == null) {
                    first = a;
                } else if (second == null) {
                    second = a;
                } else {
                    return error("Too many arguments");
                }
            }
        }
        if (first == null) {
            return trail ? error(TRAIL_FLAG + " needs a quest id") : new TrackRequest(Action.SHOW, null, false, null, null);
        }
        if (first.toLowerCase(Locale.ROOT).equals("off")) {
            if (trail) return error(TRAIL_FLAG + " does not apply to off");
            return new TrackRequest(Action.OFF, null, false, second, null);
        }
        return new TrackRequest(Action.TRACK, first, trail, second, null);
    }

    private static TrackRequest error(String message) {
        return new TrackRequest(null, null, false, null, message);
    }

    public boolean isError() {
        return error != null;
    }
}
