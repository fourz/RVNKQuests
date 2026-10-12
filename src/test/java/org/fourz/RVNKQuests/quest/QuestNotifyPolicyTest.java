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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.HashMap;
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
 * Per-quest notification control (#2266): the {@code notify} metadata block. The defaults must be
 * exactly the pre-1.1.69 behaviour.
 */
@DisplayName("Quest notify policy (#2266)")
class QuestNotifyPolicyTest {

    private static Map<String, Object> meta(Object notify) {
        Map<String, Object> m = new HashMap<>();
        m.put("notify", notify);
        return m;
    }

    @Test
    @DisplayName("no metadata, no notify block, or a non-map block: everything on, as before")
    void defaultsAreTodaysBehaviour() {
        assertEquals(QuestNotifyPolicy.DEFAULT, QuestNotifyPolicy.fromMetadata(null));
        assertEquals(QuestNotifyPolicy.DEFAULT, QuestNotifyPolicy.fromMetadata(Map.of()));
        assertEquals(QuestNotifyPolicy.DEFAULT, QuestNotifyPolicy.fromMetadata(Map.of("chain", "x")));
        assertEquals(QuestNotifyPolicy.DEFAULT, QuestNotifyPolicy.fromMetadata(meta("off")));
        assertEquals(QuestNotifyPolicy.DEFAULT, QuestNotifyPolicy.fromMetadata(meta(Map.of())));
        assertTrue(QuestNotifyPolicy.DEFAULT.startPopup());
        assertTrue(QuestNotifyPolicy.DEFAULT.completePopup());
        assertTrue(QuestNotifyPolicy.DEFAULT.broadcast());
    }

    @Test
    @DisplayName("each key is read on its own; a missing key stays on")
    void keysAreIndependent() {
        assertEquals(new QuestNotifyPolicy(true, true, false),
                QuestNotifyPolicy.fromMetadata(meta(Map.of("broadcast", false))));
        assertEquals(new QuestNotifyPolicy(false, true, true),
                QuestNotifyPolicy.fromMetadata(meta(Map.of("start_popup", false))));
        assertEquals(new QuestNotifyPolicy(true, false, true),
                QuestNotifyPolicy.fromMetadata(meta(Map.of("complete_popup", "false"))));
        assertEquals(new QuestNotifyPolicy(false, false, false),
                QuestNotifyPolicy.fromMetadata(meta(Map.of(
                        "start_popup", false, "complete_popup", false, "broadcast", "FALSE"))));
    }

    @Test
    @DisplayName("a value that is not a boolean keeps the default and is reported")
    void badValueKeepsDefault() {
        Map<String, Object> m = meta(Map.of("broadcast", "maybe", "popup", false));
        assertEquals(QuestNotifyPolicy.DEFAULT, QuestNotifyPolicy.fromMetadata(m));
        List<String> problems = QuestNotifyPolicy.problems(m);
        assertEquals(2, problems.size(), problems.toString());
        assertTrue(QuestNotifyPolicy.problems(Map.of()).isEmpty());
        assertTrue(QuestNotifyPolicy.problems(meta(Map.of("broadcast", true))).isEmpty());
    }

    @Test
    @DisplayName("the global announce_completion stays the master switch: both must be true")
    void broadcastNeedsBoth() {
        assertTrue(QuestNotifyPolicy.DEFAULT.shouldBroadcast(true));
        assertFalse(QuestNotifyPolicy.DEFAULT.shouldBroadcast(false));
        QuestNotifyPolicy muted = new QuestNotifyPolicy(true, true, false);
        assertFalse(muted.shouldBroadcast(true));
        assertFalse(muted.shouldBroadcast(false));
    }

    /** Drives the real AbstractQuest completion and start paths. */
    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    @DisplayName("dispatch in AbstractQuest")
    class Dispatch {

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
            when(configManager.getConfig()).thenReturn(config);
            when(journalService.isAvailable()).thenReturn(false);
            when(server.getPlayer(playerId)).thenReturn(player);
            when(server.getPluginManager()).thenReturn(pluginManager);
            when(server.getScheduler()).thenReturn(scheduler);
            when(scheduler.runTask(any(org.bukkit.plugin.Plugin.class), any(Runnable.class)))
                .thenAnswer(inv -> { inv.getArgument(1, Runnable.class).run(); return null; });
            when(player.getUniqueId()).thenReturn(playerId);
            when(player.getName()).thenReturn("wizardofire");

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

        private AbstractQuest quest(QuestNotifyPolicy policy) {
            return new AbstractQuest(plugin, "tfah_spire", "Filed as Collapsed") {
                @Override public QuestNotifyPolicy getNotifyPolicy() {
                    return policy != null ? policy : super.getNotifyPolicy();
                }
                @Override protected boolean onStart(Player p) { return true; }
                @Override protected boolean onComplete(Player p) { return true; }
                @Override public boolean update(Player p) { return true; }
                @Override public void initialize() {}
                @Override public void cleanup() {}
                @Override public List<org.bukkit.event.Listener> createListenersForState(QuestState s) { return List.of(); }
                @Override public org.bukkit.Location getStartLocation() { return null; }
                @Override public String getStartTrigger() { return "test"; }
            };
        }

        private void complete(AbstractQuest q, boolean globalAnnounce) throws Exception {
            when(config.getBoolean(eq("quests.announce_completion"), anyBoolean())).thenReturn(globalAnnounce);
            store.put("tfah_spire", QuestState.OBJECTIVE_FOUND);
            q.advanceStateForPlayer(playerId, QuestState.COMPLETED).get(5, TimeUnit.SECONDS);
        }

        @Test
        @DisplayName("no notify block + announce on: popup once and broadcast once, as before 1.1.69")
        void defaultCompletionIsUnchanged() throws Exception {
            complete(quest(null), true);
            verify(notifService, times(1)).notifyQuestComplete(player, "Filed as Collapsed");
            verify(server, times(1)).broadcastMessage(contains("has completed the quest"));
        }

        @Test
        @DisplayName("no notify block + announce off: popup, no broadcast, as before 1.1.69")
        void defaultWithGlobalOff() throws Exception {
            complete(quest(null), false);
            verify(notifService, times(1)).notifyQuestComplete(player, "Filed as Collapsed");
            verify(server, never()).broadcastMessage(anyString());
        }

        @Test
        @DisplayName("notify.broadcast false mutes the server broadcast even with announce on")
        void questMutesBroadcast() throws Exception {
            complete(quest(new QuestNotifyPolicy(true, true, false)), true);
            verify(server, never()).broadcastMessage(anyString());
            verify(notifService, times(1)).notifyQuestComplete(player, "Filed as Collapsed");
        }

        @Test
        @DisplayName("notify.complete_popup false mutes the popup, broadcast still follows the switches")
        void questMutesCompletePopup() throws Exception {
            complete(quest(new QuestNotifyPolicy(true, false, true)), true);
            verify(notifService, never()).notifyQuestComplete(any(), anyString());
            verify(player, never()).sendMessage(anyString());
            verify(server, times(1)).broadcastMessage(anyString());
        }

        @Test
        @DisplayName("start(): popup by default, none with notify.start_popup false")
        void startPopup() throws Exception {
            assertTrue(quest(null).start(player).get(5, TimeUnit.SECONDS));
            verify(notifService, times(1)).notifyQuestStart(player, "Filed as Collapsed", null);

            store.clear();
            clearInvocations(notifService);
            assertTrue(quest(new QuestNotifyPolicy(false, true, true)).start(player).get(5, TimeUnit.SECONDS));
            verify(notifService, never()).notifyQuestStart(any(), anyString(), any());
        }

        @Test
        @DisplayName("the player's prefs still win: the policy only decides whether the service is asked")
        void prefsStillApplyInsideTheService() throws Exception {
            // The service owns the prefs check; with the policy on, the service is called and
            // makes that decision itself. This pins that the policy never bypasses the service.
            complete(quest(QuestNotifyPolicy.DEFAULT), false);
            verify(notifService).notifyQuestComplete(player, "Filed as Collapsed");
            verify(player, never()).sendMessage(anyString());
        }
    }
}
