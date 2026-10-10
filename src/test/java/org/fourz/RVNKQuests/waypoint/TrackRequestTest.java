package org.fourz.RVNKQuests.waypoint;

import org.fourz.RVNKQuests.waypoint.TrackRequest.Action;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** {@code /quest track} grammar and the prefs toggle words (#2264). */
@DisplayName("/quest track parsing and the waypoints pref (#2264)")
class TrackRequestTest {

    private static TrackRequest p(String... args) {
        return TrackRequest.parse(args);
    }

    @Test
    @DisplayName("no args shows; off stops; an id tracks")
    void actions() {
        assertEquals(Action.SHOW, p().action());
        assertEquals(Action.SHOW, TrackRequest.parse(null).action());
        assertEquals(Action.OFF, p("off").action());
        assertEquals(Action.OFF, p("OFF").action());
        TrackRequest t = p("tfah_worldforge_aether");
        assertEquals(Action.TRACK, t.action());
        assertEquals("tfah_worldforge_aether", t.questId());
        assertFalse(t.trail());
        assertNull(t.player());
        assertFalse(t.isError());
    }

    @Test
    @DisplayName("--trail anywhere, any case; a second token is the player")
    void trailAndPlayer() {
        TrackRequest a = p("q1", "--trail");
        assertTrue(a.trail());
        assertEquals("q1", a.questId());
        TrackRequest b = p("--TRAIL", "q1", "Shad0melt");
        assertTrue(b.trail());
        assertEquals("q1", b.questId());
        assertEquals("Shad0melt", b.player());
        TrackRequest c = p("off", "wizardofire");
        assertEquals(Action.OFF, c.action());
        assertEquals("wizardofire", c.player());
    }

    @Test
    @DisplayName("bad input is an error, never a guess")
    void errors() {
        assertTrue(p("--trail").isError());
        assertTrue(p("off", "--trail").isError());
        assertTrue(p("q1", "--fast").isError());
        assertTrue(p("q1", "a", "b").isError());
    }

    @Test
    @DisplayName("prefs toggle words")
    void prefsToggle() {
        assertEquals(Boolean.TRUE, WaypointPrefs.parseToggle("on"));
        assertEquals(Boolean.TRUE, WaypointPrefs.parseToggle("ON"));
        assertEquals(Boolean.TRUE, WaypointPrefs.parseToggle("true"));
        assertEquals(Boolean.FALSE, WaypointPrefs.parseToggle("off"));
        assertEquals(Boolean.FALSE, WaypointPrefs.parseToggle("disable"));
        assertNull(WaypointPrefs.parseToggle("maybe"));
        assertNull(WaypointPrefs.parseToggle(null));
    }

    @Test
    @DisplayName("stored prefs: waypoints default on; tracked quest read back")
    void storedPrefs() {
        assertTrue(WaypointPrefs.enabledFrom(null));
        assertTrue(WaypointPrefs.enabledFrom(java.util.Map.of()));
        assertTrue(WaypointPrefs.enabledFrom(java.util.Map.of(WaypointPrefs.KEY_ENABLED, "true")));
        assertFalse(WaypointPrefs.enabledFrom(java.util.Map.of(WaypointPrefs.KEY_ENABLED, "false")));
        assertNull(WaypointPrefs.trackedFrom(java.util.Map.of()));
        assertNull(WaypointPrefs.trackedFrom(java.util.Map.of(WaypointPrefs.KEY_TRACKED, " ")));
        assertEquals("q1", WaypointPrefs.trackedFrom(java.util.Map.of(WaypointPrefs.KEY_TRACKED, "q1")));
    }
}
