package org.fourz.RVNKQuests.objective.generic;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.npc.NpcInteractionCoordinator;
import org.fourz.RVNKQuests.npc.NpcQuestComponent;
import org.fourz.RVNKQuests.npc.NpcTestSupport;
import org.fourz.RVNKQuests.party.PartyBeatContext;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent;
import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent.ClickType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.fourz.RVNKQuests.npc.NpcTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** DELIVER: hand the right item to the keyed NPC; taken once, only on a committed advance (#2390). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DELIVER objective (#2390)")
class DeliverObjectiveTest {

    @Mock private RVNKQuests plugin;

    private Player player;
    private PlayerInventory inventory;
    private AtomicReference<QuestState> state;
    private DataDrivenQuest quest;

    @BeforeEach
    void setUp() {
        player = player("Tester");
        inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getContents()).thenReturn(new ItemStack[0]);
        state = new AtomicReference<>(QuestState.QUEST_ACTIVE);
        quest = NpcTestSupport.quest("outpost_ledger", state, true);
    }

    private GenericDeliverObjective deliver(Object... kv) {
        return deliver(quest, kv);
    }

    private GenericDeliverObjective deliver(DataDrivenQuest q, Object... kv) {
        Map<String, Object> config = config("objective_type", "DELIVER", kv);
        config.putIfAbsent("npc_key", "guide_outpost");
        return new GenericDeliverObjective(plugin, q, config, Runnable::run);
    }

    /** A main-hand stack. {@code name} and {@code loreId} may be null. */
    private ItemStack hold(Material type, int amount, String name, Integer loreId, String loreName) {
        ItemStack stack = stack(type, amount, name, loreId, loreName);
        when(inventory.getItemInMainHand()).thenReturn(stack);
        return stack;
    }

    private static ItemStack stack(Material type, int amount, String name, Integer loreId, String loreName) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(type);
        when(stack.getAmount()).thenReturn(amount);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(meta.hasDisplayName()).thenReturn(name != null);
        when(meta.getDisplayName()).thenReturn(name);
        when(pdc.get(eq(DeliverItemMatch.LORE_ITEM_ID_KEY), eq(PersistentDataType.INTEGER))).thenReturn(loreId);
        when(pdc.get(eq(DeliverItemMatch.LORE_ITEM_NAME_KEY), eq(PersistentDataType.STRING))).thenReturn(loreName);
        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        return stack;
    }

    private void assertAdvancedTo(QuestState target) {
        verify(quest).tryAdvanceStateForPlayer(eq(player.getUniqueId()), eq(target), any(PartyBeatContext.class));
    }

    private void assertNotAdvanced() {
        verify(quest, never()).tryAdvanceStateForPlayer(any(UUID.class), any(QuestState.class), any());
        verify(quest, never()).advanceStateForPlayer(any(UUID.class), any(QuestState.class), any());
    }

    private void assertKept(ItemStack stack) {
        verify(inventory, never()).setItemInMainHand(any());
        verify(inventory, never()).setItem(anyInt(), any());
        verify(stack, never()).setAmount(anyInt());
    }

    // ==================== Matching ====================

    @Test
    @DisplayName("match by item_type: the held book is taken and the step advances")
    void matchByType() {
        hold(Material.WRITTEN_BOOK, 1, null, null, null);
        deliver("item_type", "WRITTEN_BOOK").onNpcInteract(rightClick(player, "guide_outpost"));

        assertAdvancedTo(QuestState.OBJECTIVE_FOUND);
        verify(inventory, times(1)).setItemInMainHand(null);
        assertEquals(QuestState.OBJECTIVE_FOUND, state.get());
    }

    @Test
    @DisplayName("match by custom_name: colour codes and the Paper JSON quotes are stripped")
    void matchByName() {
        hold(Material.WRITTEN_BOOK, 1, "\"§6Outpost Ledger\"", null, null);
        deliver("custom_name", "&6Outpost Ledger", "advance_state", "COMPLETED")
            .onNpcInteract(rightClick(player, "guide_outpost"));

        assertAdvancedTo(QuestState.COMPLETED);
        verify(inventory).setItemInMainHand(null);
    }

    @Test
    @DisplayName("item_name is the legacy alias of custom_name; a different name is refused")
    void nameMismatchRefused() {
        ItemStack held = hold(Material.WRITTEN_BOOK, 1, "Grocery List", null, null);
        deliver("item_name", "Outpost Ledger").onNpcInteract(rightClick(player, "guide_outpost"));
        assertNotAdvanced();
        assertKept(held);
    }

    @Test
    @DisplayName("match by lore_item_id: the rvnklore:lore_item_id PDC tag")
    void matchByLoreId() {
        hold(Material.WRITTEN_BOOK, 1, "Ledger", 42, "outpost_ledger");
        deliver("lore_item_id", 42).onNpcInteract(rightClick(player, "guide_outpost"));
        assertAdvancedTo(QuestState.OBJECTIVE_FOUND);
        verify(inventory).setItemInMainHand(null);
    }

    @Test
    @DisplayName("a lore item with another id, or no tag, is refused and kept")
    void wrongLoreIdRefused() {
        ItemStack held = hold(Material.WRITTEN_BOOK, 1, "Ledger", 41, null);
        GenericDeliverObjective o = deliver("lore_item_id", "42", "item_type", "WRITTEN_BOOK");
        o.onNpcInteract(rightClick(player, "guide_outpost"));

        ItemStack untagged = hold(Material.WRITTEN_BOOK, 1, "Ledger", null, null);
        o.onNpcInteract(rightClick(player, "guide_outpost"));

        assertNotAdvanced();
        assertKept(held);
        assertKept(untagged);
    }

    @Test
    @DisplayName("a non-numeric lore_item_id matches the rvnklore:lore_item_name tag, case ignored")
    void matchByLoreName() {
        hold(Material.WRITTEN_BOOK, 1, null, 7, "Outpost_Ledger");
        deliver("lore_item_id", "outpost_ledger").onNpcInteract(rightClick(player, "guide_outpost"));
        assertAdvancedTo(QuestState.OBJECTIVE_FOUND);
    }

    @Test
    @DisplayName("every key given must match: right type, wrong name is refused")
    void allKeysMustMatch() {
        ItemStack held = hold(Material.WRITTEN_BOOK, 1, "Other Book", 42, null);
        deliver("item_type", "WRITTEN_BOOK", "custom_name", "Outpost Ledger", "lore_item_id", 42)
            .onNpcInteract(rightClick(player, "guide_outpost"));
        assertNotAdvanced();
        assertKept(held);
    }

    // ==================== Refusals ====================

    @Test
    @DisplayName("wrong item: kept, no advance")
    void wrongItemKept() {
        ItemStack held = hold(Material.DIRT, 64, null, null, null);
        deliver("item_type", "WRITTEN_BOOK").onNpcInteract(rightClick(player, "guide_outpost"));
        assertNotAdvanced();
        assertKept(held);
        assertEquals(QuestState.QUEST_ACTIVE, state.get());
    }

    @Test
    @DisplayName("empty hand: no advance")
    void emptyHand() {
        hold(Material.AIR, 0, null, null, null);
        deliver("item_type", "WRITTEN_BOOK").onNpcInteract(rightClick(player, "guide_outpost"));
        when(inventory.getItemInMainHand()).thenReturn(null);
        deliver("item_type", "WRITTEN_BOOK").onNpcInteract(rightClick(player, "guide_outpost"));
        assertNotAdvanced();
        verify(inventory, never()).setItemInMainHand(any());
    }

    @Test
    @DisplayName("amount greater than the held stack is refused and the stack is kept")
    void amountAboveHeldRefused() {
        ItemStack held = hold(Material.EMERALD, 2, null, null, null);
        deliver("item_type", "EMERALD", "amount", 3).onNpcInteract(rightClick(player, "guide_outpost"));
        assertNotAdvanced();
        assertKept(held);
    }

    @Test
    @DisplayName("amount 3 from a stack of 5 leaves 2")
    void amountTakesPartOfStack() {
        ItemStack held = hold(Material.EMERALD, 5, null, null, null);
        deliver("item_type", "EMERALD", "amount", 3).onNpcInteract(rightClick(player, "guide_outpost"));
        assertAdvancedTo(QuestState.OBJECTIVE_FOUND);
        verify(held, times(1)).setAmount(2);
        verify(inventory, times(1)).setItemInMainHand(held);
    }

    @Test
    @DisplayName("the right item at the wrong beat: nothing happens")
    void wrongStateDoesNothing() {
        state.set(QuestState.TRIGGER_FOUND);
        ItemStack held = hold(Material.WRITTEN_BOOK, 1, null, null, null);
        deliver("item_type", "WRITTEN_BOOK").onNpcInteract(rightClick(player, "guide_outpost"));
        assertNotAdvanced();
        assertKept(held);
    }

    @Test
    @DisplayName("another NPC, or a left click on a right-click DELIVER, does nothing")
    void wrongNpcOrClick() {
        ItemStack held = hold(Material.WRITTEN_BOOK, 1, null, null, null);
        GenericDeliverObjective o = deliver("item_type", "WRITTEN_BOOK");
        o.onNpcInteract(rightClick(player, "archivist"));
        o.onNpcInteract(click(player, "guide_outpost", ClickType.LEFT));
        assertNotAdvanced();
        assertKept(held);
    }

    @Test
    @DisplayName("requires_path gates the hand-in to its branch")
    void requiresPath() {
        when(quest.getPathChoiceCached(any(Player.class))).thenReturn("left");
        ItemStack held = hold(Material.WRITTEN_BOOK, 1, null, null, null);
        deliver("item_type", "WRITTEN_BOOK", "requires_path", "right")
            .onNpcInteract(rightClick(player, "guide_outpost"));
        assertNotAdvanced();
        assertKept(held);
    }

    // ==================== Consume exactly once, only on commit ====================

    @Test
    @DisplayName("a refused advance takes nothing")
    void refusedAdvanceKeepsItem() {
        DataDrivenQuest refusing = NpcTestSupport.quest("outpost_ledger", state, false);
        ItemStack held = hold(Material.WRITTEN_BOOK, 1, null, null, null);
        deliver(refusing, "item_type", "WRITTEN_BOOK").onNpcInteract(rightClick(player, "guide_outpost"));

        verify(refusing).tryAdvanceStateForPlayer(eq(player.getUniqueId()), eq(QuestState.OBJECTIVE_FOUND),
            any(PartyBeatContext.class));
        assertKept(held);
    }

    @Test
    @DisplayName("a failed advance (exception) takes nothing")
    void failedAdvanceKeepsItem() {
        when(quest.tryAdvanceStateForPlayer(any(UUID.class), any(QuestState.class), any(PartyBeatContext.class)))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("db down")));
        ItemStack held = hold(Material.WRITTEN_BOOK, 1, null, null, null);
        deliver("item_type", "WRITTEN_BOOK").onNpcInteract(rightClick(player, "guide_outpost"));
        assertKept(held);
    }

    @Test
    @DisplayName("the item is taken only after the advance commits, and only once")
    void takenAfterCommitOnce() {
        CompletableFuture<Boolean> pending = new CompletableFuture<>();
        when(quest.tryAdvanceStateForPlayer(any(UUID.class), any(QuestState.class), any(PartyBeatContext.class)))
            .thenReturn(pending);
        ItemStack held = hold(Material.WRITTEN_BOOK, 3, null, null, null);
        GenericDeliverObjective o = deliver("item_type", "WRITTEN_BOOK");

        o.onNpcInteract(rightClick(player, "guide_outpost"));
        assertKept(held);                                   // not yet committed

        o.onNpcInteract(rightClick(player, "guide_outpost")); // second click while in flight
        verify(quest, times(1)).tryAdvanceStateForPlayer(any(UUID.class), any(QuestState.class), any());

        pending.complete(true);
        verify(held, times(1)).setAmount(2);
        verify(inventory, times(1)).setItemInMainHand(held);
    }

    @Test
    @DisplayName("one click heard by two DELIVER quests on one NPC takes one item, advances one quest")
    void oneClickOneDelivery() {
        AtomicReference<QuestState> otherState = new AtomicReference<>(QuestState.QUEST_ACTIVE);
        DataDrivenQuest other = NpcTestSupport.quest("other_quest", otherState, true);
        ItemStack held = hold(Material.WRITTEN_BOOK, 2, null, null, null);
        GenericDeliverObjective a = deliver("item_type", "WRITTEN_BOOK");
        GenericDeliverObjective b = deliver(other, "item_type", "WRITTEN_BOOK");

        RvnkNpcInteractEvent click = rightClick(player, "guide_outpost");
        a.onNpcInteract(click);
        b.onNpcInteract(click);

        verify(quest, times(1)).tryAdvanceStateForPlayer(any(UUID.class), any(QuestState.class), any());
        verify(other, never()).tryAdvanceStateForPlayer(any(UUID.class), any(QuestState.class), any());
        verify(held, times(1)).setAmount(1);
    }

    @Test
    @DisplayName("the item moved before commit: taken from the inventory instead")
    void takenFromInventoryWhenMoved() {
        CompletableFuture<Boolean> pending = new CompletableFuture<>();
        when(quest.tryAdvanceStateForPlayer(any(UUID.class), any(QuestState.class), any(PartyBeatContext.class)))
            .thenReturn(pending);
        hold(Material.WRITTEN_BOOK, 1, null, null, null);
        GenericDeliverObjective o = deliver("item_type", "WRITTEN_BOOK");
        o.onNpcInteract(rightClick(player, "guide_outpost"));

        // The player switched slot before the write landed.
        when(inventory.getItemInMainHand()).thenReturn(null);
        ItemStack moved = stack(Material.WRITTEN_BOOK, 1, null, null, null);
        when(inventory.getContents()).thenReturn(new ItemStack[] {null, moved});
        pending.complete(true);

        verify(inventory, times(1)).setItem(1, null);
        verify(inventory, never()).setItemInMainHand(any());
    }

    // ==================== Dialogue through the coordinator ====================

    @Nested
    @DisplayName("wrong_item_message")
    class WrongItemLine {
        private final List<NpcQuestComponent> components = new ArrayList<>();
        private final Map<String, String> lore = new HashMap<>();
        private NpcInteractionCoordinator coordinator;

        @BeforeEach
        void wire() {
            coordinator = new NpcInteractionCoordinator(
                () -> components, () -> null,
                (key, context) -> CompletableFuture.completedFuture(
                    Optional.ofNullable(lore.get("npc_" + key + "_" + context))),
                Runnable::run, Runnable::run, m -> { }, m -> { });
            when(plugin.getNpcCoordinator()).thenReturn(coordinator);
            lore.put("npc_guide_outpost_active", "Bring me the ledger.");
            lore.put("npc_guide_outpost_done", "Thank you.");
        }

        private void fire(RvnkNpcInteractEvent event, GenericDeliverObjective... os) {
            for (GenericDeliverObjective o : os) o.onNpcInteract(event);
            coordinator.onNpcInteract(event);
        }

        @Test
        @DisplayName("wrong item: the message, once, as the NPC's only line")
        void wrongItemMessageOnce() {
            hold(Material.DIRT, 1, null, null, null);
            GenericDeliverObjective a = deliver("item_type", "WRITTEN_BOOK", "wrong_item_message", "&cThat is not the ledger.");
            GenericDeliverObjective b = deliver("item_type", "WRITTEN_BOOK", "wrong_item_message", "second line");
            components.add(a);
            components.add(b);

            fire(rightClick(player, "guide_outpost"), a, b);

            verify(player, times(1)).sendMessage(anyString());
            verify(player).sendMessage(contains("That is not the ledger."));
        }

        @Test
        @DisplayName("no wrong_item_message: the usual active line")
        void noMessageFallsBackToDialogue() {
            hold(Material.DIRT, 1, null, null, null);
            GenericDeliverObjective a = deliver("item_type", "WRITTEN_BOOK");
            components.add(a);

            fire(rightClick(player, "guide_outpost"), a);

            verify(player, times(1)).sendMessage(anyString());
            verify(player).sendMessage(contains("Bring me the ledger."));
        }

        @Test
        @DisplayName("right item: the commit line, never the wrong-item line")
        void rightItemNoWrongLine() {
            hold(Material.WRITTEN_BOOK, 1, null, null, null);
            GenericDeliverObjective a = deliver("item_type", "WRITTEN_BOOK", "advance_state", "COMPLETED",
                "wrong_item_message", "Not that.");
            components.add(a);

            fire(rightClick(player, "guide_outpost"), a);

            verify(player, times(1)).sendMessage(anyString());
            verify(player).sendMessage(contains("Thank you."));
            verify(player, never()).sendMessage(contains("Not that."));
        }
    }

    // ==================== Config validation ====================

    @Test
    @DisplayName("no item key fails construction")
    void noItemKeyFails() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> deliver());
        assertTrue(e.getMessage().contains("item_type"), e.getMessage());
    }

    @Test
    @DisplayName("amount below 1 fails construction")
    void amountBelowOneFails() {
        assertThrows(IllegalArgumentException.class, () -> deliver("item_type", "WRITTEN_BOOK", "amount", 0));
    }

    @Test
    @DisplayName("an unknown item_type fails construction")
    void badMaterialFails() {
        assertThrows(IllegalArgumentException.class, () -> deliver("item_type", "NOT_A_THING"));
    }

    @Test
    @DisplayName("a missing npc_key makes it inert, and quest component add rejects it")
    void missingKey() {
        Map<String, Object> config = config("objective_type", "DELIVER", "item_type", "WRITTEN_BOOK");
        GenericDeliverObjective o = new GenericDeliverObjective(plugin, quest, config, Runnable::run);
        assertFalse(o.hasValidKey());

        QuestComponentFactory factory = new QuestComponentFactory(plugin, quest);
        assertNotNull(factory.validateComponentConfig("o", config));
        assertNotNull(factory.validateComponentConfig("o",
            config("objective_type", "DELIVER", "npc_key", "guide_outpost")));
        assertNotNull(factory.validateComponentConfig("o",
            config("objective_type", "DELIVER", "npc_key", "guide_outpost", "item_type", "BOOK", "amount", -1)));
        assertNull(factory.validateComponentConfig("o",
            config("objective_type", "DELIVER", "npc_key", "Guide_Outpost", "item_type", "written_book")));
    }

    @Test
    @DisplayName("defaults: amount 1, right click, QUEST_ACTIVE -> OBJECTIVE_FOUND, an objective")
    void defaults() {
        GenericDeliverObjective o = deliver("item_type", "WRITTEN_BOOK");
        assertEquals(1, o.getAmount());
        assertEquals(QuestState.QUEST_ACTIVE, o.getRequiredState());
        assertEquals(QuestState.OBJECTIVE_FOUND, o.getAdvanceState());
        assertFalse(o.isTrigger());
        assertTrue(o.acceptsClick(ClickType.RIGHT));
        assertFalse(o.acceptsClick(ClickType.LEFT));
        assertEquals("guide_outpost", o.getNpcKey());
    }
}
