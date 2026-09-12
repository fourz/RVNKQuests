package org.fourz.RVNKQuests.trigger;

import org.fourz.RVNKQuests.trigger.generic.GenericWorldEventTrigger;
import org.junit.jupiter.api.*;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the WORLD_EVENT type and moon-phase mapping (#1017).
 *
 * <p>The phase table is the part of this feature that is easy to get quietly wrong: an off-by-one
 * means a quest fires on the wrong night and nobody notices until a player reports it, because
 * nothing errors. These tests pin the vanilla indices the specification named.</p>
 */
@DisplayName("WORLD_EVENT types and moon phases (#1017)")
class WorldEventTypeTest {

    @Nested
    @DisplayName("Type classification")
    class Types {

        @Test
        @DisplayName("the three timed types are polled")
        void timedTypesArePolled() {
            assertTrue(GenericWorldEventTrigger.Type.TIME_NIGHT.isPolled());
            assertTrue(GenericWorldEventTrigger.Type.TIME_DAY.isPolled());
            assertTrue(GenericWorldEventTrigger.Type.MOON_PHASE.isPolled());
        }

        @Test
        @DisplayName("the Bukkit-driven types are not polled")
        void bukkitTypesAreNotPolled() {
            // These arrive as WeatherChangeEvent and PlayerJoinEvent. If one of them ever reports
            // as polled, the scheduler would both listen for it and poll it — firing twice.
            assertFalse(GenericWorldEventTrigger.Type.STORM_START.isPolled());
            assertFalse(GenericWorldEventTrigger.Type.STORM_END.isPolled());
            assertFalse(GenericWorldEventTrigger.Type.PLAYER_JOIN.isPolled());
        }

        @Test
        @DisplayName("exactly the six Phase 1 types exist")
        void phaseOneTypes() {
            Set<String> names = new HashSet<>();
            for (GenericWorldEventTrigger.Type type : GenericWorldEventTrigger.Type.values()) {
                names.add(type.name());
            }
            assertEquals(Set.of("STORM_START", "STORM_END", "TIME_NIGHT", "TIME_DAY",
                    "PLAYER_JOIN", "MOON_PHASE"), names);
        }
    }

    @Nested
    @DisplayName("Moon phase aliases")
    class MoonPhases {

        @Test
        @DisplayName("FULL is phase 0 only")
        void fullIsZero() {
            assertTrue(GenericWorldEventTrigger.MoonPhase.FULL.matches(0));
            for (int i = 1; i < 8; i++) {
                assertFalse(GenericWorldEventTrigger.MoonPhase.FULL.matches(i), "phase " + i);
            }
        }

        @Test
        @DisplayName("CRESCENT is phases 1 and 7")
        void crescentIsOneAndSeven() {
            assertTrue(GenericWorldEventTrigger.MoonPhase.CRESCENT.matches(1));
            assertTrue(GenericWorldEventTrigger.MoonPhase.CRESCENT.matches(7));
            assertFalse(GenericWorldEventTrigger.MoonPhase.CRESCENT.matches(0));
            assertFalse(GenericWorldEventTrigger.MoonPhase.CRESCENT.matches(2));
        }

        @Test
        @DisplayName("QUARTER is phases 2 and 6")
        void quarterIsTwoAndSix() {
            assertTrue(GenericWorldEventTrigger.MoonPhase.QUARTER.matches(2));
            assertTrue(GenericWorldEventTrigger.MoonPhase.QUARTER.matches(6));
            assertFalse(GenericWorldEventTrigger.MoonPhase.QUARTER.matches(3));
            assertFalse(GenericWorldEventTrigger.MoonPhase.QUARTER.matches(5));
        }

        @Test
        @DisplayName("NEW is phase 4 only")
        void newIsFour() {
            assertTrue(GenericWorldEventTrigger.MoonPhase.NEW.matches(4));
            assertFalse(GenericWorldEventTrigger.MoonPhase.NEW.matches(3));
            assertFalse(GenericWorldEventTrigger.MoonPhase.NEW.matches(5));
        }

        @Test
        @DisplayName("phases 3 and 5 match no alias — deliberately")
        void threeAndFiveAreUnmapped() {
            // The specification named four aliases covering six of the eight indices. Widening
            // QUARTER to swallow 3 and 5 would make "fires on the quarter moon" mean "fires on
            // four of eight nights", so they are left unmatched on purpose.
            for (int unmapped : new int[]{3, 5}) {
                for (GenericWorldEventTrigger.MoonPhase phase : GenericWorldEventTrigger.MoonPhase.values()) {
                    assertFalse(phase.matches(unmapped),
                            phase + " must not claim unmapped phase " + unmapped);
                }
            }
        }

        @Test
        @DisplayName("the four aliases cover six distinct indices with no overlap")
        void aliasesDoNotOverlap() {
            Set<Integer> claimed = new HashSet<>();
            for (GenericWorldEventTrigger.MoonPhase phase : GenericWorldEventTrigger.MoonPhase.values()) {
                for (int i = 0; i < 8; i++) {
                    if (phase.matches(i)) {
                        assertTrue(claimed.add(i),
                                "phase index " + i + " is claimed by more than one alias");
                    }
                }
            }
            assertEquals(6, claimed.size());
        }
    }

    @Nested
    @DisplayName("Defaults")
    class Defaults {

        @Test
        @DisplayName("default priority is 10, so an authored priority always wins")
        void defaultPriority() {
            // Lower number wins, so the default must be high enough that any deliberate priority
            // outranks an un-prioritised sibling.
            assertEquals(10, GenericWorldEventTrigger.DEFAULT_PRIORITY);
        }
    }
}
