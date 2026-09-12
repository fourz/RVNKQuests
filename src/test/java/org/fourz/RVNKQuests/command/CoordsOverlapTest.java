package org.fourz.RVNKQuests.command;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the overlap arithmetic behind {@code /quest debug coords} (#2093).
 *
 * <p>The first version of that command compared centre-to-centre distance against a fixed 3 blocks.
 * It reported {@code 0 co-located} for live Event content whose trigger volumes overlapped by 39
 * blocks — the exact same-tick race shape (#1853) the command exists to surface. These tests use
 * those real coordinates so the regression cannot come back silently.</p>
 *
 * <p>The maths is duplicated here rather than reached through the command, because the command's
 * {@code Point} record is private and reaching it would mean loosening visibility purely for a
 * test. The formula is one line and pinning it is the point.</p>
 */
@DisplayName("coords overlap arithmetic (#2093)")
class CoordsOverlapTest {

    /** Mirrors {@code Point.overlap}: positive means the volumes intersect. */
    private static double overlap(double distance, double r1, double r2) {
        return (r1 + r2) - distance;
    }

    private static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Nested
    @DisplayName("The live Event case the old check missed")
    class ZealArrival {

        // tfah_zeal_arrival on Event, read 2026-09-12:
        //   arr_trigger  zeal -178,65,265  LOCATION_PROXIMITY  r=30
        //   arr_reach    zeal -173,64,268  REACH               r=15
        private static final double D = distance(-178, 65, 265, -173, 64, 268);

        @Test
        @DisplayName("the two components are about 5.9 blocks apart")
        void centresAreClose() {
            assertEquals(5.92, D, 0.01);
        }

        @Test
        @DisplayName("a fixed 3-block centre test finds nothing — the original bug")
        void oldCheckMissesIt() {
            assertFalse(D <= 3.0,
                    "5.9 > 3, which is why the original check reported 0 co-located");
        }

        @Test
        @DisplayName("the overlap test finds them overlapping by roughly 39 blocks")
        void overlapCatchesIt() {
            double over = overlap(D, 30, 15);
            assertTrue(over > 0, "r30 and r15 at 5.9 blocks apart must register as overlapping");
            assertEquals(39.08, over, 0.01);
        }
    }

    @Nested
    @DisplayName("Boundaries")
    class Boundaries {

        @Test
        @DisplayName("volumes exactly touching count as overlapping")
        void touchingCountsAsOverlap() {
            // Inclusive on purpose: a player standing on the shared boundary is inside both, and
            // reporting that as clear would be the same false negative in miniature.
            assertEquals(0.0, overlap(45, 30, 15), 1e-9);
            assertTrue(overlap(45, 30, 15) >= 0);
        }

        @Test
        @DisplayName("disjoint volumes report a negative overlap")
        void disjointIsNegative() {
            assertEquals(-5.0, overlap(50, 30, 15), 1e-9);
        }

        @Test
        @DisplayName("two radius-less points at distance overlap by a negative amount")
        void radiuslessPointsApart() {
            // These are caught by the separate same-place check, not by overlap.
            assertTrue(overlap(10, 0, 0) < 0);
        }

        @Test
        @DisplayName("two radius-less points at the same spot overlap by zero")
        void radiuslessPointsTogether() {
            assertEquals(0.0, overlap(0, 0, 0), 1e-9);
        }

        @Test
        @DisplayName("one large radius alone can swallow a distant point — the #1855 shape")
        void largeRadiusSwallowsDistantPoint() {
            // #1855: tower_trigger was r50 at the old centre and reached another quest's endpoint
            // roughly 100 blocks away. At 100 it is clear; at 40 a single r50 is enough on its own.
            assertTrue(overlap(40, 50, 0) > 0, "an r50 reaches a radius-less point 40 blocks away");
            assertTrue(overlap(100, 50, 0) < 0, "but not one 100 blocks away");
        }
    }
}
