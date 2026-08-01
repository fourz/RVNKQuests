package org.fourz.RVNKQuests.quest;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QuestState enum integrity and ordering.
 * Validates the state machine contract used by all quest implementations.
 */
@DisplayName("QuestState State Machine Tests")
class QuestStateTest {

    @Test
    @DisplayName("All expected states are defined")
    void allStatesExist() {
        Set<String> names = new HashSet<>();
        for (QuestState s : QuestState.values()) {
            names.add(s.name());
        }
        assertTrue(names.contains("NOT_STARTED"), "NOT_STARTED state must exist");
        assertTrue(names.contains("TRIGGER_FOUND"), "TRIGGER_FOUND state must exist");
        assertTrue(names.contains("QUEST_ACTIVE"), "QUEST_ACTIVE state must exist");
        assertTrue(names.contains("OBJECTIVE_FOUND"), "OBJECTIVE_FOUND state must exist");
        assertTrue(names.contains("COMPLETED"), "COMPLETED state must exist");
    }

    @Test
    @DisplayName("State ordinal ordering matches progression")
    void stateOrdinalOrdering() {
        assertTrue(QuestState.NOT_STARTED.ordinal() < QuestState.TRIGGER_FOUND.ordinal(),
            "NOT_STARTED must come before TRIGGER_FOUND");
        assertTrue(QuestState.TRIGGER_FOUND.ordinal() < QuestState.QUEST_ACTIVE.ordinal(),
            "TRIGGER_FOUND must come before QUEST_ACTIVE");
        assertTrue(QuestState.QUEST_ACTIVE.ordinal() < QuestState.OBJECTIVE_FOUND.ordinal(),
            "QUEST_ACTIVE must come before OBJECTIVE_FOUND");
        assertTrue(QuestState.OBJECTIVE_FOUND.ordinal() < QuestState.COMPLETED.ordinal(),
            "OBJECTIVE_FOUND must come before COMPLETED");
    }

    @Test
    @DisplayName("COMPLETED is not equal to NOT_STARTED")
    void completedIsNotNotStarted() {
        assertNotEquals(QuestState.COMPLETED, QuestState.NOT_STARTED);
    }

    @Test
    @DisplayName("valueOf round-trips correctly for all states")
    void valueOfRoundTrip() {
        for (QuestState state : QuestState.values()) {
            assertEquals(state, QuestState.valueOf(state.name()));
        }
    }

    @Test
    @DisplayName("States can be used as switch keys without fall-through gap")
    void statesEnumeratedInSwitch() {
        // Verify that a switch covering all states leaves no gaps — any new state
        // would cause this test to fail (missing case).
        int coveredStates = 0;
        for (QuestState state : QuestState.values()) {
            switch (state) {
                case NOT_STARTED:
                case TRIGGER_FOUND:
                case QUEST_ACTIVE:
                case OBJECTIVE_FOUND:
                case COMPLETED:
                case ABANDONED:
                case PAUSED:
                    coveredStates++;
                    break;
                default:
                    fail("Unhandled QuestState in switch: " + state);
            }
        }
        assertEquals(QuestState.values().length, coveredStates,
            "Switch must cover all QuestState values");
    }
}
