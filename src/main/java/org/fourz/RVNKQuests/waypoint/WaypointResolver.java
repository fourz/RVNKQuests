package org.fourz.RVNKQuests.waypoint;

import org.fourz.RVNKQuests.quest.QuestState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Picks what a tracked quest points at (#2264). Pure: no Bukkit, so every rule is unit-tested.
 *
 * <ol>
 *   <li><b>Active set.</b> The components listed in {@code state_mapping} for the player's current
 *       state that have a waypoint, in {@code state_mapping} order.</li>
 *   <li><b>Shown.</b> The nearest of those in the player's world, by straight-line distance. When
 *       none is in the player's world, the first of the set.</li>
 * </ol>
 */
public final class WaypointResolver {

    private WaypointResolver() {
    }

    /**
     * The active set for one state.
     *
     * @param metadata  the quest metadata (reads {@code state_mapping})
     * @param state     the state name, e.g. {@code QUEST_ACTIVE}
     * @param waypoints every component's waypoint, from {@link WaypointParser#parseAll}
     * @return the waypoints of the components active in that state; empty when none
     */
    public static List<Waypoint> activeSet(Map<String, Object> metadata, String state, Map<String, Waypoint> waypoints) {
        if (metadata == null || state == null || waypoints == null || waypoints.isEmpty()) return List.of();
        if (!(metadata.get("state_mapping") instanceof Map<?, ?> mapping)) return List.of();
        if (!(mapping.get(state) instanceof List<?> ids)) return List.of();
        List<Waypoint> out = new ArrayList<>(ids.size());
        for (Object id : ids) {
            Waypoint wp = waypoints.get(String.valueOf(id));
            if (wp != null && !out.contains(wp)) out.add(wp);
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    /**
     * The active set of every state, precomputed once per quest load so the tick task never walks
     * metadata.
     */
    public static Map<QuestState, List<Waypoint>> activeSets(Map<String, Object> metadata, Map<String, Waypoint> waypoints) {
        if (waypoints == null || waypoints.isEmpty()) return Collections.emptyMap();
        Map<QuestState, List<Waypoint>> out = new EnumMap<>(QuestState.class);
        for (QuestState state : QuestState.values()) {
            List<Waypoint> set = activeSet(metadata, state.name(), waypoints);
            if (!set.isEmpty()) out.put(state, set);
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * The waypoint to show.
     *
     * @param active      the active set
     * @param playerWorld the player's world name
     * @return the nearest same-world waypoint; else the first of the set; null when the set is empty
     */
    public static Waypoint select(List<Waypoint> active, String playerWorld, double px, double py, double pz) {
        if (active == null || active.isEmpty()) return null;
        Waypoint best = null;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0, n = active.size(); i < n; i++) {
            Waypoint wp = active.get(i);
            if (!wp.isInWorld(playerWorld)) continue;
            double d = wp.distanceSquared(px, py, pz);
            if (d < bestDist) {
                bestDist = d;
                best = wp;
            }
        }
        return best != null ? best : active.get(0);
    }

    /**
     * Bossbar progress: {@code 1 - min(1, dist / startDist)}, clamped to 0..1.
     *
     * @param dist      the distance now
     * @param startDist the distance when tracking (of this target) started; 0 or less reads as arrived
     */
    public static double progress(double dist, double startDist) {
        if (!(startDist > 0)) return 1.0;
        if (!(dist > 0)) return 1.0;
        double p = 1.0 - Math.min(1.0, dist / startDist);
        return p < 0 ? 0 : (p > 1 ? 1 : p);
    }
}
