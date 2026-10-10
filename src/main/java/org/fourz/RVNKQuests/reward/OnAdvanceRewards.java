package org.fourz.RVNKQuests.reward;

import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.data.dto.RewardType;
import org.fourz.RVNKQuests.service.OnceRewards;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parses a component's {@code on_advance} list (#2267): rewards given when that component's
 * advance commits, not only at quest completion.
 *
 * <pre>
 * obj_gold_door:
 *   objective_type: INTERACT
 *   block_type: GOLD_BLOCK
 *   advance_state: OBJECTIVE_FOUND
 *   on_advance:
 *   - reward_id: a_key
 *     type: COMMAND
 *     value: lore item give %player% Lodestone Key
 *   - reward_id: b_xp
 *     type: EXPERIENCE
 *     amount: 50
 * </pre>
 *
 * <p>Each entry is one reward of an existing type, with the same fields as an entry under
 * {@code rewards:} — {@code type}, {@code value}, {@code amount}, {@code description},
 * {@code metadata}, {@code once} — plus {@code reward_id}. A map keyed by reward id (the shape of
 * {@code rewards:}) is accepted too.</p>
 *
 * <p><b>Order.</b> Entries sort by {@code reward_id}, the order {@code quest_definition_rewards}
 * is read in ({@code ORDER BY reward_id}); delivery then applies {@code RewardServiceImpl}'s stable
 * priority sort, exactly as for completion rewards. An entry without a {@code reward_id} gets
 * {@code <component>_NN} from its list position, so a list of unnamed entries keeps its order.</p>
 *
 * <p>The list lives inside the component's config in the quest {@code metadata} JSON, so
 * {@code quest import} and {@code quest export} carry it with no schema change.</p>
 */
public final class OnAdvanceRewards {

    /** The component config key. */
    public static final String CONFIG_KEY = "on_advance";

    /** Keys an entry understands. */
    public static final Set<String> ENTRY_KEYS = Set.of(
            "reward_id", "type", "value", "amount", "description", "metadata", OnceRewards.METADATA_KEY);

    /** The reward_id order {@code quest_definition_rewards} is read in. */
    public static final Comparator<RewardDTO> REWARD_ID_ORDER =
            Comparator.comparing(RewardDTO::rewardId, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(RewardDTO::rewardId);

    private OnAdvanceRewards() {
    }

    /**
     * Parses one component's {@code on_advance} value.
     *
     * @param componentId the component, for default ids and messages
     * @param raw         the value of the {@code on_advance} key; null yields an empty list
     * @param problems    receives one line per skipped entry or ignored key; may be null
     * @return the rewards, sorted by reward_id; never null
     */
    public static List<RewardDTO> parse(String componentId, Object raw, List<String> problems) {
        List<String> sink = problems != null ? problems : new ArrayList<>();
        List<RewardDTO> out = new ArrayList<>();
        if (raw == null) return out;

        if (raw instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                String fallbackId = componentId + "_" + String.format(Locale.ROOT, "%02d", i + 1);
                RewardDTO r = entry(componentId, fallbackId, list.get(i), true, sink);
                if (r != null) out.add(r);
            }
        } else if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                RewardDTO r = entry(componentId, String.valueOf(e.getKey()), e.getValue(), false, sink);
                if (r != null) out.add(r);
            }
        } else {
            sink.add("component '" + componentId + "' on_advance must be a list of rewards - ignored");
            return out;
        }
        out.sort(REWARD_ID_ORDER);
        return out;
    }

    private static RewardDTO entry(String componentId, String fallbackId, Object rawEntry, boolean idFromEntry,
                                   List<String> problems) {
        String where = "component '" + componentId + "' on_advance";
        if (!(rawEntry instanceof Map<?, ?> e)) {
            problems.add(where + " entry " + fallbackId + " is not a map - skipped");
            return null;
        }
        String rewardId = fallbackId;
        if (idFromEntry && e.get("reward_id") != null && !String.valueOf(e.get("reward_id")).isBlank()) {
            rewardId = String.valueOf(e.get("reward_id")).trim();
        }
        where += " '" + rewardId + "'";

        Object typeRaw = e.get("type");
        RewardType type = null;
        if (typeRaw != null) {
            try {
                type = RewardType.valueOf(String.valueOf(typeRaw).trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // reported below
            }
        }
        if (type == null) {
            problems.add(where + " has " + (typeRaw == null ? "no type" : "unknown type '" + typeRaw + "'")
                    + " - skipped");
            return null;
        }

        for (Object key : e.keySet()) {
            String k = String.valueOf(key);
            if (!ENTRY_KEYS.contains(k)) {
                problems.add(where + " ignores unknown key '" + k + "'");
            }
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        if (e.get("metadata") instanceof Map<?, ?> meta) {
            for (Map.Entry<?, ?> m : meta.entrySet()) {
                metadata.put(String.valueOf(m.getKey()), text(m.getValue()));
            }
        }
        Object once = e.get(OnceRewards.METADATA_KEY);
        if (once != null) {
            if (OnceRewards.SERVER.equalsIgnoreCase(String.valueOf(once).trim())) {
                metadata.put(OnceRewards.METADATA_KEY, OnceRewards.SERVER);
            } else {
                problems.add(where + " once '" + once + "' is not 'server' - ignored, fires for every player");
            }
        }

        return new RewardDTO(rewardId, type, text(e.get("value")), amount(e.get("amount")),
                e.get("description") != null ? String.valueOf(e.get("description")) : null, metadata);
    }

    /** Amount as an int; Gson reads JSON numbers back as doubles, so 1.0 is 1. Default 1. */
    private static int amount(Object raw) {
        if (raw instanceof Number n) return n.intValue();
        if (raw != null) {
            try {
                return (int) Double.parseDouble(String.valueOf(raw).trim());
            } catch (NumberFormatException ignored) {
                // default
            }
        }
        return 1;
    }

    /** Text form of a value; a whole-number double from Gson renders without ".0". */
    private static String text(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Double d && d == Math.rint(d) && !d.isInfinite()) {
            return String.valueOf(d.longValue());
        }
        return String.valueOf(raw);
    }
}
