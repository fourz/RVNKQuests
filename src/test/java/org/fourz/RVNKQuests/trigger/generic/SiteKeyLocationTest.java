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

/**
 * #2316: REACH and LOCATION_PROXIMITY take a named {@code site} (resolved through RVNKCore's site locator) in place of
 * fixed x/y/z, so a quest line follows a drowned_world glyph trail on any seed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("site keys: REACH and LOCATION_PROXIMITY resolve their target by name (#2316)")
class SiteKeyLocationTest {

    @Mock private RVNKQuests plugin;
    @Mock private DataDrivenQuest quest;
    @Mock private Player player;
    @Mock private World world;
    @Mock private Server server;
    @Mock private BukkitScheduler scheduler;

    private Server previousServer;
    private UUID playerId;
    private final AtomicInteger lookups = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Tester");
        when(player.getServer()).thenReturn(server);
        when(world.getName()).thenReturn("dw_trail");
        when(server.getWorld("dw_trail")).thenReturn(world);
        when(quest.getId()).thenReturn("glyph_song");
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(any(org.bukkit.plugin.Plugin.class), any(Runnable.class)))
            .thenAnswer(inv -> { inv.getArgument(1, Runnable.class).run(); return null; });
        when(quest.tryAdvanceStateForPlayer(any(UUID.class), any(QuestState.class), any(PartyBeatContext.class)))
            .thenReturn(CompletableFuture.completedFuture(true));
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = (Server) serverField.get(null);
        serverField.set(null, server);
        SiteResolver.setLookup((w, key) -> {
            lookups.incrementAndGet();
            return w.equals("dw_trail") && key.equals("drowned:trail:2") ? new double[] {335.5, -59.5, 882.5} : null;
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        SiteResolver.setLookup(null);
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, previousServer);
    }

    private Map<String, Object> config(String type, String site, String required, String advance) {
        Map<String, Object> c = new HashMap<>();
        c.put(type.equals("REACH") ? "objective_type" : "type", type);
        c.put("world", "dw_trail");
        c.put("site", site);
        c.put("radius", 6);
        c.put("required_state", required);
        c.put("advance_state", advance);
        c.put("advance_sound", "none");
        return c;
    }

    private PlayerTeleportEvent arrive(double x, double y, double z) {
        return new PlayerTeleportEvent(player, new Location(world, 0, 64, 0), new Location(world, x, y, z));
    }

    @Test
    @DisplayName("LOCATION_PROXIMITY fires at the site's position and carries it to the party beat")
    void proximityAtTheSite() {
        when(quest.getStateForPlayer(any(Player.class))).thenReturn(QuestState.NOT_STARTED);
        GenericLocationProximityTrigger t = new GenericLocationProximityTrigger(plugin, quest,
            config("LOCATION_PROXIMITY", "drowned:trail:2", "NOT_STARTED", "TRIGGER_FOUND"));
        t.onPlayerTeleport(arrive(0, 64, 0));                       // far: nothing
        verify(quest, never()).tryAdvanceStateForPlayer(any(UUID.class), any(QuestState.class), any(PartyBeatContext.class));
        t.onPlayerTeleport(arrive(333, -60, 880));
        verify(quest).tryAdvanceStateForPlayer(eq(playerId), eq(QuestState.TRIGGER_FOUND), any(PartyBeatContext.class));
    }

    @Test
    @DisplayName("REACH completes within the radius of the site")
    void reachTheSite() {
        when(quest.getStateForPlayer(any(Player.class))).thenReturn(QuestState.QUEST_ACTIVE);
        GenericReachObjective o = new GenericReachObjective(plugin, quest,
            config("REACH", "drowned:trail:2", "QUEST_ACTIVE", "OBJECTIVE_FOUND"));
        o.onPlayerTeleport(arrive(345, -60, 882));                  // 9.5 away: outside 6
        verify(quest, never()).tryAdvanceStateForPlayer(any(UUID.class), any(QuestState.class), any(PartyBeatContext.class));
        o.onPlayerTeleport(arrive(338, -60, 884));
        verify(quest).tryAdvanceStateForPlayer(eq(playerId), eq(QuestState.OBJECTIVE_FOUND), any(PartyBeatContext.class));
    }

    @Test
    @DisplayName("an unknown site never fires, and is asked again later (a miss is not kept)")
    void unknownSite() {
        when(quest.getStateForPlayer(any(Player.class))).thenReturn(QuestState.QUEST_ACTIVE);
        GenericReachObjective o = new GenericReachObjective(plugin, quest,
            config("REACH", "drowned:nowhere", "QUEST_ACTIVE", "OBJECTIVE_FOUND"));
        o.onPlayerTeleport(arrive(0, 64, 0));
        o.onPlayerTeleport(arrive(0, 64, 0));
        verify(quest, never()).tryAdvanceStateForPlayer(any(UUID.class), any(QuestState.class), any(PartyBeatContext.class));
        assertEquals(2, lookups.get());
    }

    @Test
    @DisplayName("a found site is kept: one lookup for many checks")
    void foundSiteIsKept() {
        assertArrayEquals(new double[] {335.5, -59.5, 882.5}, SiteResolver.resolve("dw_trail", "drowned:trail:2"));
        SiteResolver.resolve("dw_trail", "drowned:trail:2");
        assertEquals(1, lookups.get());
        assertNull(SiteResolver.resolve("other", "drowned:trail:2"));
    }
}
