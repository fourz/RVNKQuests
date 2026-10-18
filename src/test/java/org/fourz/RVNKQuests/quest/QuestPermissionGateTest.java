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
 * Quest start requirement {@code required_permission} (#2353), and the start edge both gates cover.
 *
 * <p>A start is an automatic move out of NOT_STARTED to TRIGGER_FOUND (a trigger) or QUEST_ACTIVE
 * ({@code /quest start}, which every player may run). Before #2353 the QUEST_ACTIVE edge skipped the
 * prerequisite gate. Admin paths (setState, NOT_STARTED to COMPLETED) still bypass both.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Quest permission gate (#2353)")
class QuestPermissionGateTest {

    private static final String QUEST_ID = "tfah_worldforge_ledger";
    private static final String PERM = "rvnkquests.access.fragile_worlds";
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
    private List<String> prereqs = List.of();
    private String requiredPermission = PERM;
    private String refusal = null;
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
        when(player.hasPermission(PERM)).thenReturn(false);

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
            @Override protected List<String> getPrerequisiteQuestIds() { return prereqs; }
            @Override protected String getRequiredPermission() { return requiredPermission; }
            @Override protected String getRequiredPermissionMessage() { return refusal; }
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
        return quest.tryAdvanceStateForPlayer(playerId, target, null).get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("without the permission: a trigger start is refused, nothing is written, the refusal is spoken")
    void triggerStartRefused() throws Exception {
        assertFalse(tryAdvance(QuestState.TRIGGER_FOUND));
        assertNull(store.get(QUEST_ID));
        verify(player).sendMessage(contains("This path is not open to you yet."));
    }

    @Test
    @DisplayName("without the permission: /quest start (NOT_STARTED -> QUEST_ACTIVE) is refused too")
    void commandStartRefused() throws Exception {
        assertFalse(tryAdvance(QuestState.QUEST_ACTIVE));
        assertNull(store.get(QUEST_ID));
    }

    @Test
    @DisplayName("with the permission: the start commits")
    void permittedStartCommits() throws Exception {
        when(player.hasPermission(PERM)).thenReturn(true);
        assertTrue(tryAdvance(QuestState.TRIGGER_FOUND));
        assertEquals(QuestState.TRIGGER_FOUND, store.get(QUEST_ID));
        verify(player, never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("custom refusal text, legacy colours translated")
    void customRefusal() throws Exception {
        refusal = "&e[Warden Tolla] &fThe ledger is for veterans.";
        assertFalse(tryAdvance(QuestState.TRIGGER_FOUND));
        verify(player).sendMessage("\u00a7e[Warden Tolla] \u00a7fThe ledger is for veterans.");
    }

    @Test
    @DisplayName("refusal is sent once per cooldown window, not on every proximity fire")
    void refusalCooldown() throws Exception {
        assertFalse(tryAdvance(QuestState.TRIGGER_FOUND));
        assertFalse(tryAdvance(QuestState.TRIGGER_FOUND));
        assertFalse(tryAdvance(QuestState.QUEST_ACTIVE));
        verify(player, times(1)).sendMessage(anyString());
    }

    @Test
    @DisplayName("an offline player cannot be checked, so a gated start is refused")
    void offlineRefused() throws Exception {
        when(server.getPlayer(playerId)).thenReturn(null);
        assertFalse(tryAdvance(QuestState.TRIGGER_FOUND));
        assertNull(store.get(QUEST_ID));
    }

    @Test
    @DisplayName("no requirement: behaviour unchanged")
    void noRequirement() throws Exception {
        requiredPermission = null;
        assertTrue(tryAdvance(QuestState.QUEST_ACTIVE));
        verify(player, never()).hasPermission(anyString());
    }

    @Test
    @DisplayName("prerequisites now also gate /quest start (QUEST_ACTIVE edge)")
    void prerequisitesGateCommandStart() throws Exception {
        requiredPermission = null;
        prereqs = List.of(PREREQ_ID);
        assertFalse(tryAdvance(QuestState.QUEST_ACTIVE), "an unmet prerequisite must block /quest start");
        assertNull(store.get(QUEST_ID));
        store.put(PREREQ_ID, QuestState.COMPLETED);
        assertTrue(tryAdvance(QuestState.QUEST_ACTIVE));
    }

    @Test
    @DisplayName("prerequisites are checked first: an unearned quest stays silent, no permission refusal")
    void unmetPrerequisitesStaySilent() throws Exception {
        prereqs = List.of(PREREQ_ID);
        assertFalse(tryAdvance(QuestState.TRIGGER_FOUND));
        verify(player, never()).sendMessage(anyString());
        store.put(PREREQ_ID, QuestState.COMPLETED);
        assertFalse(tryAdvance(QuestState.TRIGGER_FOUND), "prerequisites met, permission still missing");
        verify(player).sendMessage(contains("This path is not open to you yet."));
    }

    @Test
    @DisplayName("admin paths bypass: setState and NOT_STARTED -> COMPLETED")
    void adminBypass() throws Exception {
        prereqs = List.of(PREREQ_ID);
        quest.setStateForPlayer(playerId, QuestState.QUEST_ACTIVE).get(5, TimeUnit.SECONDS);
        assertEquals(QuestState.QUEST_ACTIVE, store.get(QUEST_ID));
        store.remove(QUEST_ID);
        assertTrue(tryAdvance(QuestState.COMPLETED), "/quest complete is an admin path");
        assertEquals(1, onCompleteCalls.get());
    }
}
