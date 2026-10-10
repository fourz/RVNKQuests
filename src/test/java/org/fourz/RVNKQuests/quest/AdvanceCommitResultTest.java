package org.fourz.RVNKQuests.quest;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.config.ConfigManager;
import org.fourz.RVNKQuests.data.dto.QuestProgressDTO;
import org.fourz.RVNKQuests.event.QuestCompleteEvent;
import org.fourz.RVNKQuests.party.PartyBeatContext;
import org.fourz.RVNKQuests.service.IJournalService;
import org.fourz.RVNKQuests.service.INotificationService;
import org.fourz.RVNKQuests.service.IQuestProgressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@code tryAdvanceStateForPlayer} reports whether the change committed (#2249, for #1764).
 *
 * <p>Every refusing exit of {@code applyStateChange} completes normally, so the {@code Void}
 * overloads cannot tell a caller whether anything happened. These tests pin the boolean to the
 * real outcome for each gate, and pin that the COMPLETED side effects still fire exactly once,
 * inside the state machine, as before.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Advance commit result (#2249)")
class AdvanceCommitResultTest {

    private static final String QUEST_ID = "sotw_cavern";
    private static final String PREREQ_ID = "sotw_aether";
    private static final PartyBeatContext CTX =
        new PartyBeatContext("sotw_city", 8, 63, 10, 5, QuestState.NOT_STARTED);

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

    private UUID playerId;
    private AbstractQuest quest;
    private final AtomicInteger onCompleteCalls = new AtomicInteger();

    /** Per-quest persisted state for the subject player. Absent means NOT_STARTED. */
    private final Map<String, QuestState> store = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        playerId = UUID.randomUUID();
        store.clear();
        onCompleteCalls.set(0);

        when(plugin.getQuestProgressService()).thenReturn(progressService);
        when(plugin.getJournalService()).thenReturn(journalService);
        when(plugin.getNotificationService()).thenReturn(notifService);
        when(plugin.getQuestManager()).thenReturn(questManager);
        when(plugin.getConfigManager()).thenReturn(configManager);
        when(plugin.getServer()).thenReturn(server);
        when(configManager.getConfig()).thenReturn(config);
        when(config.getBoolean(eq("quests.announce_completion"), anyBoolean())).thenReturn(false);
        when(journalService.isAvailable()).thenReturn(false);
        when(server.getPlayer(playerId)).thenReturn(player);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(any(org.bukkit.plugin.Plugin.class), any(Runnable.class)))
            .thenAnswer(inv -> { inv.getArgument(1, Runnable.class).run(); return null; });
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("test_player");

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
                return CompletableFuture.completedFuture(
                    new QuestProgressDTO(playerId, id, s, null, null, null, null));
            });
        doNothing().when(questManager).updateQuestListenersForPlayer(any(), eq(playerId));

        quest = new AbstractQuest(plugin, QUEST_ID, "The Cavern") {
            @Override protected List<String> getPrerequisiteQuestIds() { return List.of(PREREQ_ID); }
            @Override protected boolean onStart(Player p) { return true; }
            @Override protected boolean onComplete(Player p) { onCompleteCalls.incrementAndGet(); return true; }
            @Override public boolean update(Player p) { return true; }
            @Override public void initialize() {}
            @Override public void cleanup() {}
            @Override public List<org.bukkit.event.Listener> createListenersForState(QuestState s) {
                return List.of();
            }
            @Override public org.bukkit.Location getStartLocation() { return null; }
            @Override public String getStartTrigger() { return "test"; }
        };
    }

    private boolean tryAdvance(QuestState target) throws Exception {
        return quest.tryAdvanceStateForPlayer(playerId, target, CTX).get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("prerequisite unmet: false, and the state stays NOT_STARTED")
    void prerequisiteBlockedReportsFalse() throws Exception {
        assertFalse(tryAdvance(QuestState.TRIGGER_FOUND));
        assertNull(store.get(QUEST_ID), "nothing may be written for a blocked advance");
    }

    @Test
    @DisplayName("prerequisite met: true, and the state is written")
    void prerequisiteMetReportsTrue() throws Exception {
        store.put(PREREQ_ID, QuestState.COMPLETED);

        assertTrue(tryAdvance(QuestState.TRIGGER_FOUND));
        assertEquals(QuestState.TRIGGER_FOUND, store.get(QUEST_ID));
    }

    @Test
    @DisplayName("already at the target: false (no side effects re-fired)")
    void alreadyThereReportsFalse() throws Exception {
        store.put(QUEST_ID, QuestState.TRIGGER_FOUND);

        assertFalse(tryAdvance(QuestState.TRIGGER_FOUND));
    }

    @Test
    @DisplayName("already past the target: false (monotonic guard)")
    void alreadyPastReportsFalse() throws Exception {
        store.put(QUEST_ID, QuestState.QUEST_ACTIVE);

        assertFalse(tryAdvance(QuestState.TRIGGER_FOUND));
        assertEquals(QuestState.QUEST_ACTIVE, store.get(QUEST_ID));
    }

    @Test
    @DisplayName("a null context takes the solo path and still reports the commit")
    void nullContext() throws Exception {
        store.put(QUEST_ID, QuestState.TRIGGER_FOUND);

        assertTrue(quest.tryAdvanceStateForPlayer(playerId, QuestState.QUEST_ACTIVE, null)
            .get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("COMPLETED: true, and the completion side effects fire exactly once, unchanged")
    void completedSideEffectsUnchanged() throws Exception {
        store.put(QUEST_ID, QuestState.OBJECTIVE_FOUND);

        assertTrue(tryAdvance(QuestState.COMPLETED));
        // A second attempt is refused and must not re-fire anything.
        assertFalse(tryAdvance(QuestState.COMPLETED));

        assertEquals(1, onCompleteCalls.get(), "onComplete (rewards) must fire exactly once");
        verify(notifService, times(1)).notifyQuestComplete(player, "The Cavern");
        verify(pluginManager, times(1)).callEvent(any(QuestCompleteEvent.class));
    }

    @Test
    @DisplayName("a failed write completes exceptionally, not true")
    void failedWrite() {
        store.put(QUEST_ID, QuestState.TRIGGER_FOUND);
        when(progressService.updateQuestState(eq(playerId), eq(QUEST_ID), any(QuestState.class)))
            .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("db down")));

        CompletableFuture<Boolean> result = quest.tryAdvanceStateForPlayer(playerId, QuestState.QUEST_ACTIVE, CTX);

        assertThrows(java.util.concurrent.ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("the Void overloads still complete normally on a refusal (existing callers)")
    void voidOverloadsUnchanged() throws Exception {
        assertNull(quest.advanceStateForPlayer(playerId, QuestState.TRIGGER_FOUND, CTX).get(5, TimeUnit.SECONDS));
        assertNull(quest.advanceStateForPlayer(playerId, QuestState.TRIGGER_FOUND).get(5, TimeUnit.SECONDS));
        assertNull(store.get(QUEST_ID));
    }
}
