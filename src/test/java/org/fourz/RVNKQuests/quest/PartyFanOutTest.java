package org.fourz.RVNKQuests.quest;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.config.ConfigManager;
import org.fourz.RVNKQuests.data.dto.QuestProgressDTO;
import org.fourz.RVNKQuests.party.PartyBeatContext;
import org.fourz.RVNKQuests.party.QuestPartyService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Party fan-out at the {@code AbstractQuest} choke point (#1982).
 *
 * <p>Pattern follows {@link QuestStateRaceTest}: mocked services, a real read-write backing store
 * so wrong outcomes are observable, and an anonymous {@code AbstractQuest}. The party service is
 * mocked — its own logic is covered by {@code QuestPartyServiceTest}; here we only care what the
 * fan-out does with its answers.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Party fan-out (#1982)")
class PartyFanOutTest {

    @Mock private RVNKQuests plugin;
    @Mock private IQuestProgressService progressService;
    @Mock private IJournalService journalService;
    @Mock private INotificationService notifService;
    @Mock private QuestManager questManager;
    @Mock private ConfigManager configManager;
    @Mock private QuestPartyService partyService;
    @Mock private Server server;
    @Mock private PluginManager pluginManager;
    @Mock private org.bukkit.configuration.file.FileConfiguration config;
    @Mock private org.bukkit.scheduler.BukkitScheduler scheduler;

    private UUID firerId;
    private UUID memberId;
    private Player firer;
    private Player member;
    private AbstractQuest quest;

    /** Persisted state per player — a real store so stale reads surface as wrong outcomes. */
    private final Map<UUID, QuestState> store = new ConcurrentHashMap<>();

    private static final PartyBeatContext CTX =
        new PartyBeatContext("alphac", 0, 64, 0, 20.0, QuestState.NOT_STARTED);

    @BeforeEach
    void setUp() throws Exception {
        firerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        store.clear();
        store.put(firerId, QuestState.NOT_STARTED);
        store.put(memberId, QuestState.NOT_STARTED);

        when(plugin.getQuestProgressService()).thenReturn(progressService);
        when(plugin.getJournalService()).thenReturn(journalService);
        when(plugin.getNotificationService()).thenReturn(notifService);
        when(plugin.getQuestManager()).thenReturn(questManager);
        when(plugin.getConfigManager()).thenReturn(configManager);
        when(plugin.getQuestPartyService()).thenReturn(partyService);
        when(plugin.getServer()).thenReturn(server);
        when(configManager.getConfig()).thenReturn(config);
        when(config.getBoolean(eq("quests.announce_completion"), anyBoolean())).thenReturn(false);
        when(journalService.isAvailable()).thenReturn(false);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(any(org.bukkit.plugin.Plugin.class), any(Runnable.class)))
            .thenAnswer(inv -> { inv.getArgument(1, Runnable.class).run(); return null; });

        firer = mockPlayer(firerId, "Firer");
        member = mockPlayer(memberId, "Member");

        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);

        when(progressService.getQuestState(any(UUID.class), anyString()))
            .thenAnswer(inv -> CompletableFuture.completedFuture(
                store.getOrDefault(inv.getArgument(0, UUID.class), QuestState.NOT_STARTED)));
        when(progressService.updateQuestState(any(UUID.class), anyString(), any(QuestState.class)))
            .thenAnswer(inv -> {
                UUID who = inv.getArgument(0);
                QuestState newState = inv.getArgument(2);
                store.put(who, newState);
                return CompletableFuture.completedFuture(
                    new QuestProgressDTO(who, "party_quest", newState, null, null, null, null));
            });
        doNothing().when(questManager).updateQuestListenersForPlayer(any(), any(UUID.class));

        when(partyService.isEnabled()).thenReturn(true);

        quest = newQuest(List.of());
    }

    private Player mockPlayer(UUID id, String name) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(id);
        when(p.getName()).thenReturn(name);
        when(server.getPlayer(id)).thenReturn(p);
        return p;
    }

    private AbstractQuest newQuest(List<String> prereqs) {
        return new AbstractQuest(plugin, "party_quest", "Party Quest") {
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
            @Override protected List<String> getPrerequisiteQuestIds() { return prereqs; }
        };
    }

    private void await(CompletableFuture<Void> f) throws Exception {
        f.get(5, TimeUnit.SECONDS);
        // Member advances are enqueued as siblings, not chained onto the firer's future —
        // drain them by pushing a no-op through each write chain.
        quest.advanceStateForPlayer(memberId, store.getOrDefault(memberId, QuestState.NOT_STARTED))
            .get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("firer + qualifying member both advance through their own paths")
    void qualifyingMemberAdvances() throws Exception {
        when(partyService.qualifyingMembers(eq(firerId), any())).thenReturn(List.of(memberId));

        await(quest.advanceStateForPlayer(firerId, QuestState.TRIGGER_FOUND, CTX));

        assertEquals(QuestState.TRIGGER_FOUND, store.get(firerId));
        assertEquals(QuestState.TRIGGER_FOUND, store.get(memberId));
        verify(progressService).updateQuestState(eq(firerId), eq("party_quest"), eq(QuestState.TRIGGER_FOUND));
        verify(progressService).updateQuestState(eq(memberId), eq("party_quest"), eq(QuestState.TRIGGER_FOUND));
    }

    @Test
    @DisplayName("non-qualifying member is untouched")
    void nonQualifyingMemberUntouched() throws Exception {
        when(partyService.qualifyingMembers(eq(firerId), any())).thenReturn(List.of());

        await(quest.advanceStateForPlayer(firerId, QuestState.TRIGGER_FOUND, CTX));

        assertEquals(QuestState.TRIGGER_FOUND, store.get(firerId));
        assertEquals(QuestState.NOT_STARTED, store.get(memberId));
    }

    @Test
    @DisplayName("in-step gate: a member behind the beat does not leapfrog")
    void inStepGateBlocksLeapfrog() throws Exception {
        // Beat expects QUEST_ACTIVE; the member is still NOT_STARTED.
        store.put(firerId, QuestState.QUEST_ACTIVE);
        PartyBeatContext midBeat = new PartyBeatContext("alphac", 0, 64, 0, 20.0, QuestState.QUEST_ACTIVE);
        when(partyService.qualifyingMembers(eq(firerId), any())).thenReturn(List.of(memberId));

        await(quest.advanceStateForPlayer(firerId, QuestState.OBJECTIVE_FOUND, midBeat));

        assertEquals(QuestState.OBJECTIVE_FOUND, store.get(firerId));
        assertEquals(QuestState.NOT_STARTED, store.get(memberId), "member behind the beat stays put");
    }

    @Test
    @DisplayName("member ahead of the beat is not regressed")
    void aheadMemberNotRegressed() throws Exception {
        store.put(memberId, QuestState.COMPLETED);
        when(partyService.qualifyingMembers(eq(firerId), any())).thenReturn(List.of(memberId));

        await(quest.advanceStateForPlayer(firerId, QuestState.TRIGGER_FOUND, CTX));

        assertEquals(QuestState.COMPLETED, store.get(memberId));
    }

    @Test
    @DisplayName("prereq-blocked member: no advance, visible feedback sent")
    void prereqBlockedMemberGetsFeedback() throws Exception {
        quest = newQuest(List.of("gate_quest"));
        // Firer completed the prereq; member did not.
        when(progressService.getQuestState(eq(firerId), eq("gate_quest")))
            .thenReturn(CompletableFuture.completedFuture(QuestState.COMPLETED));
        when(progressService.getQuestState(eq(memberId), eq("gate_quest")))
            .thenReturn(CompletableFuture.completedFuture(QuestState.NOT_STARTED));
        when(partyService.qualifyingMembers(eq(firerId), any())).thenReturn(List.of(memberId));

        await(quest.advanceStateForPlayer(firerId, QuestState.TRIGGER_FOUND, CTX));

        assertEquals(QuestState.TRIGGER_FOUND, store.get(firerId));
        assertEquals(QuestState.NOT_STARTED, store.get(memberId), "prereq gate holds per player");
        verify(member, atLeastOnce()).sendMessage(anyString());
        verify(firer, never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("no recursion: a fan-out consults the party service exactly once")
    void noRecursion() throws Exception {
        when(partyService.qualifyingMembers(eq(firerId), any())).thenReturn(List.of(memberId));

        await(quest.advanceStateForPlayer(firerId, QuestState.TRIGGER_FOUND, CTX));

        verify(partyService, times(1)).qualifyingMembers(any(UUID.class), any());
    }

    @Test
    @DisplayName("null ctx and disabled service are pure legacy behavior")
    void legacyPathsUnchanged() throws Exception {
        // Null ctx: never consults the party service.
        await(quest.advanceStateForPlayer(firerId, QuestState.TRIGGER_FOUND, null));
        verify(partyService, never()).qualifyingMembers(any(), any());
        assertEquals(QuestState.NOT_STARTED, store.get(memberId));

        // Disabled service: consulted for isEnabled only.
        when(partyService.isEnabled()).thenReturn(false);
        store.put(firerId, QuestState.NOT_STARTED);
        await(quest.advanceStateForPlayer(firerId, QuestState.TRIGGER_FOUND, CTX));
        verify(partyService, never()).qualifyingMembers(any(), any());
    }
}
