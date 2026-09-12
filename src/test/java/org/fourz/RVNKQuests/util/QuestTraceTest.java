package org.fourz.RVNKQuests.util;

import org.fourz.RVNKQuests.quest.QuestState;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the quest trace sink (#2093).
 *
 * <p>Two properties matter. First, the hot path must be free when nobody is tracing — these hooks
 * sit inside the write chain every state change passes through. Second, a broken sink must not be
 * able to break the state machine observing it.</p>
 */
@DisplayName("QuestTrace (#2093)")
class QuestTraceTest {

    private UUID player;
    private UUID other;

    @BeforeEach
    void setUp() {
        QuestTrace.clear();
        player = UUID.randomUUID();
        other = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        QuestTrace.clear();
    }

    private static QuestTrace.Event event(QuestTrace.Decision decision) {
        return new QuestTrace.Event(UUID.randomUUID(), "q", QuestState.NOT_STARTED,
                QuestState.TRIGGER_FOUND, decision, "detail", null);
    }

    @Nested
    @DisplayName("Activity gating")
    class Gating {

        @Test
        @DisplayName("inactive with no subscribers")
        void inactiveByDefault() {
            assertFalse(QuestTrace.isActive());
            assertFalse(QuestTrace.isTracing(player));
        }

        @Test
        @DisplayName("active once a player is subscribed")
        void activeAfterSubscribe() {
            QuestTrace.subscribe(player, e -> { });
            assertTrue(QuestTrace.isActive());
            assertTrue(QuestTrace.isTracing(player));
            assertFalse(QuestTrace.isTracing(other), "an untraced player must not report as traced");
        }

        @Test
        @DisplayName("inactive again after the last unsubscribe")
        void inactiveAfterLastUnsubscribe() {
            QuestTrace.subscribe(player, e -> { });
            QuestTrace.subscribe(other, e -> { });
            assertTrue(QuestTrace.isActive());

            assertTrue(QuestTrace.unsubscribe(player));
            assertTrue(QuestTrace.isActive(), "one trace remains");

            assertTrue(QuestTrace.unsubscribe(other));
            assertFalse(QuestTrace.isActive());
        }

        @Test
        @DisplayName("unsubscribe reports whether a trace was running")
        void unsubscribeReportsPresence() {
            assertFalse(QuestTrace.unsubscribe(player));
            QuestTrace.subscribe(player, e -> { });
            assertTrue(QuestTrace.unsubscribe(player));
        }
    }

    @Nested
    @DisplayName("Delivery")
    class Delivery {

        @Test
        @DisplayName("emits only to the traced player")
        void emitsOnlyToTracedPlayer() {
            List<QuestTrace.Event> received = new ArrayList<>();
            QuestTrace.subscribe(player, received::add);

            QuestTrace.emit(player, "q1", QuestState.NOT_STARTED, QuestState.TRIGGER_FOUND,
                    QuestTrace.Decision.ADVANCED, null, null);
            QuestTrace.emit(other, "q1", QuestState.NOT_STARTED, QuestState.TRIGGER_FOUND,
                    QuestTrace.Decision.ADVANCED, null, null);

            assertEquals(1, received.size(), "the untraced player's decision must not be delivered");
            assertEquals("q1", received.get(0).questId());
        }

        @Test
        @DisplayName("emit is a no-op while nothing is traced")
        void emitNoOpWhenInactive() {
            // No subscriber, so nothing to assert on except that this does not throw and stays
            // inactive — the hot-path guarantee.
            assertDoesNotThrow(() -> QuestTrace.emit(player, "q", QuestState.NOT_STARTED,
                    QuestState.TRIGGER_FOUND, QuestTrace.Decision.ALREADY, null, null));
            assertFalse(QuestTrace.isActive());
        }

        @Test
        @DisplayName("re-subscribing replaces the sink rather than adding a second")
        void resubscribeReplaces() {
            List<QuestTrace.Event> first = new ArrayList<>();
            List<QuestTrace.Event> second = new ArrayList<>();
            QuestTrace.subscribe(player, first::add);
            QuestTrace.subscribe(player, second::add);

            QuestTrace.emit(player, "q", QuestState.NOT_STARTED, QuestState.TRIGGER_FOUND,
                    QuestTrace.Decision.ADVANCED, null, null);

            assertTrue(first.isEmpty(), "the replaced sink must stop receiving");
            assertEquals(1, second.size());
        }

        @Test
        @DisplayName("a throwing sink cannot propagate into the state machine")
        void throwingSinkIsSwallowed() {
            QuestTrace.subscribe(player, e -> {
                throw new IllegalStateException("sink is broken");
            });

            // This is the whole point of the try/catch in emit: observing must not perturb. If this
            // throws, a bad debug sink takes out a quest state write.
            assertDoesNotThrow(() -> QuestTrace.emit(player, "q", QuestState.NOT_STARTED,
                    QuestState.TRIGGER_FOUND, QuestTrace.Decision.ADVANCED, null, null));
        }

        @Test
        @DisplayName("clear drops every sink")
        void clearDropsAll() {
            QuestTrace.subscribe(player, e -> { });
            QuestTrace.subscribe(other, e -> { });
            QuestTrace.clear();
            assertFalse(QuestTrace.isActive());
            assertTrue(QuestTrace.traced().isEmpty());
        }
    }

    @Nested
    @DisplayName("Rendering")
    class Rendering {

        @Test
        @DisplayName("every decision renders its own label")
        void everyDecisionHasALabel() {
            for (QuestTrace.Decision decision : QuestTrace.Decision.values()) {
                String rendered = event(decision).render();
                assertTrue(rendered.contains(decision.label()),
                        decision + " must name itself in the rendered line");
            }
        }

        @Test
        @DisplayName("renders the state edge")
        void rendersStateEdge() {
            String rendered = new QuestTrace.Event(player, "tfah_ch1", QuestState.QUEST_ACTIVE,
                    QuestState.OBJECTIVE_FOUND, QuestTrace.Decision.ADVANCED, null, null).render();
            assertTrue(rendered.contains("tfah_ch1"));
            assertTrue(rendered.contains("QUEST_ACTIVE"));
            assertTrue(rendered.contains("OBJECTIVE_FOUND"));
        }

        @Test
        @DisplayName("includes the checkpoint when one was carried")
        void includesCheckpoint() {
            String rendered = new QuestTrace.Event(player, "q", QuestState.NOT_STARTED,
                    QuestState.TRIGGER_FOUND, QuestTrace.Decision.ADVANCED, null,
                    "alphac -316,118,447").render();
            assertTrue(rendered.contains("alphac -316,118,447"),
                    "coordinates are the only component attribution available at this layer");
        }

        @Test
        @DisplayName("omits an absent checkpoint and detail without rendering null")
        void omitsNulls() {
            String rendered = new QuestTrace.Event(player, "q", QuestState.NOT_STARTED,
                    QuestState.TRIGGER_FOUND, QuestTrace.Decision.ALREADY, null, null).render();
            assertFalse(rendered.contains("null"), "a null field must not reach the operator");
        }

        @Test
        @DisplayName("blank detail is treated as absent")
        void blankDetailOmitted() {
            String rendered = new QuestTrace.Event(player, "q", QuestState.NOT_STARTED,
                    QuestState.TRIGGER_FOUND, QuestTrace.Decision.ALREADY, "   ", null).render();
            assertFalse(rendered.contains("()"), "an empty bracket pair is noise");
        }
    }
}
