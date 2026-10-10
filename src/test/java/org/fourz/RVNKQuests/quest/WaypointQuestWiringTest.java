package org.fourz.RVNKQuests.quest;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.config.ConfigManager;
import org.fourz.RVNKQuests.data.dto.QuestDTO;
import org.fourz.RVNKQuests.data.dto.QuestProgressDTO;
import org.fourz.RVNKQuests.service.IJournalService;
import org.fourz.RVNKQuests.service.INotificationService;
import org.fourz.RVNKQuests.service.IQuestProgressService;
import org.fourz.RVNKQuests.waypoint.Waypoint;
import org.fourz.RVNKQuests.waypoint.WaypointService;
import org.fourz.RVNKQuests.waypoint.WaypointStyle;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * A real {@link DataDrivenQuest} exposes its waypoints, and a committed advance tells the waypoint
 * service (#2264). A quest without waypoints is unchanged.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Waypoint wiring in DataDrivenQuest and AbstractQuest (#2264)")
class WaypointQuestWiringTest {

    private static final String QUEST_ID = "tfah_worldforge_aether";

    @Mock private RVNKQuests plugin;
    @Mock private IQuestProgressService progressService;
    @Mock private IJournalService journalService;
    @Mock private INotificationService notifService;
    @Mock private QuestManager questManager;
    @Mock private ConfigManager configManager;
    @Mock private Server server;
    @Mock private Player player;
    @Mock private PluginManager pluginManager;
    @Mock private org.bukkit.configuration.file.FileConfiguration config;
    @Mock private org.bukkit.scheduler.BukkitScheduler scheduler;
    @Mock private WaypointService waypoints;

    private UUID playerId;
    private final Map<String, QuestState> store = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        playerId = UUID.randomUUID();
        store.clear();
        when(plugin.getQuestProgressService()).thenReturn(progressService);
        when(plugin.getJournalService()).thenReturn(journalService);
        when(plugin.getNotificationService()).thenReturn(notifService);
        when(plugin.getQuestManager()).thenReturn(questManager);
        when(plugin.getConfigManager()).thenReturn(configManager);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getWaypointService()).thenReturn(waypoints);
        when(configManager.getConfig()).thenReturn(config);
        when(journalService.isAvailable()).thenReturn(false);
        when(server.getPlayer(playerId)).thenReturn(player);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(any(org.bukkit.plugin.Plugin.class), any(Runnable.class)))
            .thenAnswer(inv -> { inv.getArgument(1, Runnable.class).run(); return null; });
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Shad0melt");

        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);

        when(progressService.getQuestState(eq(playerId), anyString()))
            .thenAnswer(inv -> CompletableFuture.completedFuture(
                store.getOrDefault(inv.getArgument(1, String.class), QuestState.NOT_STARTED)));
        when(progressService.updateQuestState(eq(playerId), anyString(), any(QuestState.class)))
            .thenAnswer(inv -> {
                String id = inv.getArgument(1, String.class);
                QuestState s = inv.getArgument(2, QuestState.class);
                store.put(id, s);
                return CompletableFuture.completedFuture(new QuestProgressDTO(playerId, id, s, null, null, null, null));
            });
    }

    private static Map<String, Object> entry(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    private DataDrivenQuest quest(boolean withWaypoint) {
        Map<String, Object> rell = entry("type", "NPC_INTERACT", "npc_key", "guide_aether", "advance_state", "QUEST_ACTIVE");
        Map<String, Object> key = entry("objective_type", "INTERACT", "block_type", "LODESTONE",
            "advance_state", "OBJECTIVE_FOUND");
        if (withWaypoint) {
            key.put("waypoint", entry("world", "sotw_sky_0", "x", -421, "y", 98, "z", 19, "label", "The gold door"));
        }
        Map<String, Object> components = new HashMap<>();
        components.put("trig_rell", rell);
        components.put("obj_lodestone_key", key);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("components", components);
        metadata.put("state_mapping", Map.of(
            "NOT_STARTED", List.of("trig_rell"),
            "QUEST_ACTIVE", List.of("obj_lodestone_key")));
        return new DataDrivenQuest(plugin, new QuestDTO(QUEST_ID, "The Lodestone Key", "", null, false, 0,
            null, null, List.of(), null, metadata));
    }

    @Test
    @DisplayName("the quest exposes the active set per state")
    void exposesWaypoints() {
        DataDrivenQuest q = quest(true);
        assertTrue(q.hasWaypoints());
        assertTrue(q.getEngineKeyProblems().isEmpty(), q.getEngineKeyProblems().toString());
        assertEquals(List.of(new Waypoint("obj_lodestone_key", "sotw_sky_0", -421, 98, 19, "The gold door",
            WaypointStyle.BOSSBAR, false)), q.getActiveWaypoints(QuestState.QUEST_ACTIVE));
        assertEquals(List.of(), q.getActiveWaypoints(QuestState.NOT_STARTED));
        assertEquals(List.of(), q.getActiveWaypoints(null));
    }

    @Test
    @DisplayName("a quest without waypoints has none, and no new problems")
    void noWaypoints() {
        DataDrivenQuest q = quest(false);
        assertFalse(q.hasWaypoints());
        assertTrue(q.getWaypoints().isEmpty());
        assertTrue(q.getEngineKeyProblems().isEmpty());
    }

    @Test
    @DisplayName("a bad waypoint block reaches the validate/load problem list")
    void badBlockIsAProblem() {
        Map<String, Object> components = Map.of("obj", entry("objective_type", "REACH", "waypoint", "far away"));
        DataDrivenQuest q = new DataDrivenQuest(plugin, new QuestDTO("q", "Q", "", null, false, 0, null, null,
            List.of(), null, Map.of("components", components, "state_mapping", Map.of())));
        assertEquals(1, q.getEngineKeyProblems().size());
        assertFalse(q.hasWaypoints());
    }

    @Test
    @DisplayName("a committed advance tells the waypoint service on the main thread")
    void advanceNotifiesService() throws Exception {
        DataDrivenQuest q = quest(true);
        q.advanceStateForPlayer(playerId, QuestState.QUEST_ACTIVE).get(5, TimeUnit.SECONDS);
        verify(waypoints).onStateCommitted(playerId, QUEST_ID, QuestState.NOT_STARTED, QuestState.QUEST_ACTIVE);
    }

    @Test
    @DisplayName("a refused advance does not")
    void refusedAdvanceIsSilent() throws Exception {
        store.put(QUEST_ID, QuestState.OBJECTIVE_FOUND);
        DataDrivenQuest q = quest(true);
        // Backwards: refused by the monotonic guard.
        assertFalse(q.tryAdvanceStateForPlayer(playerId, QuestState.QUEST_ACTIVE, null).get(5, TimeUnit.SECONDS));
        verifyNoInteractions(waypoints);
    }

    @Test
    @DisplayName("a waypoint fault never blocks the advance")
    void faultIsContained() throws Exception {
        doThrow(new IllegalStateException("boom")).when(waypoints)
            .onStateCommitted(any(), anyString(), any(), any());
        DataDrivenQuest q = quest(true);
        q.advanceStateForPlayer(playerId, QuestState.QUEST_ACTIVE).get(5, TimeUnit.SECONDS);
        assertEquals(QuestState.QUEST_ACTIVE, store.get(QUEST_ID));
        verify(questManager).updateQuestListenersForPlayer(q, playerId);
    }
}
