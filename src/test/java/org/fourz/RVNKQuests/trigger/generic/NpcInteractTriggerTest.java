package org.fourz.RVNKQuests.trigger.generic;

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

/** NPC_INTERACT filtering: key, click and state (#2214). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NPC_INTERACT trigger (#2214)")
class NpcInteractTriggerTest {

    @Mock private RVNKQuests plugin;

    private Player player;
    private AtomicReference<QuestState> state;
    private DataDrivenQuest quest;

    @BeforeEach
    void setUp() {
        player = player("Tester");
        state = new AtomicReference<>(QuestState.NOT_STARTED);
        quest = NpcTestSupport.quest("npc_demo", state, true);
    }

    private GenericNpcInteractTrigger trigger(Object... kv) {
        return new GenericNpcInteractTrigger(plugin, quest, config("type", "NPC_INTERACT", kv));
    }

    private void assertAdvancedTo(QuestState target) {
        verify(quest).advanceStateForPlayer(eq(player.getUniqueId()), eq(target), any(PartyBeatContext.class));
    }

    private void assertNotAdvanced() {
        verify(quest, never()).advanceStateForPlayer(any(UUID.class), any(QuestState.class), any());
        verify(quest, never()).advanceStateForPlayer(any(UUID.class), any(QuestState.class));
    }

    @Test
    @DisplayName("right-click on the keyed NPC offers the quest (default advance TRIGGER_FOUND)")
    void rightClickOnKeyFires() {
        trigger("npc_key", "archivist").onNpcInteract(rightClick(player, "archivist"));
        assertAdvancedTo(QuestState.TRIGGER_FOUND);
    }

    @Test
    @DisplayName("a different key does not fire")
    void otherKeyDoesNotFire() {
        trigger("npc_key", "archivist").onNpcInteract(rightClick(player, "courier"));
        assertNotAdvanced();
    }

    @Test
    @DisplayName("keys compare case-insensitively, in config and in the event")
    void keyIsCaseInsensitive() {
        GenericNpcInteractTrigger t = trigger("npc_key", "Archivist");
        assertEquals("archivist", t.getNpcKey());
        t.onNpcInteract(rightClick(player, "ARCHIVIST"));
        assertAdvancedTo(QuestState.TRIGGER_FOUND);
    }

    @Test
    @DisplayName("default click is right: a left click does not fire")
    void leftClickIgnoredByDefault() {
        trigger("npc_key", "archivist").onNpcInteract(click(player, "archivist", ClickType.LEFT));
        assertNotAdvanced();
    }

    @Test
    @DisplayName("click: left fires on left only")
    void clickLeft() {
        GenericNpcInteractTrigger t = trigger("npc_key", "archivist", "click", "left");
        t.onNpcInteract(rightClick(player, "archivist"));
        assertNotAdvanced();
        t.onNpcInteract(click(player, "archivist", ClickType.LEFT));
        assertAdvancedTo(QuestState.TRIGGER_FOUND);
    }

    @Test
    @DisplayName("click: any fires on either")
    void clickAny() {
        GenericNpcInteractTrigger t = trigger("npc_key", "archivist", "click", "ANY");
        assertTrue(t.acceptsClick(ClickType.LEFT));
        assertTrue(t.acceptsClick(ClickType.RIGHT));
    }

    @Test
    @DisplayName("an unknown click value falls back to right")
    void unknownClickFallsBackToRight() {
        GenericNpcInteractTrigger t = trigger("npc_key", "archivist", "click", "middle");
        assertTrue(t.acceptsClick(ClickType.RIGHT));
        assertFalse(t.acceptsClick(ClickType.LEFT));
    }

    @Test
    @DisplayName("advance_state is read: an archivist can start the quest straight to QUEST_ACTIVE")
    void advanceStateIsRead() {
        trigger("npc_key", "archivist", "advance_state", "QUEST_ACTIVE")
            .onNpcInteract(rightClick(player, "archivist"));
        assertAdvancedTo(QuestState.QUEST_ACTIVE);
    }

    @Test
    @DisplayName("a player past the required state does not re-fire the trigger")
    void wrongStateDoesNotFire() {
        state.set(QuestState.QUEST_ACTIVE);
        trigger("npc_key", "archivist").onNpcInteract(rightClick(player, "archivist"));
        assertNotAdvanced();
    }

    @Test
    @DisplayName("a COMPLETED quest (repeatable cooldown, or done) is not offered again")
    void completedQuestNotOffered() {
        state.set(QuestState.COMPLETED);
        trigger("npc_key", "archivist").onNpcInteract(rightClick(player, "archivist"));
        assertNotAdvanced();
    }

    @Test
    @DisplayName("the party checkpoint is the NPC's location and carries required_state")
    void partyCheckpointIsNpcLocation() {
        trigger("npc_key", "archivist").onNpcInteract(rightClick(player, "archivist"));
        var captor = org.mockito.ArgumentCaptor.forClass(PartyBeatContext.class);
        verify(quest).advanceStateForPlayer(any(UUID.class), any(QuestState.class), captor.capture());
        assertEquals(10.0, captor.getValue().x(), 0.001);
        assertEquals(QuestState.NOT_STARTED, captor.getValue().requiredState());
    }

    @Test
    @DisplayName("an invalid npc_key leaves the trigger inert instead of failing the quest")
    void invalidKeyIsInert() {
        GenericNpcInteractTrigger t = trigger("npc_key", "Not A Key!");
        assertFalse(t.hasValidKey());
        t.onNpcInteract(rightClick(player, "not a key!"));
        assertNotAdvanced();
    }

    @Test
    @DisplayName("a missing npc_key leaves the trigger inert")
    void missingKeyIsInert() {
        GenericNpcInteractTrigger t = trigger();
        assertFalse(t.hasValidKey());
        assertNull(t.getNpcKey());
    }
}
