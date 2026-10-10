package org.fourz.RVNKQuests.npc;

import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.objective.generic.GenericTalkToObjective;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.trigger.generic.GenericNpcInteractTrigger;
import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent;
import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent.ClickType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.fourz.RVNKQuests.npc.NpcTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Dialogue context selection and the one-line-per-interaction rule (#2214).
 *
 * <p>Simulates Bukkit's dispatch: every component handler runs (NORMAL), then the coordinator
 * (MONITOR). Main-thread hops run inline.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NPC dialogue coordinator (#2214)")
class NpcInteractionCoordinatorTest {

    @Mock private RVNKQuests plugin;

    private Player player;
    private final List<NpcQuestComponent> components = new ArrayList<>();
    /** Lore entries by name: npc_<key>_<context>. */
    private final Map<String, String> lore = new HashMap<>();
    private final List<String> lookups = new ArrayList<>();
    private NpcInteractionCoordinator coordinator;

    @BeforeEach
    void setUp() {
        player = player("Tester");
        components.clear();
        lore.clear();
        lookups.clear();
        coordinator = new NpcInteractionCoordinator(
            () -> components,
            () -> null,
            (key, context) -> {
                String name = "npc_" + key + "_" + context;
                lookups.add(name);
                return CompletableFuture.completedFuture(Optional.ofNullable(lore.get(name)));
            },
            Runnable::run,
            Runnable::run,
            msg -> { },
            msg -> { });
        when(plugin.getNpcCoordinator()).thenReturn(coordinator);
    }

    private GenericNpcInteractTrigger trigger(DataDrivenQuest quest, String key, Object... kv) {
        Map<String, Object> config = config("type", "NPC_INTERACT", kv);
        config.put("npc_key", key);
        GenericNpcInteractTrigger t = new GenericNpcInteractTrigger(plugin, quest, config);
        components.add(t);
        return t;
    }

    private GenericTalkToObjective talkTo(DataDrivenQuest quest, String key, Object... kv) {
        Map<String, Object> config = config("objective_type", "TALK_TO", kv);
        config.put("npc_key", key);
        GenericTalkToObjective o = new GenericTalkToObjective(plugin, quest, config);
        components.add(o);
        return o;
    }

    /** Bukkit order: every component at NORMAL, then the coordinator at MONITOR. */
    private void dispatch(RvnkNpcInteractEvent event) {
        for (NpcQuestComponent c : components) {
            if (c instanceof GenericNpcInteractTrigger t) t.onNpcInteract(event);
            if (c instanceof GenericTalkToObjective o) o.onNpcInteract(event);
        }
        coordinator.onNpcInteract(event);
    }

    private List<String> sentLines() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(player, atLeast(0)).sendMessage(captor.capture());
        return captor.getAllValues();
    }

    private void withLoreForAllContexts(String key) {
        lore.put("npc_" + key + "_offer", key + " offers");
        lore.put("npc_" + key + "_active", key + " is waiting");
        lore.put("npc_" + key + "_done", key + " thanks you");
    }

    @Nested
    @DisplayName("context selection")
    class Contexts {

        @Test
        @DisplayName("a trigger that offers the quest sends the offer line")
        void triggerOffer() {
            AtomicReference<QuestState> s = new AtomicReference<>(QuestState.NOT_STARTED);
            trigger(quest("q1", s, true), "archivist", "advance_state", "QUEST_ACTIVE");
            withLoreForAllContexts("archivist");

            dispatch(rightClick(player, "archivist"));

            assertEquals(List.of("npc_archivist_offer"), lookups);
            assertEquals(1, sentLines().size());
            assertTrue(sentLines().get(0).contains("archivist offers"));
        }

        @Test
        @DisplayName("a TALK_TO that completes the quest sends the done line")
        void talkToDone() {
            AtomicReference<QuestState> s = new AtomicReference<>(QuestState.QUEST_ACTIVE);
            talkTo(quest("q1", s, true), "courier", "advance_state", "COMPLETED");
            withLoreForAllContexts("courier");

            dispatch(rightClick(player, "courier"));

            assertEquals(List.of("npc_courier_done"), lookups);
            assertEquals(1, sentLines().size());
        }

        @Test
        @DisplayName("a TALK_TO that advances without completing sends the active line")
        void talkToActive() {
            AtomicReference<QuestState> s = new AtomicReference<>(QuestState.QUEST_ACTIVE);
            talkTo(quest("q1", s, true), "courier");
            withLoreForAllContexts("courier");

            dispatch(rightClick(player, "courier"));

            assertEquals(List.of("npc_courier_active"), lookups);
        }

        @Test
        @DisplayName("no advance: a quest in progress with this NPC gives the active line")
        void fallbackActive() {
            AtomicReference<QuestState> s = new AtomicReference<>(QuestState.QUEST_ACTIVE);
            trigger(quest("q1", s, true), "archivist"); // NOT_STARTED gate refuses: player is past it
            withLoreForAllContexts("archivist");

            dispatch(rightClick(player, "archivist"));

            assertEquals(List.of("npc_archivist_active"), lookups);
            assertEquals(1, sentLines().size());
        }

        @Test
        @DisplayName("no advance: a completed quest with this NPC gives the done line")
        void fallbackDone() {
            AtomicReference<QuestState> s = new AtomicReference<>(QuestState.COMPLETED);
            trigger(quest("q1", s, true), "archivist");
            withLoreForAllContexts("archivist");

            dispatch(rightClick(player, "archivist"));

            assertEquals(List.of("npc_archivist_done"), lookups);
        }

        @Test
        @DisplayName("a TALK_TO NPC clicked before the quest starts says nothing (no spoiler)")
        void notStartedIsSilent() {
            AtomicReference<QuestState> s = new AtomicReference<>(QuestState.NOT_STARTED);
            talkTo(quest("q1", s, true), "courier", "advance_state", "COMPLETED");
            withLoreForAllContexts("courier");

            dispatch(rightClick(player, "courier"));

            assertTrue(lookups.isEmpty());
            assertTrue(sentLines().isEmpty());
        }

        @Test
        @DisplayName("an offer refused by the prerequisite gate is not announced")
        void refusedOfferIsSilent() {
            AtomicReference<QuestState> s = new AtomicReference<>(QuestState.NOT_STARTED);
            trigger(quest("q1", s, false), "archivist"); // advance does not commit
            withLoreForAllContexts("archivist");

            dispatch(rightClick(player, "archivist"));

            assertTrue(sentLines().isEmpty(), "a quest the player cannot take must not be advertised");
        }

        @Test
        @DisplayName("an NPC no quest references says nothing")
        void unrelatedNpcIsSilent() {
            AtomicReference<QuestState> s = new AtomicReference<>(QuestState.QUEST_ACTIVE);
            talkTo(quest("q1", s, true), "courier");
            withLoreForAllContexts("blacksmith");

            dispatch(rightClick(player, "blacksmith"));

            assertTrue(lookups.isEmpty());
        }

        @Test
        @DisplayName("the fallback respects each component's click filter")
        void fallbackRespectsClick() {
            AtomicReference<QuestState> s = new AtomicReference<>(QuestState.QUEST_ACTIVE);
            trigger(quest("q1", s, true), "archivist");
            withLoreForAllContexts("archivist");

            dispatch(click(player, "archivist", ClickType.LEFT));

            assertTrue(lookups.isEmpty());
        }
    }

    @Nested
    @DisplayName("one line per interaction")
    class OneLine {

        @Test
        @DisplayName("an NPC shared by three quests sends ONE line, with the highest context")
        void sharedNpcOneLine() {
            // q1: the archivist offers it. q2: talking to the archivist completes it. q3: the
            // player is mid-quest elsewhere with the archivist as a later beat.
            trigger(quest("q1", new AtomicReference<>(QuestState.NOT_STARTED), true), "archivist");
            talkTo(quest("q2", new AtomicReference<>(QuestState.QUEST_ACTIVE), true), "archivist",
                "advance_state", "COMPLETED");
            talkTo(quest("q3", new AtomicReference<>(QuestState.TRIGGER_FOUND), true), "archivist");
            withLoreForAllContexts("archivist");

            dispatch(rightClick(player, "archivist"));

            assertEquals(1, sentLines().size(), "one click, one line");
            assertEquals(List.of("npc_archivist_done"), lookups, "done outranks offer and active");
        }

        @Test
        @DisplayName("offer outranks active")
        void offerOutranksActive() {
            trigger(quest("q1", new AtomicReference<>(QuestState.NOT_STARTED), true), "archivist");
            talkTo(quest("q2", new AtomicReference<>(QuestState.QUEST_ACTIVE), true), "archivist");
            withLoreForAllContexts("archivist");

            dispatch(rightClick(player, "archivist"));

            assertEquals(List.of("npc_archivist_offer"), lookups);
            assertEquals(1, sentLines().size());
        }

        @Test
        @DisplayName("two clicks are two interactions: two lines, and no claims left behind")
        void claimsDoNotLeak() {
            AtomicReference<QuestState> s = new AtomicReference<>(QuestState.QUEST_ACTIVE);
            talkTo(quest("q1", s, true), "courier");
            withLoreForAllContexts("courier");

            dispatch(rightClick(player, "courier"));
            dispatch(rightClick(player, "courier"));

            assertEquals(2, sentLines().size());
            assertEquals(0, coordinator.pendingClicks());
        }

        @Test
        @DisplayName("a cancelled click sends no line and still clears its claims")
        void cancelledClick() {
            AtomicReference<QuestState> s = new AtomicReference<>(QuestState.QUEST_ACTIVE);
            GenericTalkToObjective o = talkTo(quest("q1", s, true), "courier");
            withLoreForAllContexts("courier");

            RvnkNpcInteractEvent event = rightClick(player, "courier");
            o.onNpcInteract(event);           // the component fired, then
            event.setCancelled(true);          // another plugin cancelled the click
            coordinator.onNpcInteract(event);

            assertTrue(sentLines().isEmpty());
            assertEquals(0, coordinator.pendingClicks());
        }
    }

    @Nested
    @DisplayName("silence when there is no line")
    class Silence {

        @Test
        @DisplayName("no lore entry: the quest still advances, nothing is said")
        void noEntry() {
            AtomicReference<QuestState> s = new AtomicReference<>(QuestState.QUEST_ACTIVE);
            talkTo(quest("q1", s, true), "courier", "advance_state", "COMPLETED");

            dispatch(rightClick(player, "courier"));

            assertEquals(QuestState.COMPLETED, s.get());
            assertTrue(sentLines().isEmpty());
        }

        @Test
        @DisplayName("RVNKLore absent (lookup always empty): the quest still advances")
        void noLorePlugin() {
            NpcInteractionCoordinator silent = new NpcInteractionCoordinator(() -> components, () -> null,
                (k, c) -> CompletableFuture.completedFuture(Optional.empty()),
                Runnable::run, Runnable::run, m -> { }, m -> { });
            when(plugin.getNpcCoordinator()).thenReturn(silent);
            AtomicReference<QuestState> s = new AtomicReference<>(QuestState.NOT_STARTED);
            GenericNpcInteractTrigger t = trigger(quest("q1", s, true), "archivist");

            RvnkNpcInteractEvent event = rightClick(player, "archivist");
            t.onNpcInteract(event);
            silent.onNpcInteract(event);

            assertEquals(QuestState.TRIGGER_FOUND, s.get());
            verify(player, never()).sendMessage(anyString());
        }

        @Test
        @DisplayName("a lookup that fails is silent, not an exception")
        void failedLookup() {
            NpcInteractionCoordinator failing = new NpcInteractionCoordinator(() -> components, () -> null,
                (k, c) -> CompletableFuture.failedFuture(new IllegalStateException("db down")),
                Runnable::run, Runnable::run, m -> { }, m -> { });
            AtomicReference<QuestState> s = new AtomicReference<>(QuestState.COMPLETED);
            trigger(quest("q1", s, true), "archivist");

            assertDoesNotThrow(() -> failing.onNpcInteract(rightClick(player, "archivist")));
            verify(player, never()).sendMessage(anyString());
        }
    }

    /** Makes the quest report unmet prerequisites, as getUnmetPrerequisites does for the gate. */
    private static DataDrivenQuest withUnmet(DataDrivenQuest quest, String... unmet) {
        when(quest.getUnmetPrerequisites(any(java.util.UUID.class)))
            .thenReturn(CompletableFuture.completedFuture(List.of(unmet)));
        return quest;
    }

    @Nested
    @DisplayName("locked context (#2249)")
    class Locked {

        private void withLockedLore(String key) {
            withLoreForAllContexts(key);
            lore.put("npc_" + key + "_locked", key + " is not ready for you");
        }

        @Test
        @DisplayName("NPC_INTERACT refused by an unmet prerequisite plays the locked line")
        void triggerRefusedByPrerequisite() {
            AtomicReference<QuestState> s = new AtomicReference<>(QuestState.NOT_STARTED);
            // commits=false: the prerequisite gate refuses NOT_STARTED -> TRIGGER_FOUND
            trigger(withUnmet(quest("cavern", s, false), "aether"), "guide");
            withLockedLore("guide");

            dispatch(rightClick(player, "guide"));

            assertEquals(QuestState.NOT_STARTED, s.get());
            assertEquals(List.of("npc_guide_locked"), lookups);
            assertEquals(1, sentLines().size());
            assertTrue(sentLines().get(0).contains("guide is not ready for you"));
        }

        @Test
        @DisplayName("NPC on a later edge (TRIGGER_FOUND -> QUEST_ACTIVE) of a NOT_STARTED gated quest is locked too")
        void laterEdgeOfGatedQuest() {
            // The cavern shape: a LOCATION_PROXIMITY trigger owns NOT_STARTED -> TRIGGER_FOUND and is
            // the gated edge; the NPC only acts on TRIGGER_FOUND -> QUEST_ACTIVE.
            AtomicReference<QuestState> s = new AtomicReference<>(QuestState.NOT_STARTED);
            trigger(withUnmet(quest("cavern", s, true), "aether"), "guide",
                "required_state", "TRIGGER_FOUND", "advance_state", "QUEST_ACTIVE");
            withLockedLore("guide");

            dispatch(rightClick(player, "guide"));

            assertEquals(QuestState.NOT_STARTED, s.get(), "the NPC's own gate did not fire");
            assertEquals(List.of("npc_guide_locked"), lookups);
            assertEquals(1, sentLines().size());
        }

        @Test
        @DisplayName("TALK_TO of a NOT_STARTED gated quest is locked as well")
        void talkToOfGatedQuest() {
            AtomicReference<QuestState> s = new AtomicReference<>(QuestState.NOT_STARTED);
            talkTo(withUnmet(quest("cavern", s, true), "aether"), "guide");
            withLockedLore("guide");

            dispatch(rightClick(player, "guide"));

            assertEquals(List.of("npc_guide_locked"), lookups);
            assertEquals(1, sentLines().size());
        }

        @Test
        @DisplayName("no npc_<key>_locked entry: silence, as before 1.1.68")
        void noLockedEntryIsSilent() {
            AtomicReference<QuestState> s = new AtomicReference<>(QuestState.NOT_STARTED);
            trigger(withUnmet(quest("cavern", s, false), "aether"), "guide");
            withLoreForAllContexts("guide"); // offer/active/done, but no locked line

            dispatch(rightClick(player, "guide"));

            assertEquals(List.of("npc_guide_locked"), lookups, "only the locked entry is looked up");
            assertTrue(sentLines().isEmpty(), "a quest with no locked line must not be advertised");
        }

        @Test
        @DisplayName("NOT_STARTED with no prerequisite gate: silence, no lookup, even with a locked entry")
        void notGatedIsSilent() {
            AtomicReference<QuestState> s = new AtomicReference<>(QuestState.NOT_STARTED);
            talkTo(withUnmet(quest("errand", s, true)), "courier", "advance_state", "COMPLETED");
            withLockedLore("courier");

            dispatch(rightClick(player, "courier"));

            assertTrue(lookups.isEmpty());
            assertTrue(sentLines().isEmpty());
        }

        @Test
        @DisplayName("prerequisite met: the trigger offers the quest, not the locked line")
        void prerequisiteMetOffers() {
            AtomicReference<QuestState> s = new AtomicReference<>(QuestState.NOT_STARTED);
            trigger(withUnmet(quest("cavern", s, true)), "guide");
            withLockedLore("guide");

            dispatch(rightClick(player, "guide"));

            assertEquals(List.of("npc_guide_offer"), lookups);
        }

        @Test
        @DisplayName("locked is lowest: another quest in progress with the NPC gives the active line")
        void activeOutranksLocked() {
            trigger(withUnmet(quest("cavern", new AtomicReference<>(QuestState.NOT_STARTED), false), "aether"), "guide");
            talkTo(quest("ruins", new AtomicReference<>(QuestState.TRIGGER_FOUND), true), "guide");
            withLockedLore("guide");

            dispatch(rightClick(player, "guide"));

            assertEquals(List.of("npc_guide_active"), lookups);
            assertEquals(1, sentLines().size());
        }

        @Test
        @DisplayName("locked is lowest: a completed quest with the NPC gives the done line")
        void doneOutranksLocked() {
            trigger(withUnmet(quest("cavern", new AtomicReference<>(QuestState.NOT_STARTED), false), "aether"), "guide");
            trigger(quest("aether", new AtomicReference<>(QuestState.COMPLETED), true), "guide");
            withLockedLore("guide");

            dispatch(rightClick(player, "guide"));

            assertEquals(List.of("npc_guide_done"), lookups);
        }

        @Test
        @DisplayName("locked is lowest: an offer from another quest wins")
        void offerOutranksLocked() {
            trigger(withUnmet(quest("cavern", new AtomicReference<>(QuestState.NOT_STARTED), false), "aether"), "guide");
            trigger(quest("aether", new AtomicReference<>(QuestState.NOT_STARTED), true), "guide");
            withLockedLore("guide");

            dispatch(rightClick(player, "guide"));

            assertEquals(List.of("npc_guide_offer"), lookups);
            assertEquals(1, sentLines().size());
        }

        @Test
        @DisplayName("a quest past NOT_STARTED is never locked, whatever its prerequisites read")
        void pastNotStartedIsNotLocked() {
            // e.g. an admin setstate moved the player mid-chain with the prerequisite unmet
            AtomicReference<QuestState> s = new AtomicReference<>(QuestState.TRIGGER_FOUND);
            talkTo(withUnmet(quest("cavern", s, true), "aether"), "guide",
                "required_state", "QUEST_ACTIVE");
            withLockedLore("guide");

            dispatch(rightClick(player, "guide"));

            assertEquals(List.of("npc_guide_active"), lookups);
        }

        @Test
        @DisplayName("the locked rule respects the component's click filter")
        void lockedRespectsClick() {
            trigger(withUnmet(quest("cavern", new AtomicReference<>(QuestState.NOT_STARTED), false), "aether"), "guide");
            withLockedLore("guide");

            dispatch(click(player, "guide", ClickType.LEFT));

            assertTrue(lookups.isEmpty());
        }

        @Test
        @DisplayName("a prerequisite read that fails is treated as not locked: silence")
        void failedPrerequisiteReadIsSilent() {
            DataDrivenQuest q = quest("cavern", new AtomicReference<>(QuestState.NOT_STARTED), false);
            when(q.getUnmetPrerequisites(any(java.util.UUID.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("db down")));
            trigger(q, "guide");
            withLockedLore("guide");

            assertDoesNotThrow(() -> dispatch(rightClick(player, "guide")));
            assertTrue(lookups.isEmpty());
            assertTrue(sentLines().isEmpty());
        }

        @Test
        @DisplayName("two gated quests on one NPC still send one line")
        void twoLockedQuestsOneLine() {
            trigger(withUnmet(quest("cavern", new AtomicReference<>(QuestState.NOT_STARTED), false), "aether"), "guide");
            talkTo(withUnmet(quest("depths", new AtomicReference<>(QuestState.NOT_STARTED), true), "cavern"), "guide");
            withLockedLore("guide");

            dispatch(rightClick(player, "guide"));

            assertEquals(1, sentLines().size());
            assertEquals(0, coordinator.pendingClicks());
        }

        @Test
        @DisplayName("resolveLocked is a pure read: it never advances a quest")
        void resolveLockedHasNoSideEffects() {
            DataDrivenQuest q = withUnmet(quest("cavern", new AtomicReference<>(QuestState.NOT_STARTED), true), "aether");
            trigger(q, "guide");

            assertTrue(coordinator.resolveLocked(player, "guide", ClickType.RIGHT).join());
            verify(q, never()).advanceStateForPlayer(any(java.util.UUID.class), any(QuestState.class));
            verify(q, never()).advanceStateForPlayer(any(java.util.UUID.class), any(QuestState.class), any());
            verify(q, never()).setStateForPlayer(any(java.util.UUID.class), any(QuestState.class));
        }
    }

    @Test
    @DisplayName("the line names the NPC and honours & colour codes")
    void formatLine() {
        String line = NpcInteractionCoordinator.formatLine("§6Archivist Voss", "archivist", "&oThe spores remember.");
        assertTrue(line.contains("Archivist Voss"));
        assertFalse(line.contains("&o"));
        assertTrue(line.contains("The spores remember."));
    }
}
