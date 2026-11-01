package org.fourz.RVNKQuests.trigger.generic;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.objective.generic.GenericReachObjective;
import org.fourz.RVNKQuests.party.PartyBeatContext;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.util.SiteResolver;
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
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

/**
 * #1938: a LOCATION_PROXIMITY trigger whose advance is refused (unmet prerequisites) no longer re-fires on every move:
 * once on entering the radius, then at most every {@link GenericLocationProximityTrigger#REARM_MS} while the player
 * stays; leaving re-arms it at once.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LOCATION_PROXIMITY: a refused advance is not retried on every move (#1938)")
class ProximityRefireTest {

    @Mock private RVNKQuests plugin;
    @Mock private DataDrivenQuest quest;
    @Mock private Player player;
    @Mock private World world;
    @Mock private Server server;
    @Mock private BukkitScheduler scheduler;

    private Server previousServer;
    private UUID playerId;
    private final long[] now = {1_000_000L};

    @BeforeEach
    void setUp() throws Exception {
        playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Tester");
        when(player.getServer()).thenReturn(server);
        when(world.getName()).thenReturn("alphac");
        when(server.getWorld("alphac")).thenReturn(world);
        when(quest.getId()).thenReturn("tfah_zeal_sanctum");
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(any(org.bukkit.plugin.Plugin.class), any(Runnable.class)))
            .thenAnswer(inv -> { inv.getArgument(1, Runnable.class).run(); return null; });
        // Refused: prerequisites not met, the state stays NOT_STARTED
        when(quest.getStateForPlayer(any(Player.class))).thenReturn(QuestState.NOT_STARTED);
        when(quest.tryAdvanceStateForPlayer(any(UUID.class), any(QuestState.class), any(PartyBeatContext.class)))
            .thenReturn(CompletableFuture.completedFuture(false));
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = (Server) serverField.get(null);
        serverField.set(null, server);
    }

    @AfterEach
    void tearDown() throws Exception {
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, previousServer);
    }

    private GenericLocationProximityTrigger trigger() {
        Map<String, Object> c = new HashMap<>();
        c.put("type", "LOCATION_PROXIMITY");
        c.put("world", "alphac");
        c.put("x", -329);
        c.put("y", 145);
        c.put("z", 440);
        c.put("radius", 50);
        c.put("required_state", "NOT_STARTED");
        c.put("advance_state", "TRIGGER_FOUND");
        c.put("advance_sound", "none");
        GenericLocationProximityTrigger t = new GenericLocationProximityTrigger(plugin, quest, c);
        t.setClock(() -> now[0]);
        return t;
    }

    private PlayerTeleportEvent at(double x, double y, double z) {
        return new PlayerTeleportEvent(player, new Location(world, x, y, z), new Location(world, x, y, z));
    }

    private void verifyAdvances(int n) {
        verify(quest, times(n)).tryAdvanceStateForPlayer(eq(playerId), eq(QuestState.TRIGGER_FOUND), any(PartyBeatContext.class));
    }

    @Test
    @DisplayName("a player standing still inside the radius is tried once, not on every move")
    void standingStillFiresOnce() {
        GenericLocationProximityTrigger t = trigger();
        for (int i = 0; i < 20; i++) {
            t.onPlayerTeleport(at(-329, 145, 440));
            now[0] += 100;                                         // 20 moves over two seconds
        }
        verifyAdvances(1);
    }

    @Test
    @DisplayName("while the player stays, the refused advance is retried at most every REARM_MS")
    void retriesAreBounded() {
        GenericLocationProximityTrigger t = trigger();
        t.onPlayerTeleport(at(-329, 145, 440));
        now[0] += GenericLocationProximityTrigger.REARM_MS - 1;
        t.onPlayerTeleport(at(-329, 145, 440));
        verifyAdvances(1);
        now[0] += 1;
        t.onPlayerTeleport(at(-329, 145, 440));
        verifyAdvances(2);
    }

    @Test
    @DisplayName("leaving the radius re-arms it: the next entry fires at once (the happy path is unchanged)")
    void leavingRearms() {
        GenericLocationProximityTrigger t = trigger();
        t.onPlayerTeleport(at(-329, 145, 440));
        t.onPlayerTeleport(at(0, 64, 0));                          // out of range
        t.onPlayerTeleport(at(-320, 145, 440));                    // back in, same instant
        verifyAdvances(2);
    }
}
