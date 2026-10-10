package org.fourz.RVNKQuests.waypoint;

import com.google.gson.JsonParser;
import org.fourz.rvnkcore.util.log.LogManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The cross-world line names the world by its RVNKWorlds display name, or by its name (#2264). */
@DisplayName("World display names (#2264)")
class WorldDisplayNamesTest {

    @Test
    @DisplayName("reads name/displayName from the /v1/worlds payload, keyed without case")
    void parsesPayload() {
        Map<String, String> m = WorldDisplayNames.parse(JsonParser.parseString("""
            [{"name":"sotw_sky_0","displayName":"The Aether","state":"ACTIVE"},
             {"name":"SOTW_City","displayName":"Secrets of the Worlds"},
             {"name":"blank","displayName":""},
             {"name":"no_display"},
             "junk"]"""));
        assertEquals(Map.of("sotw_sky_0", "The Aether", "sotw_city", "Secrets of the Worlds"), m);
        assertTrue(WorldDisplayNames.parse(JsonParser.parseString("{}")).isEmpty());
        assertTrue(WorldDisplayNames.parse(null).isEmpty());
    }

    @Test
    @DisplayName("without RVNKWorlds the world name is the display name")
    void fallsBackToWorldName() {
        WorldDisplayNames names = new WorldDisplayNames(mock(LogManager.class));
        assertEquals("sotw_sky_0", names.displayName("sotw_sky_0"));
        assertEquals("", names.displayName(null));
    }
}
