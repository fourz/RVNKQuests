package org.fourz.RVNKQuests.objective.generic;

import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.npc.NpcTestSupport;
import org.fourz.RVNKQuests.party.PartyBeatContext;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent.ClickType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.fourz.RVNKQuests.npc.NpcTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** TALK_TO completes on the right key only, through the normal state advance (#2214). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TALK_TO objective (#2214)")
class TalkToObjectiveTest {

    @Mock private RVNKQuests plugin;

    private Player player;
    private AtomicReference<QuestState> state;
    private DataDrivenQuest quest;

    @BeforeEach
    void setUp() {
        player = player("Tester");
        state = new AtomicReference<>(QuestState.QUEST_ACTIVE);
        quest = NpcTestSupport.quest("npc_demo", state, true);
    }

    private GenericTalkToObjective talkTo(Object... kv) {
        return new GenericTalkToObjective(plugin, quest, config("objective_type", "TALK_TO", kv));
    }

    private void assertNotAdvanced() {
        verify(quest, never()).advanceStateForPlayer(any(UUID.class), any(QuestState.class), any());
        verify(quest, never()).advanceStateForPlayer(any(UUID.class), any(QuestState.class));
    }

    @Test
    @DisplayName("clicking the target NPC completes the quest through advanceStateForPlayer(COMPLETED)")
    void rightKeyCompletes() {
        talkTo("npc_key", "courier", "advance_state", "COMPLETED")
            .onNpcInteract(rightClick(player, "courier"));

        // COMPLETED via the advance path is what fires rewards, the notice and QuestCompleteEvent
        // (AbstractQuest.performAdvance). complete(Player) must never be the path (#1137).
        verify(quest).advanceStateForPlayer(eq(player.getUniqueId()), eq(QuestState.COMPLETED),
            any(PartyBeatContext.class));
        verify(quest, never()).complete(any(Player.class));
        assertEquals(QuestState.COMPLETED, state.get());
    }

    @Test
    @DisplayName("default advance is OBJECTIVE_FOUND, like INTERACT")
    void defaultAdvance() {
        talkTo("npc_key", "courier").onNpcInteract(rightClick(player, "courier"));
        verify(quest).advanceStateForPlayer(eq(player.getUniqueId()), eq(QuestState.OBJECTIVE_FOUND),
            any(PartyBeatContext.class));
    }

    @Test
    @DisplayName("clicking a different NPC does not complete it")
    void wrongKeyDoesNotComplete() {
        talkTo("npc_key", "courier", "advance_state", "COMPLETED")
            .onNpcInteract(rightClick(player, "archivist"));
        assertNotAdvanced();
        assertEquals(QuestState.QUEST_ACTIVE, state.get());
    }

    @Test
    @DisplayName("a left click does not complete a default (right-click) TALK_TO")
    void wrongClickDoesNotComplete() {
        talkTo("npc_key", "courier").onNpcInteract(click(player, "courier", ClickType.LEFT));
        assertNotAdvanced();
    }

    @Test
    @DisplayName("click: any accepts a left click")
    void anyClickCompletes() {
        talkTo("npc_key", "courier", "click", "any").onNpcInteract(click(player, "courier", ClickType.LEFT));
        verify(quest).advanceStateForPlayer(eq(player.getUniqueId()), eq(QuestState.OBJECTIVE_FOUND),
            any(PartyBeatContext.class));
    }

    @Test
    @DisplayName("the right NPC before the objective is active does nothing")
    void inactiveObjectiveDoesNothing() {
        state.set(QuestState.NOT_STARTED);
        talkTo("npc_key", "courier", "advance_state", "COMPLETED")
            .onNpcInteract(rightClick(player, "courier"));
        assertNotAdvanced();
    }

    @Test
    @DisplayName("requires_path gates the objective to its branch")
    void requiresPath() {
        when(quest.getPathChoiceCached(any(Player.class))).thenReturn("left");
        talkTo("npc_key", "courier", "requires_path", "right").onNpcInteract(rightClick(player, "courier"));
        assertNotAdvanced();
    }

    @Test
    @DisplayName("an invalid npc_key makes the objective inert, not a crash")
    void invalidKeyIsInert() {
        GenericTalkToObjective o = talkTo("npc_key", "x".repeat(49));
        assertFalse(o.hasValidKey());
        o.onNpcInteract(rightClick(player, "x".repeat(49)));
        assertNotAdvanced();
    }
}
