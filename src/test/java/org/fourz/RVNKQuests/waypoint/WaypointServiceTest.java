package org.fourz.RVNKQuests.waypoint;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.repository.IPreferenceRepository;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The waypoint service without a server (#2264): prefs load and save, the toggle, track/untrack,
 * auto-track on start, and the bar the tick task draws.
 */
@DisplayName("Waypoint service (#2264)")
class WaypointServiceTest {

    private RVNKQuests plugin;
    private Server server;
    private IPreferenceRepository prefs;
    private Player player;
    private World world;
    private BossBar bar;
    private DataDrivenQuest quest;
    private UUID id;
    private boolean storage;
    private final Map<String, DataDrivenQuest> quests = new HashMap<>();
    private WaypointService svc;

    private static final Waypoint DOOR = new Waypoint("obj_door", "sotw_sky_0", -421, 98, 19,
        "The gold door", WaypointStyle.BOSSBAR, false);

    @BeforeEach
    void setUp() {
        plugin = mock(RVNKQuests.class);
        server = mock(Server.class);
        prefs = mock(IPreferenceRepository.class);
        player = mock(Player.class);
        world = mock(World.class);
        bar = mock(BossBar.class);
        quest = mock(DataDrivenQuest.class);
        id = UUID.randomUUID();
        storage = true;

        when(plugin.getServer()).thenReturn(server);
        when(server.getPlayer(id)).thenReturn(player);
        when(server.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class))).thenReturn(bar);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("Shad0melt");
        when(player.getWorld()).thenReturn(world);
        when(player.isOnline()).thenReturn(true);
        when(world.getName()).thenReturn("sotw_sky_0");
        standAt(-421, 98, 419, 180f); // 400 blocks south of the door, facing north (toward it)

        when(prefs.savePreference(any(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        when(prefs.deletePreference(any(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        when(prefs.getAllPreferences(any())).thenReturn(CompletableFuture.completedFuture(Map.of()));

        when(quest.getId()).thenReturn("tfah_worldforge_aether");
        when(quest.getName()).thenReturn("The Lodestone Key");
        when(quest.hasWaypoints()).thenReturn(true);
        when(quest.isStateCached(player)).thenReturn(true);
        when(quest.getStateForPlayer(player)).thenReturn(QuestState.QUEST_ACTIVE);
        when(quest.getActiveWaypoints(QuestState.QUEST_ACTIVE)).thenReturn(List.of(DOOR));
        quests.clear();
        quests.put("tfah_worldforge_aether", quest);

        svc = new WaypointService(plugin, prefs, () -> storage, quests::get, Runnable::run);
    }

    private void standAt(double x, double y, double z, float yaw) {
        when(player.getLocation(any(Location.class))).thenAnswer(inv -> {
            Location l = inv.getArgument(0);
            l.setWorld(world);
            l.setX(x);
            l.setY(y);
            l.setZ(z);
            l.setYaw(yaw);
            return l;
        });
    }

    @Test
    @DisplayName("join loads the toggle and the tracked quest from the preference table")
    void loadsStoredPrefs() {
        when(prefs.getAllPreferences(id)).thenReturn(CompletableFuture.completedFuture(Map.of(
            WaypointPrefs.KEY_ENABLED, "false", WaypointPrefs.KEY_TRACKED, "tfah_worldforge_aether")));
        svc.load(id);
        assertFalse(svc.isEnabled(id));
        assertEquals("tfah_worldforge_aether", svc.getTracked(id));
        assertTrue(svc.tracker(id).loaded);
    }

    @Test
    @DisplayName("YAML fallback: defaults, no database call, nothing saved")
    void noStorage() {
        storage = false;
        svc.load(id);
        assertTrue(svc.isEnabled(id));
        assertNull(svc.getTracked(id));
        svc.setEnabled(id, false);
        svc.track(player, "tfah_worldforge_aether", false);
        verifyNoInteractions(prefs);
        assertFalse(svc.isPersistent());
    }

    @Test
    @DisplayName("prefs toggle: off saves false and removes the bar; tracking is kept")
    void toggle() {
        svc.load(id);
        svc.track(player, "tfah_worldforge_aether", false);
        svc.tick();
        verify(bar).addPlayer(player);

        svc.setEnabled(id, false);
        verify(prefs).savePreference(id, WaypointPrefs.KEY_ENABLED, "false");
        verify(bar).removeAll();
        assertEquals("tfah_worldforge_aether", svc.getTracked(id));
        assertFalse(svc.isEnabled(id));

        // While off, the tick draws nothing.
        clearInvocations(server);
        svc.tick();
        verify(server, never()).createBossBar(anyString(), any(BarColor.class), any(BarStyle.class));

        svc.setEnabled(id, true);
        verify(prefs).savePreference(id, WaypointPrefs.KEY_ENABLED, "true");
        svc.tick();
        verify(server).createBossBar(anyString(), any(BarColor.class), any(BarStyle.class));
    }

    @Test
    @DisplayName("track saves the quest; off deletes it and removes the bar")
    void trackAndUntrack() {
        svc.load(id);
        assertTrue(svc.track(player, "tfah_worldforge_aether", false));
        verify(prefs).savePreference(id, WaypointPrefs.KEY_TRACKED, "tfah_worldforge_aether");
        svc.tick();
        svc.untrack(id);
        verify(prefs).deletePreference(id, WaypointPrefs.KEY_TRACKED);
        verify(bar).removeAll();
        assertNull(svc.getTracked(id));
    }

    @Test
    @DisplayName("the bar: label, distance and arrow; progress from the start distance")
    void barTitleAndProgress() {
        svc.load(id);
        svc.track(player, "tfah_worldforge_aether", false);
        svc.tick();
        verify(server).createBossBar(eq(WaypointService.title("The gold door", 400, "↑")),
            eq(BarColor.YELLOW), eq(BarStyle.SOLID));
        verify(bar).setProgress(0.0);

        // Walk halfway, still facing north.
        standAt(-421, 98, 219, 180f);
        svc.tick();
        verify(bar).setTitle(WaypointService.title("The gold door", 200, "↑"));
        verify(bar).setProgress(0.5);

        // Turn to face east: the door (north) is now on the left.
        standAt(-421, 98, 219, -90f);
        svc.tick();
        verify(bar).setTitle(WaypointService.title("The gold door", 200, "←"));
    }

    @Test
    @DisplayName("another world: the bar names the target's world")
    void crossWorld() {
        when(world.getName()).thenReturn("sotw_city");
        svc.load(id);
        svc.track(player, "tfah_worldforge_aether", false);
        svc.tick();
        // No RVNKWorlds in a unit test: the world name is the display name.
        verify(server).createBossBar(eq(WaypointService.crossWorldTitle("The gold door", "sotw_sky_0")),
            eq(BarColor.YELLOW), eq(BarStyle.SOLID));
    }

    @Test
    @DisplayName("tracked quest completes: untracked and the bar removed")
    void completeUntracks() {
        svc.load(id);
        svc.track(player, "tfah_worldforge_aether", false);
        svc.tick();
        svc.onStateCommitted(id, "tfah_worldforge_aether", QuestState.OBJECTIVE_FOUND, QuestState.COMPLETED);
        assertNull(svc.getTracked(id));
        verify(bar).removeAll();
        verify(prefs).deletePreference(id, WaypointPrefs.KEY_TRACKED);
    }

    @Test
    @DisplayName("the tick sees a completed state even without the commit hook")
    void tickSeesCompletion() {
        svc.load(id);
        svc.track(player, "tfah_worldforge_aether", false);
        svc.tick();
        when(quest.getStateForPlayer(player)).thenReturn(QuestState.COMPLETED);
        svc.tick();
        assertNull(svc.getTracked(id));
        verify(bar).removeAll();
    }

    @Test
    @DisplayName("a state with no waypoint hides the bar but keeps tracking")
    void stateWithoutWaypoint() {
        svc.load(id);
        svc.track(player, "tfah_worldforge_aether", false);
        svc.tick();
        when(quest.getStateForPlayer(player)).thenReturn(QuestState.OBJECTIVE_FOUND);
        when(quest.getActiveWaypoints(QuestState.OBJECTIVE_FOUND)).thenReturn(List.of());
        svc.tick();
        verify(bar).removeAll();
        assertEquals("tfah_worldforge_aether", svc.getTracked(id));
    }

    @Test
    @DisplayName("an uncached state: the tick asks for a load and draws nothing")
    void uncachedState() {
        when(quest.isStateCached(player)).thenReturn(false);
        svc.load(id);
        svc.track(player, "tfah_worldforge_aether", false);
        svc.tick();
        svc.tick(); // throttled: one request
        verify(quest, times(1)).getStateForPlayer(player);
        verify(server, never()).createBossBar(anyString(), any(BarColor.class), any(BarStyle.class));
    }

    @Test
    @DisplayName("starting a quest with waypoints auto-tracks it only when nothing is tracked")
    void autoTrack() {
        svc.load(id);
        svc.onStateCommitted(id, "tfah_worldforge_aether", QuestState.NOT_STARTED, QuestState.QUEST_ACTIVE);
        assertEquals("tfah_worldforge_aether", svc.getTracked(id));
        verify(player).sendMessage(contains("Tracking"));

        DataDrivenQuest other = mock(DataDrivenQuest.class);
        when(other.hasWaypoints()).thenReturn(true);
        quests.put("other", other);
        svc.onStateCommitted(id, "other", QuestState.NOT_STARTED, QuestState.TRIGGER_FOUND);
        assertEquals("tfah_worldforge_aether", svc.getTracked(id));
    }

    @Test
    @DisplayName("no auto-track for a quest without waypoints, or for a mid-quest beat")
    void noAutoTrack() {
        svc.load(id);
        when(quest.hasWaypoints()).thenReturn(false);
        svc.onStateCommitted(id, "tfah_worldforge_aether", QuestState.NOT_STARTED, QuestState.QUEST_ACTIVE);
        assertNull(svc.getTracked(id));
        when(quest.hasWaypoints()).thenReturn(true);
        svc.onStateCommitted(id, "tfah_worldforge_aether", QuestState.TRIGGER_FOUND, QuestState.QUEST_ACTIVE);
        assertNull(svc.getTracked(id));
        verifyNoInteractions(bar);
    }

    @Test
    @DisplayName("a quest without waypoints is never drawn, even if tracked")
    void questWithoutWaypointsDrawsNothing() {
        when(quest.getActiveWaypoints(any())).thenReturn(List.of());
        svc.load(id);
        svc.track(player, "tfah_worldforge_aether", false);
        svc.tick();
        verify(server, never()).createBossBar(anyString(), any(BarColor.class), any(BarStyle.class));
    }

    @Test
    @DisplayName("compass style: holding a compass in the target's world points it there, no bar")
    void compassStyle() {
        Waypoint compassDoor = new Waypoint("obj_door", "sotw_sky_0", -421, 98, 19,
            "The gold door", WaypointStyle.COMPASS, false);
        when(quest.getActiveWaypoints(QuestState.QUEST_ACTIVE)).thenReturn(List.of(compassDoor));
        org.bukkit.inventory.PlayerInventory inv = mock(org.bukkit.inventory.PlayerInventory.class);
        org.bukkit.inventory.ItemStack compass = mock(org.bukkit.inventory.ItemStack.class);
        org.bukkit.inventory.ItemStack air = mock(org.bukkit.inventory.ItemStack.class);
        when(compass.getType()).thenReturn(org.bukkit.Material.COMPASS);
        when(air.getType()).thenReturn(org.bukkit.Material.AIR);
        when(player.getInventory()).thenReturn(inv);
        when(inv.getItemInMainHand()).thenReturn(compass);
        when(inv.getItemInOffHand()).thenReturn(air);
        Location spawn = new Location(world, 0, 64, 0);
        when(world.getSpawnLocation()).thenReturn(spawn);

        svc.load(id);
        svc.track(player, "tfah_worldforge_aether", false);
        svc.tick();
        verify(player).setCompassTarget(argThat(l -> l.getX() == -421 && l.getZ() == 19));
        verify(server, never()).createBossBar(anyString(), any(BarColor.class), any(BarStyle.class));

        // Put the compass away: the bar comes back.
        when(inv.getItemInMainHand()).thenReturn(air);
        svc.tick();
        verify(server).createBossBar(anyString(), any(BarColor.class), any(BarStyle.class));

        // Off: the compass gets its normal target back.
        svc.untrack(id);
        verify(player).setCompassTarget(spawn);
    }

    @Test
    @DisplayName("--trail: dust only for that player, along at most 9 blocks, stops after 5 seconds")
    void trail() {
        svc.load(id);
        svc.track(player, "tfah_worldforge_aether", true);
        svc.tick();
        verify(player, times(WaypointService.TRAIL_BLOCKS)).spawnParticle(eq(org.bukkit.Particle.DUST),
            anyDouble(), anyDouble(), anyDouble(), eq(1), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any());
        svc.tracker(id).trailUntil = System.currentTimeMillis() - 1;
        clearInvocations(player);
        svc.tick();
        verify(player, never()).spawnParticle(any(org.bukkit.Particle.class),
            anyDouble(), anyDouble(), anyDouble(), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any());
        assertEquals(0L, svc.tracker(id).trailUntil);
    }

    @Test
    @DisplayName("shutdown removes every bar")
    void shutdownRemovesBars() {
        svc.load(id);
        svc.track(player, "tfah_worldforge_aether", false);
        svc.tick();
        svc.shutdown();
        verify(bar).removeAll();
        assertNull(svc.getTracked(id));
    }
}
