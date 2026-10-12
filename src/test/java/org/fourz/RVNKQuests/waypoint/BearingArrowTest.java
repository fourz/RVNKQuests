package org.fourz.RVNKQuests.waypoint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The 8-sector arrow (#2264). Minecraft yaw: 0 south (+z), 90 west (-x), 180 north (-z), -90 east (+x).
 */
@DisplayName("Bearing arrow (#2264)")
class BearingArrowTest {

    @ParameterizedTest(name = "facing south, target dx={0} dz={1} -> {2}")
    @CsvSource({
        "0, 10, ↑",      // south: ahead
        "-10, 10, ↗",    // south-west: ahead-right (yaw grows clockwise, west is to the right when facing south)
        "-10, 0, →",     // west: right
        "-10, -10, ↘",
        "0, -10, ↓",     // north: behind
        "10, -10, ↙",
        "10, 0, ←",      // east: left
        "10, 10, ↖"
    })
    void eightSectorsFacingSouth(double dx, double dz, String arrow) {
        assertEquals(arrow, BearingArrow.arrow(0f, dx, dz));
    }

    @Test
    @DisplayName("the arrow turns with the player")
    void followsYaw() {
        // Target due north (-z).
        assertEquals("↑", BearingArrow.arrow(180f, 0, -10));   // facing north
        assertEquals("↑", BearingArrow.arrow(-180f, 0, -10));  // the same facing, other sign
        assertEquals("←", BearingArrow.arrow(-90f, 0, -10));   // facing east: north is to the left
        assertEquals("→", BearingArrow.arrow(90f, 0, -10));    // facing west: north is to the right
        assertEquals("↓", BearingArrow.arrow(0f, 0, -10));     // facing south
        // Yaw outside -180..180 is normalised.
        assertEquals("↑", BearingArrow.arrow(540f, 0, -10));
        assertEquals("↑", BearingArrow.arrow(-900f, 0, -10));
    }

    @Test
    @DisplayName("edge angles belong to the next sector clockwise")
    void edges() {
        // Target due south (bearing 0); relative angle = -yaw.
        assertEquals(1, BearingArrow.sector(-22.5f, 0, 10));   // exactly 22.5: ahead-right
        assertEquals(0, BearingArrow.sector(-22.4f, 0, 10));   // just inside ahead
        assertEquals(0, BearingArrow.sector(22.5f, 0, 10));    // exactly 337.5: ahead
        assertEquals(7, BearingArrow.sector(22.6f, 0, 10));    // just inside ahead-left
        assertEquals(2, BearingArrow.sector(-67.5f, 0, 10));   // exactly 67.5: right
        assertEquals(4, BearingArrow.sector(-157.5f, 0, 10));  // exactly 157.5: behind
        assertEquals(4, BearingArrow.sector(180f, 0, 10));     // exactly 180: behind
    }

    @Test
    @DisplayName("a target straight above or below shows the vertical arrow")
    void vertical() {
        assertEquals(-1, BearingArrow.sector(37f, 0.3, -0.4));
        assertEquals(BearingArrow.VERTICAL, BearingArrow.arrow(0f, 0, 0));
        assertEquals(BearingArrow.VERTICAL, BearingArrow.arrowFor(-1));
        assertEquals("↑", BearingArrow.arrowFor(0));
        assertEquals("↖", BearingArrow.arrowFor(7));
    }
}
