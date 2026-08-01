package org.fourz.RVNKQuests.quest;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.config.ConfigManager;
import org.fourz.RVNKQuests.service.IJournalService;
import org.fourz.RVNKQuests.service.INotificationService;
import org.fourz.RVNKQuests.service.IQuestProgressService;
import org.fourz.RVNKQuests.data.dto.QuestProgressDTO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression test for #1137: rewards/notifications must fire when advanceStateForPlayer
 * reaches COMPLETED, regardless of whether complete(Player) was called directly.
 *
 * <p>Before the fix, onComplete() only ran if complete(Player) was called explicitly.
 * Trigger components call advanceStateForPlayer(uuid, COMPLETED) directly, so rewards
 * were silently skipped for all data-driven quest completions.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Quest Completion Dispatch Tests")
class QuestCompletionDispatchTest {

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
    private AtomicInteger onCompleteCallCount;
    private AbstractQuest quest;

    @BeforeEach
    void setUp() {
        playerId = UUID.randomUUID();

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
        // performAdvance() marshals all Bukkit work through the scheduler; run it inline
        // so assertions see the side effects without a real server tick.
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(any(org.bukkit.plugin.Plugin.class), any(Runnable.class)))
            .thenAnswer(invocation -> {
                invocation.getArgument(1, Runnable.class).run();
                return null;
            });
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("test_player");

        // Wire Bukkit static singleton so callEvent() doesn't NPE
        try {
            Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, server);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not set Bukkit.server mock", e);
        }

        // progressService returns NOT_STARTED → COMPLETED transition
        when(progressService.getQuestState(eq(playerId), anyString()))
            .thenReturn(CompletableFuture.completedFuture(QuestState.OBJECTIVE_FOUND));
        when(progressService.updateQuestState(eq(playerId), anyString(), any(QuestState.class)))
            .thenReturn(CompletableFuture.completedFuture(
                new QuestProgressDTO(playerId, "test_quest", QuestState.COMPLETED, null, null, null, null)));
        doNothing().when(questManager).updateQuestListenersForPlayer(any(), eq(playerId));

        onCompleteCallCount = new AtomicInteger(0);

        quest = new AbstractQuest(plugin, "test_quest", "Test Quest") {
            @Override protected boolean onStart(Player p) { return true; }
            @Override protected boolean onComplete(Player p) {
                onCompleteCallCount.incrementAndGet();
                return true;
            }
            @Override public boolean update(Player p) { return true; }
            @Override public void initialize() {}
            @Override public void cleanup() {}
            @Override public java.util.List<org.bukkit.event.Listener> createListenersForState(QuestState s) {
                return java.util.List.of();
            }
            @Override public org.bukkit.Location getStartLocation() { return null; }
            @Override public String getStartTrigger() { return "test"; }
        };
    }

    @Test
    @DisplayName("onComplete fires when advanceStateForPlayer reaches COMPLETED — regression for #1137")
    void onCompleteFiresOnAdvanceToCompleted() throws Exception {
        quest.advanceStateForPlayer(playerId, QuestState.COMPLETED).get();

        assertEquals(1, onCompleteCallCount.get(),
            "onComplete() must fire exactly once when advanceStateForPlayer(COMPLETED) is called — " +
            "trigger components use this path directly (regression: #1137)");
    }

    @Test
    @DisplayName("onComplete does NOT fire when advancing to non-COMPLETED states")
    void onCompleteDoesNotFireForIntermediateStates() throws Exception {
        when(progressService.getQuestState(eq(playerId), anyString()))
            .thenReturn(CompletableFuture.completedFuture(QuestState.NOT_STARTED));

        quest.advanceStateForPlayer(playerId, QuestState.QUEST_ACTIVE).get();
        quest.advanceStateForPlayer(playerId, QuestState.OBJECTIVE_FOUND).get();

        assertEquals(0, onCompleteCallCount.get(),
            "onComplete() must not fire for intermediate state transitions");
    }

    @Test
    @DisplayName("onComplete fires exactly once even if complete(Player) and advanceStateForPlayer both reach COMPLETED")
    void onCompleteFiresOnceViaTriggerPath() throws Exception {
        // Simulate trigger component calling advanceStateForPlayer directly (the only path)
        quest.advanceStateForPlayer(playerId, QuestState.COMPLETED).get();

        assertEquals(1, onCompleteCallCount.get(),
            "Reward delivery must fire exactly once via the advanceStateForPlayer path");
    }
}
