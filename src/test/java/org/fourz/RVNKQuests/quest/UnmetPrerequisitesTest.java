package org.fourz.RVNKQuests.quest;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.config.ConfigManager;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@code getUnmetPrerequisites} — the query behind {@code /quest state}'s refusal (#1884).
 *
 * <p>The decided pairing is: {@code /quest state} refuses a mid-chain state and names the blocker;
 * {@code /quest debug setstate} honours the bypass it advertises. That makes the <b>identity</b> of
 * the blocking quest load-bearing, not just its existence — a refusal that cannot say which quest is
 * in the way is barely better than the silent no-op this issue started as.</p>
 *
 * <p>Ordering matters for the same reason. The message renders the list directly, so a
 * declaration-ordered answer is reproducible while a race-ordered one changes between runs.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Unmet prerequisites (#1884)")
class UnmetPrerequisitesTest {

    @Mock private RVNKQuests plugin;
    @Mock private IQuestProgressService progressService;
    @Mock private IJournalService journalService;
    @Mock private INotificationService notifService;
    @Mock private QuestManager questManager;
    @Mock private ConfigManager configManager;
    @Mock private Server server;
    @Mock private org.bukkit.configuration.file.FileConfiguration config;
    @Mock private org.bukkit.plugin.PluginManager pluginManager;
    @Mock private org.bukkit.scheduler.BukkitScheduler scheduler;

    private UUID playerId;

    /** Per-quest state for the subject player. Absent means NOT_STARTED. */
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
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getScheduler()).thenReturn(scheduler);
        // Run scheduled main-thread work inline, so a completion's side-effects are observable
        // within the test rather than dropped on the floor.
        when(scheduler.runTask(any(org.bukkit.plugin.Plugin.class), any(Runnable.class)))
            .thenAnswer(inv -> { inv.getArgument(1, Runnable.class).run(); return null; });

        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);

        when(progressService.getQuestState(any(UUID.class), anyString()))
            .thenAnswer(inv -> CompletableFuture.completedFuture(
                store.getOrDefault(inv.getArgument(1, String.class), QuestState.NOT_STARTED)));
    }

    /** A quest declaring the given prerequisite ids. */
    private AbstractQuest questWithPrereqs(List<String> prereqs) {
        return new AbstractQuest(plugin, "subject_quest", "Subject Quest") {
            @Override protected List<String> getPrerequisiteQuestIds() { return prereqs; }
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

    private List<String> unmetOf(AbstractQuest quest) {
        return quest.getUnmetPrerequisites(playerId).join();
    }

    @Nested
    @DisplayName("which quests are reported")
    class Reporting {

        @Test
        @DisplayName("a quest with no prerequisites reports nothing blocking")
        void noPrereqsIsEmpty() {
            assertTrue(unmetOf(questWithPrereqs(List.of())).isEmpty());
        }

        @Test
        @DisplayName("a null prerequisite list is treated as none, not as an error")
        void nullPrereqsIsEmpty() {
            assertTrue(unmetOf(questWithPrereqs(null)).isEmpty());
        }

        @Test
        @DisplayName("an unmet prerequisite is named")
        void unmetIsNamed() {
            assertEquals(List.of("tfah_ch1_journey"),
                unmetOf(questWithPrereqs(List.of("tfah_ch1_journey"))));
        }

        @Test
        @DisplayName("a COMPLETED prerequisite blocks nothing")
        void completedIsNotBlocking() {
            store.put("tfah_ch1_journey", QuestState.COMPLETED);

            assertTrue(unmetOf(questWithPrereqs(List.of("tfah_ch1_journey"))).isEmpty());
        }

        @Test
        @DisplayName("a prerequisite in progress still blocks — only COMPLETED satisfies")
        void inProgressStillBlocks() {
            // The live repro: journey sat at TRIGGER_FOUND, not NOT_STARTED, and still blocked.
            store.put("tfah_ch1_journey", QuestState.TRIGGER_FOUND);

            assertEquals(List.of("tfah_ch1_journey"),
                unmetOf(questWithPrereqs(List.of("tfah_ch1_journey"))));
        }

        @Test
        @DisplayName("ABANDONED does not satisfy a prerequisite")
        void abandonedStillBlocks() {
            store.put("tfah_ch1_journey", QuestState.ABANDONED);

            assertFalse(unmetOf(questWithPrereqs(List.of("tfah_ch1_journey"))).isEmpty());
        }
    }

    @Nested
    @DisplayName("multiple prerequisites")
    class Multiple {

        @Test
        @DisplayName("only the unmet ones are reported, not all of them")
        void reportsOnlyBlockers() {
            store.put("first", QuestState.COMPLETED);
            store.put("third", QuestState.COMPLETED);

            assertEquals(List.of("second"),
                unmetOf(questWithPrereqs(List.of("first", "second", "third"))));
        }

        @Test
        @DisplayName("blockers keep declaration order, so the message is reproducible")
        void keepsDeclarationOrder() {
            // Evaluated sequentially for exactly this reason — a parallel scan would return
            // whichever future resolved first, and the refusal text would differ between runs.
            assertEquals(List.of("alpha", "beta", "gamma"),
                unmetOf(questWithPrereqs(List.of("alpha", "beta", "gamma"))));
        }

        @Test
        @DisplayName("all satisfied means nothing blocking")
        void allSatisfied() {
            store.put("alpha", QuestState.COMPLETED);
            store.put("beta", QuestState.COMPLETED);

            assertTrue(unmetOf(questWithPrereqs(List.of("alpha", "beta"))).isEmpty());
        }
    }

    @Nested
    @DisplayName("which states /quest state refuses")
    class MidChainClassification {

        @Test
        @DisplayName("progression states past the start are mid-chain")
        void progressionStatesAreMidChain() {
            assertTrue(AbstractQuest.isMidChainProgress(QuestState.TRIGGER_FOUND));
            assertTrue(AbstractQuest.isMidChainProgress(QuestState.QUEST_ACTIVE));
            assertTrue(AbstractQuest.isMidChainProgress(QuestState.OBJECTIVE_FOUND));
            assertTrue(AbstractQuest.isMidChainProgress(QuestState.COMPLETED));
        }

        @Test
        @DisplayName("NOT_STARTED is not mid-chain — teardown must never be refused")
        void resetIsNeverRefused() {
            // A QA pass that can set a state but not clear it is worse than one that can do
            // neither. Every session in this issue's history ended by resetting to NOT_STARTED.
            assertFalse(AbstractQuest.isMidChainProgress(QuestState.NOT_STARTED));
        }

        @Test
        @DisplayName("lifecycle states are not mid-chain progress")
        void lifecycleStatesAreNotProgress() {
            assertFalse(AbstractQuest.isMidChainProgress(QuestState.ABANDONED));
            assertFalse(AbstractQuest.isMidChainProgress(QuestState.PAUSED));
        }
    }

    @Nested
    @DisplayName("the debug bypass is unaffected")
    class BypassUnaffected {

        @Test
        @DisplayName("setStateForPlayer still applies a mid-chain state with the prereq unmet")
        void setStateStillBypasses() {
            // This is the other half of the pairing. /quest debug setstate routes here and must
            // keep working — it advertises "bypasses normal state transitions", and the model
            // layer is where that promise is kept. Only the command layer refuses.
            AbstractQuest quest = questWithPrereqs(List.of("tfah_ch1_journey"));
            when(progressService.updateQuestState(any(UUID.class), anyString(), any(QuestState.class)))
                .thenAnswer(inv -> {
                    store.put("subject_quest", inv.getArgument(2, QuestState.class));
                    return CompletableFuture.completedFuture(null);
                });

            quest.setStateForPlayer(playerId, QuestState.TRIGGER_FOUND).join();

            assertEquals(QuestState.TRIGGER_FOUND, store.get("subject_quest"),
                "the admin bypass must still apply — refusing here is what caused #1884");
        }
    }
}
