package org.fourz.RVNKQuests.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for per-beat advance feedback (#2025).
 *
 * <p>The behaviour under test is the success mirror of {@link OutOfOrderFeedback}: a beat that
 * fires can now say so. The single most important property is the <b>silent-unless-authored</b>
 * guarantee — a component with no {@code advance_message} must behave exactly as it did before
 * this class existed, because that is what allowed it to be wired into all twelve components at
 * once without changing the meaning of any shipped quest.</p>
 */
@DisplayName("AdvanceFeedback Tests")
class AdvanceFeedbackTest {

    private Player player;

    @BeforeEach
    void setUp() {
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getLocation()).thenReturn(mock(Location.class));
    }

    private static Map<String, Object> config(Object... pairs) {
        Map<String, Object> config = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            config.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return config;
    }

    @Nested
    @DisplayName("Silent unless authored")
    class SilentUnlessAuthored {

        @Test
        @DisplayName("no advance_message sends nothing at all")
        void absentMessageIsSilent() {
            AdvanceFeedback feedback = AdvanceFeedback.from(config("world", "alphac"));

            assertEquals(AdvanceFeedback.Outcome.SUPPRESSED_NO_MESSAGE,
                feedback.notifyAdvanced(player));
            assertFalse(feedback.isConfigured());
            verify(player, never()).sendMessage(anyString());
            verify(player, never()).playSound(any(Location.class), anyString(), anyFloat(), anyFloat());
        }

        @Test
        @DisplayName("a null config block is silent rather than throwing")
        void nullConfigIsSilent() {
            AdvanceFeedback feedback = AdvanceFeedback.from(null);

            assertEquals(AdvanceFeedback.Outcome.SUPPRESSED_NO_MESSAGE,
                feedback.notifyAdvanced(player));
            verify(player, never()).sendMessage(anyString());
        }

        @Test
        @DisplayName("a blank advance_message is treated as absent, not as an empty line")
        void blankMessageIsSilent() {
            AdvanceFeedback feedback = AdvanceFeedback.from(config("advance_message", "   "));

            assertEquals(AdvanceFeedback.Outcome.SUPPRESSED_NO_MESSAGE,
                feedback.notifyAdvanced(player));
            verify(player, never()).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("Sending")
    class Sending {

        @Test
        @DisplayName("an authored beat sends its line with & codes translated")
        void sendsTranslatedMessage() {
            AdvanceFeedback feedback = AdvanceFeedback.from(
                config("advance_message", "&7The bridge is behind you."));

            assertEquals(AdvanceFeedback.Outcome.SENT, feedback.notifyAdvanced(player));
            assertTrue(feedback.isConfigured());

            ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
            verify(player).sendMessage(sent.capture());
            assertEquals("§7The bridge is behind you.", sent.getValue());
        }

        @Test
        @DisplayName("a cue plays by default, because text alone is missed while walking")
        void playsDefaultSound() {
            AdvanceFeedback feedback = AdvanceFeedback.from(config("advance_message", "done"));

            feedback.notifyAdvanced(player);

            verify(player).playSound(any(Location.class),
                eq("minecraft:entity.experience_orb.pickup"), anyFloat(), anyFloat());
        }

        @Test
        @DisplayName("advance_sound: none gives text without a cue")
        void soundCanBeDisabled() {
            AdvanceFeedback feedback = AdvanceFeedback.from(
                config("advance_message", "done", "advance_sound", "none"));

            assertEquals(AdvanceFeedback.Outcome.SENT, feedback.notifyAdvanced(player));
            verify(player).sendMessage(anyString());
            verify(player, never()).playSound(any(Location.class), anyString(), anyFloat(), anyFloat());
        }

        @Test
        @DisplayName("a bad sound name still delivers the message the player was owed")
        void badSoundDoesNotSwallowTheMessage() {
            doThrow(new IllegalArgumentException("no such sound"))
                .when(player).playSound(any(Location.class), anyString(), anyFloat(), anyFloat());

            AdvanceFeedback feedback = AdvanceFeedback.from(
                config("advance_message", "done", "advance_sound", "minecraft:not.a.sound"));

            assertEquals(AdvanceFeedback.Outcome.SENT_WITHOUT_SOUND, feedback.notifyAdvanced(player));
            verify(player).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("Gates")
    class Gates {

        @Test
        @DisplayName("advance_feedback: false silences an authored beat")
        void masterSwitchWins() {
            AdvanceFeedback feedback = AdvanceFeedback.from(
                config("advance_message", "done", "advance_feedback", false));

            assertEquals(AdvanceFeedback.Outcome.SUPPRESSED_DISABLED, feedback.notifyAdvanced(player));
            assertFalse(feedback.isConfigured());
            verify(player, never()).sendMessage(anyString());
        }

        @Test
        @DisplayName("an offline player is reported, not thrown over")
        void nullPlayerIsReported() {
            AdvanceFeedback feedback = AdvanceFeedback.from(config("advance_message", "done"));

            assertEquals(AdvanceFeedback.Outcome.SUPPRESSED_NO_PLAYER, feedback.notifyAdvanced(null));
        }
    }

    @Test
    @DisplayName("configKeys covers every key read, so unknown-key validation stays quiet")
    void configKeysAreComplete() {
        assertEquals(
            java.util.Set.of("advance_feedback", "advance_message", "advance_sound",
                "advance_volume", "advance_pitch"),
            AdvanceFeedback.configKeys());
    }

    @Test
    @DisplayName("no throttle: an advance fires once per beat, unlike repeated failed interactions")
    void repeatedAdvancesAreNotThrottled() {
        AdvanceFeedback feedback = AdvanceFeedback.from(config("advance_message", "done"));

        assertEquals(AdvanceFeedback.Outcome.SENT, feedback.notifyAdvanced(player));
        assertEquals(AdvanceFeedback.Outcome.SENT, feedback.notifyAdvanced(player));

        verify(player, times(2)).sendMessage(anyString());
    }
    @Nested
    @DisplayName("Only on a committed advance (#1764, #2249)")
    class OnlyOnCommit {

        /** Records each task instead of running it, so the test can prove the hop happens. */
        private final java.util.List<Runnable> mainThreadTasks = new java.util.ArrayList<>();
        private final java.util.concurrent.Executor mainThread = mainThreadTasks::add;

        private void drainMainThread() {
            new java.util.ArrayList<>(mainThreadTasks).forEach(Runnable::run);
            mainThreadTasks.clear();
        }

        @Test
        @DisplayName("a committed advance sends the line, on the main thread")
        void committedSends() throws Exception {
            AdvanceFeedback feedback = AdvanceFeedback.from(
                config("advance_message", "You arrive.", "advance_sound", "none"));

            var outcome = feedback.notifyIfCommitted(player,
                java.util.concurrent.CompletableFuture.completedFuture(true), mainThread);

            verify(player, never()).sendMessage(anyString());
            drainMainThread();
            verify(player).sendMessage("You arrive.");
            assertEquals(AdvanceFeedback.Outcome.SENT, outcome.get());
        }

        @Test
        @DisplayName("a refused advance (prerequisite gate, already past) sends nothing")
        void refusedIsSilent() throws Exception {
            AdvanceFeedback feedback = AdvanceFeedback.from(config("advance_message", "You arrive."));

            var outcome = feedback.notifyIfCommitted(player,
                java.util.concurrent.CompletableFuture.completedFuture(false), mainThread);
            drainMainThread();

            verify(player, never()).sendMessage(anyString());
            assertEquals(AdvanceFeedback.Outcome.SUPPRESSED_NOT_COMMITTED, outcome.get());
        }

        @Test
        @DisplayName("a failed write sends nothing")
        void failedWriteIsSilent() throws Exception {
            AdvanceFeedback feedback = AdvanceFeedback.from(config("advance_message", "You arrive."));

            var outcome = feedback.notifyIfCommitted(player,
                java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("db down")),
                mainThread);
            drainMainThread();

            verify(player, never()).sendMessage(anyString());
            assertEquals(AdvanceFeedback.Outcome.SUPPRESSED_NOT_COMMITTED, outcome.get());
        }

        @Test
        @DisplayName("a missing result (a mock, an unwired caller) sends nothing")
        void nullResultIsSilent() throws Exception {
            AdvanceFeedback feedback = AdvanceFeedback.from(config("advance_message", "You arrive."));

            var outcome = feedback.notifyIfCommitted(player, null, mainThread);

            verify(player, never()).sendMessage(anyString());
            assertEquals(AdvanceFeedback.Outcome.SUPPRESSED_NOT_COMMITTED, outcome.get());
        }

        @Test
        @DisplayName("the line waits for the commit: nothing is sent while the write is in flight")
        void waitsForTheCommit() {
            AdvanceFeedback feedback = AdvanceFeedback.from(
                config("advance_message", "You arrive.", "advance_sound", "none"));
            var pending = new java.util.concurrent.CompletableFuture<Boolean>();

            feedback.notifyIfCommitted(player, pending, mainThread);
            drainMainThread();
            verify(player, never()).sendMessage(anyString());

            pending.complete(true);
            drainMainThread();
            verify(player).sendMessage("You arrive.");
        }

        @Test
        @DisplayName("a component with no line attaches nothing and schedules nothing")
        void unconfiguredSchedulesNothing() throws Exception {
            AdvanceFeedback feedback = AdvanceFeedback.from(config("world", "alphac"));

            var outcome = feedback.notifyIfCommitted(player,
                java.util.concurrent.CompletableFuture.completedFuture(true), mainThread);

            assertTrue(mainThreadTasks.isEmpty());
            assertEquals(AdvanceFeedback.Outcome.SUPPRESSED_NO_MESSAGE, outcome.get());
        }
    }
}
