package org.fourz.RVNKQuests.waypoint;

import org.fourz.RVNKQuests.quest.QuestState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Active set, nearest selection, cross-world selection and progress (#2264). */
@DisplayName("Waypoint resolution (#2264)")
class WaypointResolverTest {

    private static Waypoint wp(String id, String world, double x, double y, double z) {
        return new Waypoint(id, world, x, y, z, id, WaypointStyle.BOSSBAR, false);
    }

    private static final Waypoint DOOR = wp("obj_door", "sotw_sky_0", -421, 98, 19);
    private static final Waypoint RELL = wp("obj_rell", "sotw_sky_0", -15, 86, -1);
    private static final Waypoint WELL = wp("obj_well", "sotw_city", 8, 64, 8);

    private static Map<String, Object> metadata() {
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("NOT_STARTED", List.of("trig_rell"));
        mapping.put("QUEST_ACTIVE", List.of("obj_door", "obj_npc", "obj_well"));
        mapping.put("OBJECTIVE_FOUND", List.of("obj_rell"));
        return Map.of("state_mapping", mapping);
    }

    private static Map<String, Waypoint> waypoints() {
        Map<String, Waypoint> m = new LinkedHashMap<>();
        m.put("obj_door", DOOR);
        m.put("obj_rell", RELL);
        m.put("obj_well", WELL);
        return m;
    }

    @Test
    @DisplayName("active set = the state's components that have a waypoint, in state_mapping order")
    void activeSet() {
        assertEquals(List.of(DOOR, WELL), WaypointResolver.activeSet(metadata(), "QUEST_ACTIVE", waypoints()));
        assertEquals(List.of(RELL), WaypointResolver.activeSet(metadata(), "OBJECTIVE_FOUND", waypoints()));
        assertEquals(List.of(), WaypointResolver.activeSet(metadata(), "NOT_STARTED", waypoints()));
        assertEquals(List.of(), WaypointResolver.activeSet(metadata(), "COMPLETED", waypoints()));
        assertEquals(List.of(), WaypointResolver.activeSet(Map.of(), "QUEST_ACTIVE", waypoints()));
        assertEquals(List.of(), WaypointResolver.activeSet(metadata(), "QUEST_ACTIVE", Map.of()));
    }

    @Test
    @DisplayName("activeSets precomputes every state that has one")
    void activeSets() {
        Map<QuestState, List<Waypoint>> sets = WaypointResolver.activeSets(metadata(), waypoints());
        assertEquals(2, sets.size());
        assertEquals(List.of(DOOR, WELL), sets.get(QuestState.QUEST_ACTIVE));
        assertTrue(WaypointResolver.activeSets(metadata(), Map.of()).isEmpty());
    }

    @Test
    @DisplayName("shown = the nearest in the player's world")
    void nearestSameWorld() {
        List<Waypoint> both = List.of(DOOR, RELL);
        // At Rell's bridge: Rell is nearer.
        assertEquals(RELL, WaypointResolver.select(both, "sotw_sky_0", -20, 86, 0));
        // Out by the gold dungeon: the door is nearer.
        assertEquals(DOOR, WaypointResolver.select(both, "sotw_sky_0", -400, 98, 20));
        // World names match without case (Paper lowercases migrated names).
        assertEquals(DOOR, WaypointResolver.select(both, "SOTW_SKY_0", -400, 98, 20));
    }

    @Test
    @DisplayName("a nearer target in another world never wins over a same-world one")
    void sameWorldBeatsNearerOtherWorld() {
        // The player stands right on top of the well's x/z, but in the sky world.
        assertEquals(DOOR, WaypointResolver.select(List.of(WELL, DOOR), "sotw_sky_0", 8, 64, 8));
    }

    @Test
    @DisplayName("none in the player's world: the first of the active set")
    void crossWorldFallsBackToFirst() {
        assertEquals(WELL, WaypointResolver.select(List.of(WELL, DOOR), "world", 0, 64, 0));
        assertEquals(DOOR, WaypointResolver.select(List.of(DOOR, WELL), "world", 0, 64, 0));
        assertNull(WaypointResolver.select(List.of(), "world", 0, 0, 0));
        assertNull(WaypointResolver.select(null, "world", 0, 0, 0));
    }

    @Test
    @DisplayName("progress = 1 - min(1, dist / startDist), clamped")
    void progress() {
        assertEquals(0.0, WaypointResolver.progress(400, 400), 1e-9);
        assertEquals(0.5, WaypointResolver.progress(200, 400), 1e-9);
        assertEquals(0.75, WaypointResolver.progress(100, 400), 1e-9);
        assertEquals(1.0, WaypointResolver.progress(0, 400), 1e-9);
        // Walked the wrong way: further than the start is 0, not negative.
        assertEquals(0.0, WaypointResolver.progress(900, 400), 1e-9);
        // Tracking started on top of the target.
        assertEquals(1.0, WaypointResolver.progress(3, 0), 1e-9);
        assertEquals(1.0, WaypointResolver.progress(3, -1), 1e-9);
    }
}
