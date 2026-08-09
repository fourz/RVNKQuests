package org.fourz.RVNKQuests.util;

import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.quest.QuestState;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for out-of-order beat feedback.
 *
 * <p>The behaviour under test is what a player is told when a deliberate interaction lands on a
 * quest beat they have not reached — the gap that made state-gated triggers read as broken.</p>
 */
@DisplayName("OutOfOrderFeedback Tests")
class OutOfOrderFeedbackTest {

    private Player player;

    @BeforeEach
    void setUp() {
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    private static Map<String, Object> config(Object... pairs) {
        Map<String, Object> config = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            config.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return config;
    }

    private String captureMessage() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(player).sendMessage(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Speaks when the player has not reached the beat yet")
    void tooEarlySpeaks() {
        OutOfOrderFeedback feedback = OutOfOrderFeedback.from(
            config("out_of_order_message", "&7too early"));

        feedback.notifyWrongBeat(player, QuestState.TRIGGER_FOUND, QuestState.OBJECTIVE_FOUND);

        assertEquals("§7too early", captureMessage(),
            "Colour codes must be translated before the message reaches the player");
    }

    @Test
    @DisplayName("Speaks a different line when the player is already past the beat")
    void alreadyPastSpeaks() {
        OutOfOrderFeedback feedback = OutOfOrderFeedback.from(
            config("already_past_message", "&7already done"));

        feedback.notifyWrongBeat(player, QuestState.COMPLETED, QuestState.QUEST_ACTIVE);

        assertEquals("§7already done", captureMessage());
    }

    @Test
    @DisplayName("Says nothing to a player who has not discovered the quest")
    void notStartedIsSilent() {
        // The load-bearing case: a message here would announce hidden content to anyone
        // right-clicking their way around the map.
        OutOfOrderFeedback feedback = OutOfOrderFeedback.from(config());

        feedback.notifyWrongBeat(player, QuestState.NOT_STARTED, QuestState.QUEST_ACTIVE);

        verify(player, never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("Says nothing for abandoned or admin-paused quests")
    void offTrackStatesAreSilent() {
        OutOfOrderFeedback feedback = OutOfOrderFeedback.from(config());

        feedback.notifyWrongBeat(player, QuestState.ABANDONED, QuestState.QUEST_ACTIVE);
        feedback.notifyWrongBeat(player, QuestState.PAUSED, QuestState.QUEST_ACTIVE);

        verify(player, never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("Says nothing when the player is on the right beat")
    void matchingStateIsSilent() {
        OutOfOrderFeedback feedback = OutOfOrderFeedback.from(config());

        feedback.notifyWrongBeat(player, QuestState.QUEST_ACTIVE, QuestState.QUEST_ACTIVE);

        verify(player, never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("Throttles repeat messages within the cooldown")
    void repeatsAreThrottled() {
        // Held right-click fires PlayerInteractEvent repeatedly; without the throttle this becomes
        // a wall of identical lines.
        OutOfOrderFeedback feedback = OutOfOrderFeedback.from(
            config("out_of_order_cooldown_ms", 60_000));

        for (int i = 0; i < 5; i++) {
            feedback.notifyWrongBeat(player, QuestState.TRIGGER_FOUND, QuestState.COMPLETED);
        }

        verify(player, times(1)).sendMessage(anyString());
    }

    @Test
    @DisplayName("Throttle is per player, not global")
    void throttleIsPerPlayer() {
        OutOfOrderFeedback feedback = OutOfOrderFeedback.from(
            config("out_of_order_cooldown_ms", 60_000));
        Player other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());

        feedback.notifyWrongBeat(player, QuestState.TRIGGER_FOUND, QuestState.COMPLETED);
        feedback.notifyWrongBeat(other, QuestState.TRIGGER_FOUND, QuestState.COMPLETED);

        verify(player, times(1)).sendMessage(anyString());
        verify(other, times(1)).sendMessage(anyString());
    }

    @Test
    @DisplayName("Disabled feedback stays silent")
    void disabledStaysSilent() {
        OutOfOrderFeedback feedback = OutOfOrderFeedback.from(
            config("out_of_order_feedback", false));

        feedback.notifyWrongBeat(player, QuestState.TRIGGER_FOUND, QuestState.COMPLETED);

        verify(player, never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("A blank message suppresses that direction only")
    void blankMessageSuppresses() {
        OutOfOrderFeedback feedback = OutOfOrderFeedback.from(
            config("out_of_order_message", "", "already_past_message", "&7past"));

        feedback.notifyWrongBeat(player, QuestState.TRIGGER_FOUND, QuestState.COMPLETED);
        verify(player, never()).sendMessage(anyString());

        feedback.notifyWrongBeat(player, QuestState.COMPLETED, QuestState.TRIGGER_FOUND);
        assertEquals("§7past", captureMessage());
    }

    @Test
    @DisplayName("Null config and null arguments are tolerated")
    void nullsAreTolerated() {
        OutOfOrderFeedback feedback = OutOfOrderFeedback.from(null);

        assertDoesNotThrow(() -> {
            feedback.notifyWrongBeat(null, QuestState.TRIGGER_FOUND, QuestState.COMPLETED);
            feedback.notifyWrongBeat(player, null, QuestState.COMPLETED);
            feedback.notifyWrongBeat(player, QuestState.TRIGGER_FOUND, null);
        });
        verify(player, never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("Components can whitelist the config keys this helper consumes")
    void configKeysAreDeclared() {
        // GenericStructureInteractTrigger warns about unknown keys; if these were missing from the
        // declared set, every quest using the feature would log a spurious warning.
        assertTrue(OutOfOrderFeedback.configKeys().containsAll(java.util.Set.of(
            "out_of_order_feedback", "out_of_order_message",
            "already_past_message", "out_of_order_cooldown_ms")));
    }

    // ---- #1930: the returned Outcome is the only server-side evidence of what happened ----

    @Test
    @DisplayName("outcome names each decision, including the silences")
    void outcomeNamesEachDecision() {
        OutOfOrderFeedback feedback = OutOfOrderFeedback.from(config());
        // The whole point: a silence must be distinguishable from "nothing was clicked".
        assertEquals(OutOfOrderFeedback.Outcome.SUPPRESSED_NOT_STARTED,
            feedback.notifyWrongBeat(player, QuestState.NOT_STARTED, QuestState.QUEST_ACTIVE),
            "an undiscovered quest must report the leak guard, not just stay quiet");

        assertEquals(OutOfOrderFeedback.Outcome.SUPPRESSED_OFF_TRACK,
            feedback.notifyWrongBeat(player, QuestState.ABANDONED, QuestState.QUEST_ACTIVE));
        assertEquals(OutOfOrderFeedback.Outcome.SUPPRESSED_OFF_TRACK,
            feedback.notifyWrongBeat(player, QuestState.PAUSED, QuestState.QUEST_ACTIVE));

        assertEquals(OutOfOrderFeedback.Outcome.SUPPRESSED_SAME_STATE,
            feedback.notifyWrongBeat(player, QuestState.QUEST_ACTIVE, QuestState.QUEST_ACTIVE));

        assertEquals(OutOfOrderFeedback.Outcome.SUPPRESSED_DISABLED,
            feedback.notifyWrongBeat(null, QuestState.TRIGGER_FOUND, QuestState.COMPLETED));
    }

    @Test
    @DisplayName("outcome distinguishes the two directions and the throttle")
    void outcomeDistinguishesDirectionsAndThrottle() {
        OutOfOrderFeedback feedback = OutOfOrderFeedback.from(config());
        assertEquals(OutOfOrderFeedback.Outcome.SENT_TOO_EARLY,
            feedback.notifyWrongBeat(player, QuestState.TRIGGER_FOUND, QuestState.COMPLETED));

        // Immediately again: same player, inside the cooldown.
        assertEquals(OutOfOrderFeedback.Outcome.SUPPRESSED_THROTTLED,
            feedback.notifyWrongBeat(player, QuestState.TRIGGER_FOUND, QuestState.COMPLETED),
            "a held right-click must report the throttle rather than looking like a fresh send");

        Player fresh = mock(Player.class);
        when(fresh.getUniqueId()).thenReturn(UUID.randomUUID());
        assertEquals(OutOfOrderFeedback.Outcome.SENT_ALREADY_PAST,
            feedback.notifyWrongBeat(fresh, QuestState.COMPLETED, QuestState.TRIGGER_FOUND));
    }

    @Test
    @DisplayName("a blank configured message reports itself rather than passing as sent")
    void blankMessageReportsSuppression() {
        OutOfOrderFeedback blank = OutOfOrderFeedback.from(config("out_of_order_message", ""));

        assertEquals(OutOfOrderFeedback.Outcome.SUPPRESSED_BLANK_MESSAGE,
            blank.notifyWrongBeat(player, QuestState.TRIGGER_FOUND, QuestState.COMPLETED));
        verify(player, never()).sendMessage(anyString());
    }
}
