package org.fourz.RVNKQuests.objective.generic;

import org.junit.jupiter.api.*;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the encounter spawn offset honours {@code spawn_radius} (#1904).
 *
 * <p>The bug this locks down is quiet: offsetting each axis by {@code ±radius} independently
 * describes a square, whose corners reach {@code radius * sqrt(2)}. A configured
 * {@code spawn_radius: 8} could place a knight 11.3 blocks from the arena point, and nothing in
 * the config or the logs said so. A test that only sampled a handful of points would pass on the
 * old square too, so these assert the bound across many samples and check the corner explicitly.</p>
 */
@DisplayName("Encounter Spawn Offset Tests")
class EncounterSpawnOffsetTest {

    private static final double EPS = 1e-9;

    @Test
    @DisplayName("every sampled offset lies within the configured radius")
    void offsetsStayInsideRadius() {
        Random rng = new Random(20260809L);
        double radius = 8.0;

        for (int i = 0; i < 20_000; i++) {
            double[] o = GenericEncounterObjective.discOffset(rng, radius);
            double dist = Math.hypot(o[0], o[1]);
            assertTrue(dist <= radius + EPS,
                "offset " + dist + " exceeded spawn_radius " + radius);
        }
    }

    @Test
    @DisplayName("the old square sampling would have failed this bound")
    void squareSamplingWouldExceedRadius() {
        // The control. If this ever stops being true the test above proves nothing, because a
        // square of the same radius would slip through it.
        double radius = 8.0;
        double worstSquareCorner = Math.hypot(radius, radius);
        assertTrue(worstSquareCorner > radius,
            "a square's corner must exceed the radius, else the disc fix is untestable");
        assertEquals(11.31, worstSquareCorner, 0.01,
            "spawn_radius 8 reached 11.3 blocks under the old square sampling");
    }

    @Test
    @DisplayName("offsets actually spread across the disc rather than hugging the centre")
    void distributionIsNotCentreHeavy() {
        Random rng = new Random(4242L);
        double radius = 10.0;
        int outerHalf = 0;
        int samples = 20_000;

        for (int i = 0; i < samples; i++) {
            double[] o = GenericEncounterObjective.discOffset(rng, radius);
            if (Math.hypot(o[0], o[1]) > radius / 2.0) outerHalf++;
        }

        // Uniform over a disc puts 75% of points outside the half-radius circle (area scales with
        // r^2). Without the sqrt the figure collapses to ~50%, so this catches a regression to
        // naive polar sampling, not just to the square.
        double fraction = (double) outerHalf / samples;
        assertTrue(fraction > 0.70 && fraction < 0.80,
            "expected ~0.75 of samples beyond half radius for a uniform disc, got " + fraction);
    }

    @Test
    @DisplayName("a zero radius pins every mob to the spawn point")
    void zeroRadiusProducesNoOffset() {
        Random rng = new Random(1L);
        for (int i = 0; i < 100; i++) {
            double[] o = GenericEncounterObjective.discOffset(rng, 0.0);
            assertEquals(0.0, o[0], EPS);
            assertEquals(0.0, o[1], EPS);
        }
    }
}
