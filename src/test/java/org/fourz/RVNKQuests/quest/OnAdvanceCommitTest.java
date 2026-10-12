package org.fourz.RVNKQuests.quest;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.config.ConfigManager;
import org.fourz.RVNKQuests.data.dto.QuestDTO;
import org.fourz.RVNKQuests.data.dto.QuestProgressDTO;
import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.party.PartyBeatContext;
import org.fourz.RVNKQuests.service.IJournalService;
import org.fourz.RVNKQuests.service.INotificationService;
import org.fourz.RVNKQuests.service.IQuestProgressService;
import org.fourz.RVNKQuests.service.IRewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@code on_advance} fires once per player, and only after the advance commits (#2267).
 *
 * <p>Drives a real {@link DataDrivenQuest} through the real write chain; only the progress store,
 * the scheduler and the reward service are stand-ins.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("on_advance commit point (#2267)")
class OnAdvanceCommitTest {

    private static final String QUEST_ID = "tfah_gold_door";
    private static final String PREREQ_ID = "tfah_first_day";

    @Mock private RVNKQuests plugin;
    @Mock private IQuestProgressService progressService;
    @Mock private IJournalService journalService;
    @Mock private INotificationService notifService;
    @Mock private QuestManager questManager;
    @Mock private ConfigManager configManager;
    @Mock private IRewardService rewardService;
    @Mock private Server server;
    @Mock private Player player;
    @Mock private PluginManager pluginManager;
    @Mock private org.bukkit.configuration.file.FileConfiguration config;
    @Mock private org.bukkit.scheduler.BukkitScheduler scheduler;

    private UUID playerId;
    private final Map<String, QuestState> store = new ConcurrentHashMap<>();
    /** The state the store held at the moment each delivery call was made. */
    private final List<QuestState> stateAtDelivery = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        playerId = UUID.randomUUID();
        store.clear();
        stateAtDelivery.clear();

        when(plugin.getQuestProgressService()).thenReturn(progressService);
        when(plugin.getJournalService()).thenReturn(journalService);
        when(plugin.getNotificationService()).thenReturn(notifService);
        when(plugin.getQuestManager()).thenReturn(questManager);
        when(plugin.getConfigManager()).thenReturn(configManager);
        when(plugin.getRewardService()).thenReturn(rewardService);
        when(plugin.getOnceRewardStore()).thenReturn(null);
        when(plugin.getServer()).thenReturn(server);
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
        when(rewardService.deliverRewards(eq(playerId), eq(QUEST_ID), anyList(), anyBoolean()))
            .thenAnswer(inv -> {
                stateAtDelivery.add(store.get(QUEST_ID));
                return CompletableFuture.completedFuture(
                    new IRewardService.BatchRewardResult(1, 1, 0, List.of(), false));
            });
    }

    private static Map<String, Object> entry(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    private DataDrivenQuest quest(List<String> prerequisites) {
        Map<String, Object> door = new HashMap<>();
        door.put("type", "NPC_INTERACT");
        door.put("npc_key", "gatekeeper");
        door.put("advance_state", "TRIGGER_FOUND");
        door.put("on_advance", List.of(
            entry("reward_id", "b_xp", "type", "EXPERIENCE", "amount", 50),
            entry("reward_id", "a_key", "type", "COMMAND", "value", "lore item give %player% Lodestone Key")));
        Map<String, Object> plain = new HashMap<>();
        plain.put("type", "NPC_INTERACT");
        plain.put("npc_key", "porter");
        Map<String, Object> components = new HashMap<>();
        components.put("trig_door", door);
        components.put("trig_plain", plain);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("components", components);
        metadata.put("state_mapping", Map.of("NOT_STARTED", List.of("trig_door", "trig_plain")));
        QuestDTO def = new QuestDTO(QUEST_ID, "The Gold Door", "", null, false, 0,
            null, null, prerequisites, null, metadata);
        return new DataDrivenQuest(plugin, def);
    }

    private boolean fire(DataDrivenQuest quest, String componentId, QuestState target) throws Exception {
        return quest.tryAdvanceStateForPlayer(playerId, target, null, quest.onAdvanceHook(componentId))
            .get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("a committed advance delivers the list once, sorted by reward_id, after the write")
    void firesOnceAfterCommit() throws Exception {
        DataDrivenQuest quest = quest(null);
        assertTrue(fire(quest, "trig_door", QuestState.TRIGGER_FOUND));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RewardDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(rewardService, timeout(2000).times(1)).deliverRewards(eq(playerId), eq(QUEST_ID), captor.capture(), eq(true));
        assertEquals(List.of("a_key", "b_xp"), captor.getValue().stream().map(RewardDTO::rewardId).toList());
        assertEquals(List.of(QuestState.TRIGGER_FOUND), stateAtDelivery,
            "delivery must see the committed state - never fire before the commit");
    }

    @Test
    @DisplayName("firing the same component again does not deliver twice")
    void neverTwiceForOneTransition() throws Exception {
        DataDrivenQuest quest = quest(null);
        assertTrue(fire(quest, "trig_door", QuestState.TRIGGER_FOUND));
        assertFalse(fire(quest, "trig_door", QuestState.TRIGGER_FOUND));
        assertFalse(fire(quest, "trig_door", QuestState.TRIGGER_FOUND));
        // The hook runs on the pool thread after the write; wait for it, then make sure no more come.
        verify(rewardService, timeout(2000).times(1)).deliverRewards(any(), anyString(), anyList(), anyBoolean());
        verify(rewardService, after(300).times(1)).deliverRewards(any(), anyString(), anyList(), anyBoolean());
    }

    @Test
    @DisplayName("two fires racing for one player commit once and deliver once")
    void racingFiresDeliverOnce() throws Exception {
        DataDrivenQuest quest = quest(null);
        Consumer<UUID> hook = quest.onAdvanceHook("trig_door");
        List<CompletableFuture<Boolean>> fires = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            fires.add(CompletableFuture.supplyAsync(() -> quest.tryAdvanceStateForPlayer(
                playerId, QuestState.TRIGGER_FOUND, null, hook)).thenCompose(f -> f));
        }
        long committed = 0;
        for (CompletableFuture<Boolean> f : fires) {
            if (f.get(5, TimeUnit.SECONDS)) committed++;
        }
        assertEquals(1, committed);
        verify(rewardService, timeout(2000).times(1)).deliverRewards(any(), anyString(), anyList(), anyBoolean());
        verify(rewardService, after(300).times(1)).deliverRewards(any(), anyString(), anyList(), anyBoolean());
    }

    @Test
    @DisplayName("a refused advance (prerequisite gate) delivers nothing")
    void refusedAdvanceDeliversNothing() throws Exception {
        DataDrivenQuest quest = quest(List.of(PREREQ_ID));
        assertFalse(fire(quest, "trig_door", QuestState.TRIGGER_FOUND));
        verify(rewardService, after(300).never()).deliverRewards(any(), anyString(), anyList(), anyBoolean());
        assertNull(store.get(QUEST_ID));
    }

    @Test
    @DisplayName("a component without on_advance has no hook, so the call is exactly the pre-1.1.69 one")
    void noHookWithoutOnAdvance() {
        DataDrivenQuest quest = quest(null);
        assertNull(quest.onAdvanceHook("trig_plain"));
        assertNull(quest.onAdvanceHook("no_such_component"));
        assertNotNull(quest.onAdvanceHook("TRIG_DOOR"), "component ids match case-insensitively, as fire does");
        assertEquals(2, quest.getOnAdvanceRewards("trig_door").size());
        assertTrue(quest.getEngineKeyProblems().isEmpty(), quest.getEngineKeyProblems().toString());
    }

    @Test
    @DisplayName("a live listener resolves to its component id, so the component's own hook is found")
    void listenerResolvesToItsComponent() {
        DataDrivenQuest quest = quest(null);
        QuestComponentFactory factory = new QuestComponentFactory(plugin, quest);
        List<Listener> listeners = factory.createListenersForState(QuestState.NOT_STARTED, quest.getDefinition());
        assertEquals(2, listeners.size());
        List<String> ids = listeners.stream().map(factory::componentIdOf).sorted().toList();
        assertEquals(List.of("trig_door", "trig_plain"), ids);
        assertNull(factory.componentIdOf(mock(Listener.class)));
    }

    @Test
    @DisplayName("ComponentAdvance makes the old call when there is no hook, and the hooked call when there is")
    void componentAdvanceRouting() {
        DataDrivenQuest mocked = mock(DataDrivenQuest.class);
        Listener component = mock(Listener.class);
        PartyBeatContext ctx = new PartyBeatContext("world", 0, 64, 0, 3, QuestState.NOT_STARTED);
        when(mocked.tryAdvanceStateForPlayer(any(UUID.class), any(QuestState.class), any()))
            .thenReturn(CompletableFuture.completedFuture(true));

        ComponentAdvance.tryAdvance(mocked, component, playerId, QuestState.TRIGGER_FOUND, ctx);
        verify(mocked).tryAdvanceStateForPlayer(playerId, QuestState.TRIGGER_FOUND, ctx);

        Consumer<UUID> hook = uuid -> { };
        when(mocked.onAdvanceHook(component)).thenReturn(hook);
        when(mocked.tryAdvanceStateForPlayer(any(UUID.class), any(QuestState.class), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(true));
        ComponentAdvance.tryAdvance(mocked, component, playerId, QuestState.TRIGGER_FOUND, ctx);
        verify(mocked).tryAdvanceStateForPlayer(playerId, QuestState.TRIGGER_FOUND, ctx, hook);
    }
}
