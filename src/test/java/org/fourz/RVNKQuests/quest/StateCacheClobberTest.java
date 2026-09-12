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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression tests for #2094: a populating read must never overwrite an authoritative write.
 *
 * <p>Found on Dev. A {@code WORLD_EVENT PLAYER_JOIN} beat advanced a player on join and the advance
 * committed to the database — but {@code preloadStateForPlayer}, whose read had been issued moments
 * earlier on the same join, landed afterwards and wrote the pre-advance value straight over the
 * cache with an unconditional {@code put}.</p>
 *
 * <p>The database stayed correct and only the cache went wrong, which is the worst possible shape:
 * {@code /quest debug player}, {@code /quest debug list} and every move-based component gate read
 * that cache, so the tooling <b>confirmed</b> the stale answer. It cost a false bug call — a beat
 * that had demonstrably fired was reported as not firing, against a tester who had watched it
 * happen, and only a relog (evict then re-preload) settled it.</p>
 *
 * <p>These tests drive the race deterministically by holding the read's future open until after the
 * advance has committed, rather than relying on thread timing.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("State cache clobber (#2094)")
class StateCacheClobberTest {

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

    /**
     * When non-null, {@code getQuestState} returns this instead of a completed future, so a test
     * can decide exactly when the read lands relative to the advance.
     */
    private CompletableFuture<QuestState> heldRead;

    @BeforeEach
    void setUp() {
        playerId = UUID.randomUUID();
        store.clear();
        store.put(playerId, QuestState.NOT_STARTED);
        heldRead = null;

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

        try {
            Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, server);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not set Bukkit.server mock", e);
        }

        when(progressService.getQuestState(eq(playerId), anyString()))
            .thenAnswer(inv -> heldRead != null
                ? heldRead
                : CompletableFuture.completedFuture(
                    store.getOrDefault(playerId, QuestState.NOT_STARTED)));
        when(progressService.updateQuestState(eq(playerId), anyString(), any(QuestState.class)))
            .thenAnswer(inv -> {
                QuestState newState = inv.getArgument(2);
                store.put(playerId, newState);
                return CompletableFuture.completedFuture(
                    new QuestProgressDTO(playerId, "cache_quest", newState, null, null, null, null));
            });
        // getPathChoice() reads through getProgress(), not a dedicated method.
        when(progressService.getProgress(eq(playerId), anyString()))
            .thenReturn(CompletableFuture.completedFuture(java.util.Optional.empty()));
        doNothing().when(questManager).updateQuestListenersForPlayer(any(), eq(playerId));

        quest = new AbstractQuest(plugin, "cache_quest", "Cache Quest") {
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

    @Nested
    @DisplayName("The join-time race")
    class JoinRace {

        @Test
        @DisplayName("a preload landing after an advance must not overwrite it")
        void preloadMustNotClobberAdvance() throws Exception {
            // 1. Join: preload issues its read. Hold it open — this is the real window, in which
            //    the read has been sent and its answer has not yet come back.
            CompletableFuture<QuestState> pending = new CompletableFuture<>();
            heldRead = pending;
            quest.preloadStateForPlayer(playerId);

            // 2. The PLAYER_JOIN beat advances and commits while that read is still in flight.
            heldRead = null;
            quest.advanceStateForPlayer(playerId, QuestState.TRIGGER_FOUND).get(5, TimeUnit.SECONDS);
            assertEquals(QuestState.TRIGGER_FOUND, store.get(playerId), "precondition: DB advanced");

            // 3. The preload's read finally lands, carrying the value from before the advance.
            pending.complete(QuestState.NOT_STARTED);

            // The cache is what every console read and every move-based gate consults.
            assertEquals(QuestState.TRIGGER_FOUND, quest.getStateForPlayer(player),
                "the stale preload must not overwrite the committed advance (#2094)");
        }

        @Test
        @DisplayName("the cache agrees with the database after the race")
        void cacheAgreesWithStore() throws Exception {
            CompletableFuture<QuestState> pending = new CompletableFuture<>();
            heldRead = pending;
            quest.preloadStateForPlayer(playerId);

            heldRead = null;
            quest.advanceStateForPlayer(playerId, QuestState.QUEST_ACTIVE).get(5, TimeUnit.SECONDS);
            pending.complete(QuestState.NOT_STARTED);

            // The original defect's signature: these two disagreeing, with only the cache wrong.
            assertEquals(store.get(playerId), quest.getStateForPlayer(player),
                "cache and database must not diverge - that divergence is what made the console lie");
        }

        @Test
        @DisplayName("a lazy load landing after an advance must not overwrite it either")
        void lazyLoadMustNotClobberAdvance() throws Exception {
            // getStateForPlayer(Player) on a cache miss issues the same shape of read.
            CompletableFuture<QuestState> pending = new CompletableFuture<>();
            heldRead = pending;
            assertEquals(QuestState.NOT_STARTED, quest.getStateForPlayer(player),
                "a cache miss returns the NOT_STARTED default while the load is in flight");

            heldRead = null;
            quest.advanceStateForPlayer(playerId, QuestState.TRIGGER_FOUND).get(5, TimeUnit.SECONDS);
            pending.complete(QuestState.NOT_STARTED);

            assertEquals(QuestState.TRIGGER_FOUND, quest.getStateForPlayer(player),
                "the stale lazy load must not overwrite the committed advance (#2094)");
        }
    }

    @Nested
    @DisplayName("Populating reads still do their job")
    class StillPopulates {

        @Test
        @DisplayName("a preload fills an empty cache")
        void preloadFillsEmptyCache() {
            store.put(playerId, QuestState.OBJECTIVE_FOUND);
            assertFalse(quest.isStateCached(player), "precondition: nothing cached yet");

            quest.preloadStateForPlayer(playerId);

            assertTrue(quest.isStateCached(player), "preload must populate an absent entry");
            assertEquals(QuestState.OBJECTIVE_FOUND, quest.getStateForPlayer(player));
        }

        @Test
        @DisplayName("eviction then preload picks up the new value - the refresh path")
        void evictThenPreloadRefreshes() {
            store.put(playerId, QuestState.TRIGGER_FOUND);
            quest.preloadStateForPlayer(playerId);
            assertEquals(QuestState.TRIGGER_FOUND, quest.getStateForPlayer(player));

            // Something outside this instance changes the row, as a reset or another server would.
            store.put(playerId, QuestState.COMPLETED);

            // putIfAbsent means a re-preload alone will NOT pick it up...
            quest.preloadStateForPlayer(playerId);
            assertEquals(QuestState.TRIGGER_FOUND, quest.getStateForPlayer(player),
                "a populating read never overwrites an occupied slot, by design");

            // ...eviction is the refresh mechanism, which is why quit/rejoin settled the original bug.
            quest.evictStateForPlayer(playerId);
            quest.preloadStateForPlayer(playerId);
            assertEquals(QuestState.COMPLETED, quest.getStateForPlayer(player),
                "evict then preload is how a cache is legitimately refreshed");
        }
    }
}
