package org.fourz.RVNKQuests.trigger.generic;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.party.PartyBeatContext;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * #1764 as found in #2244: LOCATION_PROXIMITY sent its {@code advance_message} before the advance
 * committed, so a player blocked by the prerequisite gate saw the arrival line on every step.
 *
 * <p>The quest is a mock, so its {@code tryAdvanceStateForPlayer} result stands for what the
 * state machine decided. {@code AdvanceCommitResultTest} proves the state machine reports that
 * result truthfully for each gate.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LOCATION_PROXIMITY advance_message only on a committed advance (#1764, #2249)")
class LocationAdvanceMessageTest {

    private static final String LINE = "The cavern mouth breathes cold air.";

    @Mock private RVNKQuests plugin;
    @Mock private DataDrivenQuest quest;
    @Mock private Player player;
    @Mock private World world;
    @Mock private Server server;
    @Mock private BukkitScheduler scheduler;

    private Server previousServer;
    private UUID playerId;

    @BeforeEach
    void setUp() throws Exception {
        playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Tester");
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(world.getName()).thenReturn("sotw_city");
        when(quest.getId()).thenReturn("sotw_cavern");
        when(quest.getStateForPlayer(any(Player.class))).thenReturn(QuestState.NOT_STARTED);

        // AdvanceFeedback.mainThread(plugin) schedules through Bukkit; run those tasks inline.
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(any(org.bukkit.plugin.Plugin.class), any(Runnable.class)))
            .thenAnswer(inv -> {
                inv.getArgument(1, Runnable.class).run();
                return null;
            });
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = (Server) serverField.get(null);
        serverField.set(null, server);
    }

    @AfterEach
    void restoreServer() throws Exception {
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, previousServer);
    }

    private GenericLocationProximityTrigger trigger() {
        Map<String, Object> config = new HashMap<>();
        config.put("type", "LOCATION_PROXIMITY");
        config.put("world", "sotw_city");
        config.put("x", 8);
        config.put("y", 63);
        config.put("z", 10);
        config.put("radius", 5);
        config.put("required_state", "NOT_STARTED");
        config.put("advance_state", "TRIGGER_FOUND");
        config.put("advance_message", LINE);
        config.put("advance_sound", "none");
        return new GenericLocationProximityTrigger(plugin, quest, config);
    }

    private void arrive(GenericLocationProximityTrigger trigger) {
        Location from = new Location(world, 100, 63, 100);
        Location to = new Location(world, 8, 63, 10);
        trigger.onPlayerTeleport(new PlayerTeleportEvent(player, from, to));
    }

    private void advanceResult(boolean committed) {
        when(quest.tryAdvanceStateForPlayer(any(UUID.class), any(QuestState.class), any(PartyBeatContext.class)))
            .thenReturn(CompletableFuture.completedFuture(committed));
    }

    @Test
    @DisplayName("a committed advance sends the arrival line once")
    void committedAdvanceSpeaks() {
        advanceResult(true);

        arrive(trigger());

        verify(quest).tryAdvanceStateForPlayer(eq(playerId), eq(QuestState.TRIGGER_FOUND), any(PartyBeatContext.class));
        verify(player, times(1)).sendMessage(LINE);
    }

    @Test
    @DisplayName("prerequisite-blocked: the advance is refused and no line is sent, on any number of steps")
    void prerequisiteBlockedIsSilent() {
        advanceResult(false); // the prerequisite gate refuses NOT_STARTED -> TRIGGER_FOUND
        GenericLocationProximityTrigger trigger = trigger();

        // The state stays NOT_STARTED, so the trigger re-fires on every arrival. Before the fix
        // each one sent the line.
        arrive(trigger);
        arrive(trigger);
        arrive(trigger);

        verify(quest, times(3)).tryAdvanceStateForPlayer(any(UUID.class), any(QuestState.class), any(PartyBeatContext.class));
        verify(player, never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("state already past the trigger: no advance attempt, no line")
    void alreadyPastIsSilent() {
        advanceResult(true);
        when(quest.getStateForPlayer(any(Player.class))).thenReturn(QuestState.QUEST_ACTIVE);

        arrive(trigger());

        verify(quest, never()).tryAdvanceStateForPlayer(any(UUID.class), any(QuestState.class), any(PartyBeatContext.class));
        verify(quest, never()).advanceStateForPlayer(any(UUID.class), any(QuestState.class), any(PartyBeatContext.class));
        verify(player, never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("a same-tick race lost to another component (refused as not forward) sends no line")
    void lostRaceIsSilent() {
        // The component's cached gate passed, then the write chain found the state already moved.
        advanceResult(false);

        arrive(trigger());

        verify(player, never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("a failed state write sends no line")
    void failedWriteIsSilent() {
        when(quest.tryAdvanceStateForPlayer(any(UUID.class), any(QuestState.class), any(PartyBeatContext.class)))
            .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("db down")));

        arrive(trigger());

        verify(player, never()).sendMessage(anyString());
    }
}
