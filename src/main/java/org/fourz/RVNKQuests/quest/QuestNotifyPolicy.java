package org.fourz.RVNKQuests.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Per-quest notification control (#2266): quest metadata {@code notify}.
 *
 * <pre>
 * metadata:
 *   notify:
 *     start_popup: true     # "Quest Started" title + chat line
 *     complete_popup: true  # "Quest Complete!" title + chat line
 *     broadcast: false      # "&lt;player&gt; has completed the quest &lt;name&gt;!" to the server
 * </pre>
 *
 * <p><b>Defaults are today's behaviour.</b> A missing {@code notify} block, a missing key, or a
 * value that is not a boolean reads as {@code true}, so every quest that existed before 1.1.69
 * behaves exactly as it did.</p>
 *
 * <p><b>Who wins.</b> A popup goes out only when the quest allows it AND the player's own
 * {@code /quest prefs} allow it: {@code NotificationServiceImpl} still applies the player's
 * preferences after this policy says yes. The server-wide broadcast needs both this quest's
 * {@code broadcast} and the global master switch {@code quests.announce_completion}.</p>
 *
 * @param startPopup    send the quest-start title and chat line
 * @param completePopup send the quest-complete title and chat line
 * @param broadcast     allow the server-wide completion broadcast for this quest
 */
public record QuestNotifyPolicy(boolean startPopup, boolean completePopup, boolean broadcast) {

    /** The metadata key that holds the block. */
    public static final String METADATA_KEY = "notify";

    /** Keys the block understands. */
    public static final Set<String> KEYS = Set.of("start_popup", "complete_popup", "broadcast");

    /** Today's behaviour: everything on. */
    public static final QuestNotifyPolicy DEFAULT = new QuestNotifyPolicy(true, true, true);

    /**
     * Reads the policy from a quest's metadata map.
     *
     * @param metadata the quest metadata; null or empty yields {@link #DEFAULT}
     * @return the policy, never null
     */
    public static QuestNotifyPolicy fromMetadata(Map<String, Object> metadata) {
        if (metadata == null) return DEFAULT;
        Object raw = metadata.get(METADATA_KEY);
        if (!(raw instanceof Map<?, ?> block)) return DEFAULT;
        return new QuestNotifyPolicy(
                flag(block.get("start_popup")),
                flag(block.get("complete_popup")),
                flag(block.get("broadcast")));
    }

    /**
     * Whether the completion broadcast goes out.
     *
     * @param globalAnnounce the server-wide master switch {@code quests.announce_completion}
     * @return true only when both this quest and the server allow it
     */
    public boolean shouldBroadcast(boolean globalAnnounce) {
        return broadcast && globalAnnounce;
    }

    /**
     * Author-facing problems with a quest's {@code notify} block, for load warnings and
     * {@code /quest validate}. Every problem falls back to the default, so none of them stops a
     * quest from loading.
     *
     * @param metadata the quest metadata
     * @return human-readable problems; empty when the block is absent or clean
     */
    public static List<String> problems(Map<String, Object> metadata) {
        List<String> out = new ArrayList<>();
        if (metadata == null || !metadata.containsKey(METADATA_KEY)) return out;
        Object raw = metadata.get(METADATA_KEY);
        if (!(raw instanceof Map<?, ?> block)) {
            out.add("notify must be a map of start_popup/complete_popup/broadcast - ignored, all on");
            return out;
        }
        for (Map.Entry<?, ?> e : block.entrySet()) {
            String key = String.valueOf(e.getKey());
            if (!KEYS.contains(key)) {
                out.add("notify." + key + " is not a known key (start_popup, complete_popup, broadcast)");
            } else if (parse(e.getValue()) == null) {
                out.add("notify." + key + " '" + e.getValue() + "' is not true/false - using true");
            }
        }
        return out;
    }

    /** A boolean, or "true"/"false" text; anything else (including absence) is the default, true. */
    private static boolean flag(Object value) {
        Boolean parsed = parse(value);
        return parsed == null || parsed;
    }

    private static Boolean parse(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) {
            String t = s.trim().toLowerCase(Locale.ROOT);
            if (t.equals("true")) return Boolean.TRUE;
            if (t.equals("false")) return Boolean.FALSE;
        }
        return null;
    }
}
