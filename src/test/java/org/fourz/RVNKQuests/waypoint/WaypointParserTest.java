package org.fourz.RVNKQuests.waypoint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The {@code waypoint} block and {@code metadata.waypoints: auto} (#2264). */
@DisplayName("Waypoint YAML parsing and auto mode (#2264)")
class WaypointParserTest {

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return m;
    }

    private static Map<String, Object> quest(Object autoValue, Map<String, Object> components) {
        Map<String, Object> m = new HashMap<>();
        if (autoValue != null) m.put("waypoints", autoValue);
        m.put("components", components);
        return m;
    }

    @Test
    @DisplayName("an authored block is read with every field")
    void authoredBlock() {
        Map<String, Object> comp = map("objective_type", "INTERACT", "description", "Open the gold door",
            "waypoint", map("world", "sotw_sky_0", "x", -421, "y", 98, "z", 19, "label", "The gold door", "style", "Compass"));
        List<String> problems = new ArrayList<>();
        Map<String, Waypoint> all = WaypointParser.parseAll(quest(null, map("obj_door", comp)), problems);
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(new Waypoint("obj_door", "sotw_sky_0", -421, 98, 19, "The gold door", WaypointStyle.COMPASS, false),
            all.get("obj_door"));
    }

    @Test
    @DisplayName("defaults: style bossbar; label = description, then component id")
    void defaults() {
        Waypoint withDesc = WaypointParser.parse("obj_a",
            map("description", "Reach the well", "waypoint", map("world", "w", "x", 1, "y", 2, "z", 3)), false, null);
        assertEquals("Reach the well", withDesc.label());
        assertEquals(WaypointStyle.BOSSBAR, withDesc.style());
        Waypoint bare = WaypointParser.parse("obj_b", map("waypoint", map("world", "w", "x", 1, "y", 2, "z", 3)), false, null);
        assertEquals("obj_b", bare.label());
    }

    @Test
    @DisplayName("numbers as YAML ints, Gson doubles or numeric strings")
    void numberForms() {
        Waypoint a = WaypointParser.parse("c", map("waypoint", map("world", "w", "x", -421, "y", 98, "z", 19)), false, null);
        Waypoint b = WaypointParser.parse("c", map("waypoint", map("world", "w", "x", -421.0, "y", 98.0, "z", 19.0)), false, null);
        Waypoint c = WaypointParser.parse("c", map("waypoint", map("world", "w", "x", "-421", "y", " 98 ", "z", "19.0")), false, null);
        assertEquals(a, b);
        assertEquals(a, c);
    }

    @Test
    @DisplayName("a bad block is reported and that component gets no waypoint")
    void badBlocks() {
        List<String> problems = new ArrayList<>();
        assertNull(WaypointParser.parse("no_world", map("waypoint", map("x", 1, "y", 2, "z", 3)), false, problems));
        assertNull(WaypointParser.parse("no_y", map("waypoint", map("world", "w", "x", 1, "z", 3)), false, problems));
        assertNull(WaypointParser.parse("text_x", map("waypoint", map("world", "w", "x", "far", "y", 2, "z", 3)), false, problems));
        assertNull(WaypointParser.parse("scalar", map("waypoint", "sotw_sky_0 -421 98 19"), false, problems));
        assertEquals(4, problems.size(), problems.toString());
    }

    @Test
    @DisplayName("an unknown style falls back to bossbar and is reported")
    void unknownStyle() {
        List<String> problems = new ArrayList<>();
        Waypoint wp = WaypointParser.parse("c", map("waypoint", map("world", "w", "x", 1, "y", 2, "z", 3, "style", "beacon")), false, problems);
        assertEquals(WaypointStyle.BOSSBAR, wp.style());
        assertEquals(1, problems.size());
        assertEquals(WaypointStyle.PARTICLES, WaypointStyle.parse("particles"));
        assertEquals(WaypointStyle.BOSSBAR, WaypointStyle.parse(" BossBar "));
        assertNull(WaypointStyle.parse(null));
    }

    @Test
    @DisplayName("auto is off by default: a LOCATION_PROXIMITY with coordinates gets no waypoint")
    void autoOffByDefault() {
        Map<String, Object> comps = map(
            "trig_well", map("type", "LOCATION_PROXIMITY", "world", "sotw_city", "x", 8, "y", 64, "z", 8),
            "obj_reach", map("objective_type", "REACH", "world", "sotw_city", "x", 8, "y", 64, "z", 8));
        assertTrue(WaypointParser.parseAll(quest(null, comps), null).isEmpty());
        assertTrue(WaypointParser.parseAll(quest(false, comps), null).isEmpty());
        assertTrue(WaypointParser.parseAll(quest("off", comps), null).isEmpty());
        assertTrue(WaypointParser.parseAll(null, null).isEmpty());
    }

    @Test
    @DisplayName("auto: LOCATION_PROXIMITY and REACH take their own world/x/y/z")
    void autoDerives() {
        Map<String, Object> comps = map(
            "trig_well", map("type", "LOCATION_PROXIMITY", "world", "sotw_city", "x", 8, "y", 64, "z", 8,
                "description", "The collapsed well"),
            "obj_reach", map("objective_type", "REACH", "x", 100, "z", -50),
            "obj_ctx", map("objective_type", "REACH", "x", 1, "z", 1, "context_location_key", "spawn_loc"),
            "obj_kill", map("objective_type", "KILL", "world", "w", "x", 1, "y", 2, "z", 3),
            "trig_noxz", map("type", "LOCATION_PROXIMITY", "world", "w"),
            "trig_optout", map("type", "LOCATION_PROXIMITY", "world", "w", "x", 1, "z", 1, "waypoint", false),
            "trig_optout2", map("type", "LOCATION_PROXIMITY", "world", "w", "x", 1, "z", 1, "waypoint", "off"),
            "trig_authored", map("type", "LOCATION_PROXIMITY", "world", "w", "x", 1, "z", 1,
                "waypoint", map("world", "other", "x", 9, "y", 9, "z", 9, "style", "particles")));
        List<String> problems = new ArrayList<>();
        Map<String, Waypoint> all = WaypointParser.parseAll(quest("AUTO", comps), problems);
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(List.of("trig_well", "obj_reach", "trig_authored"), new ArrayList<>(all.keySet()));
        assertEquals(new Waypoint("trig_well", "sotw_city", 8, 64, 8, "The collapsed well", WaypointStyle.BOSSBAR, true),
            all.get("trig_well"));
        // The component defaults: world "world", y 64.
        assertEquals(new Waypoint("obj_reach", "world", 100, 64, -50, "obj_reach", WaypointStyle.BOSSBAR, true),
            all.get("obj_reach"));
        // An authored block beats auto.
        assertFalse(all.get("trig_authored").derived());
        assertEquals("other", all.get("trig_authored").world());
    }

    @Test
    @DisplayName("a waypoints value other than auto/off is reported and ignored")
    void badAutoValue() {
        List<String> problems = new ArrayList<>();
        assertFalse(WaypointParser.isAuto(map("waypoints", "always"), problems));
        assertEquals(1, problems.size());
        assertTrue(WaypointParser.isAuto(map("waypoints", "auto"), null));
    }
}
