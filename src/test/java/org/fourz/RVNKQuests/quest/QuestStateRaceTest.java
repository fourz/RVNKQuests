package org.fourz.RVNKQuests.quest;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.config.ConfigManager;
import org.fourz.RVNKQuests.data.dto.QuestProgressDTO;
import org.fourz.RVNKQuests.service.IJournalService;
import org.fourz.RVNKQuests.service.INotificationService;
import org.fourz.RVNKQuests.service.IQuestProgressService;
import org.junit.jupiter.api.*;
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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression tests for #1853: concurrent same-tick state advances must not clobber
 * each other.
 *
 * <p>Found on Event during the Tales From A Hat Zeal walk-test. When a quest's
 * LOCATION_PROXIMITY trigger and its REACH objective sit at nearly the same spot and
 * the player arrives by teleport or portal, both components fire in one tick, both read
 * the same NOT_STARTED snapshot, and both write. Before the fix the last write won, so
 * the persisted state was non-deterministic: one run stranded
 * {@code tfah_zeal_arrival} at TRIGGER_FOUND (blocking the rest of the chain until an
 * admin intervened), another landed {@code tfah_zeal_sanctum} on OBJECTIVE_FOUND.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Quest State Race Tests (#1853)")
class QuestStateRaceTest {

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

    /** Stands in for the persisted row. */
    private final Map<UUID, QuestState> store = new ConcurrentHashMap<>();

    /** Counts how many advances actually reached the write, for the no-op assertions. */
    private AtomicInteger writeCount;

    @BeforeEach
    void setUp() {
        playerId = UUID.randomUUID();
        store.clear();
        store.put(playerId, QuestState.NOT_STARTED);
        writeCount = new AtomicInteger(0);

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
            .thenAnswer(invocation -> {
                invocation.getArgument(1, Runnable.class).run();
                return null;
            });
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("test_player");

        try {
            Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, server);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not set Bukkit.server mock", e);
        }

        // A read-write backing store, so a stale read is observable as a wrong final state
        // instead of being masked by a constant stub.
        when(progressService.getQuestState(eq(playerId), anyString()))
            .thenAnswer(invocation ->
                CompletableFuture.completedFuture(store.getOrDefault(playerId, QuestState.NOT_STARTED)));
        when(progressService.updateQuestState(eq(playerId), anyString(), any(QuestState.class)))
            .thenAnswer(invocation -> {
                QuestState newState = invocation.getArgument(2);
                store.put(playerId, newState);
                writeCount.incrementAndGet();
                return CompletableFuture.completedFuture(
                    new QuestProgressDTO(playerId, "race_quest", newState, null, null, null, null));
            });
        doNothing().when(questManager).updateQuestListenersForPlayer(any(), eq(playerId));

        quest = new AbstractQuest(plugin, "race_quest", "Race Quest") {
            @Override protected boolean onStart(Player p) { return true; }
            @Override protected boolean onComplete(Player p) { return true; }
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

    @Test
    @DisplayName("A lower advance cannot clobber a higher one already committed")
    void lowerAdvanceDoesNotClobberHigher() throws Exception {
        quest.advanceStateForPlayer(playerId, QuestState.COMPLETED).get(5, TimeUnit.SECONDS);
        // A straggler trigger firing after the objective already completed the quest.
        quest.advanceStateForPlayer(playerId, QuestState.TRIGGER_FOUND).get(5, TimeUnit.SECONDS);

        assertEquals(QuestState.COMPLETED, store.get(playerId),
            "TRIGGER_FOUND must not overwrite a committed COMPLETED (#1853)");
    }

    @Test
    @DisplayName("Re-advancing to the current state is a no-op, not a second write")
    void repeatedAdvanceIsNoOp() throws Exception {
        quest.advanceStateForPlayer(playerId, QuestState.QUEST_ACTIVE).get(5, TimeUnit.SECONDS);
        int afterFirst = writeCount.get();

        quest.advanceStateForPlayer(playerId, QuestState.QUEST_ACTIVE).get(5, TimeUnit.SECONDS);

        assertEquals(afterFirst, writeCount.get(),
            "Advancing to the state the player already holds must not write again — "
                + "a second write would re-fire rewards and QuestCompleteEvent");
    }

    @RepeatedTest(10)
    @DisplayName("Co-located trigger + reach firing together always settle on the higher state")
    void coLocatedSameTickAdvancesAreDeterministic() throws Exception {
        // Mirrors tfah_zeal_arrival: arr_trigger wants TRIGGER_FOUND, arr_reach wants
        // COMPLETED, and a teleport arrival fires both against the same NOT_STARTED snapshot.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier startTogether = new CyclicBarrier(2);

            CompletableFuture<?> trigger = CompletableFuture.runAsync(() -> {
                awaitBarrier(startTogether);
                quest.advanceStateForPlayer(playerId, QuestState.TRIGGER_FOUND).join();
            }, pool);
            CompletableFuture<?> reach = CompletableFuture.runAsync(() -> {
                awaitBarrier(startTogether);
                quest.advanceStateForPlayer(playerId, QuestState.COMPLETED).join();
            }, pool);

            CompletableFuture.allOf(trigger, reach).get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(QuestState.COMPLETED, store.get(playerId),
            "Whichever order the two same-tick advances resolve in, the quest must end at the "
                + "higher state — the pre-fix engine settled on TRIGGER_FOUND about half the time, "
                + "stranding the chain (#1853)");
    }

    @Test
    @DisplayName("Admin setStateForPlayer still moves a quest backwards")
    void adminSetStateBypassesForwardGuard() throws Exception {
        quest.advanceStateForPlayer(playerId, QuestState.COMPLETED).get(5, TimeUnit.SECONDS);

        quest.setStateForPlayer(playerId, QuestState.NOT_STARTED).get(5, TimeUnit.SECONDS);

        assertEquals(QuestState.NOT_STARTED, store.get(playerId),
            "quest reset / quest state must still be able to lower the state — the "
                + "forward-progress guard applies only to automatic advances");
    }

    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Barrier wait failed", e);
        }
    }
}
