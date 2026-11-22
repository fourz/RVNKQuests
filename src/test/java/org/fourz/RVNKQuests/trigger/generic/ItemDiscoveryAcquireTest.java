package org.fourz.RVNKQuests.trigger.generic;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.npc.NpcTestSupport;
import org.fourz.RVNKQuests.party.PartyBeatContext;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
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

import static org.fourz.RVNKQuests.npc.NpcTestSupport.config;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * #2461: ITEM_DISCOVERY fired only on a main-hand right-click. Taking "The Lodestone Key" from a
 * chest did nothing until the player right-clicked. It now also fires on pickup, on closing an
 * inventory with the item in the player's inventory, and on switching the hotbar onto it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ITEM_DISCOVERY on acquisition (#2461)")
class ItemDiscoveryAcquireTest {

    private static final String KEY_NAME = "The Lodestone Key";

    @Mock private RVNKQuests plugin;

    private Player player;
    private PlayerInventory inventory;
    private AtomicReference<QuestState> state;
    private DataDrivenQuest quest;

    @BeforeEach
    void setUp() {
        player = NpcTestSupport.player("antnachos");
        inventory = mock(PlayerInventory.class);
        when(inventory.getContents()).thenReturn(new ItemStack[36]);
        when(player.getInventory()).thenReturn(inventory);
        state = new AtomicReference<>(QuestState.QUEST_ACTIVE);
        quest = NpcTestSupport.quest("lodestone", state, true);
    }

    private GenericItemDiscoveryTrigger trigger(Object... kv) {
        Object[] base = { "item_type", "COMPASS", "custom_name", KEY_NAME,
            "required_state", "QUEST_ACTIVE", "advance_state", "OBJECTIVE_FOUND" };
        Object[] all = new Object[base.length + kv.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(kv, 0, all, base.length, kv.length);
        return new GenericItemDiscoveryTrigger(plugin, quest, config("type", "ITEM_DISCOVERY", all));
    }

    private static ItemStack stack(Material type, String displayName) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(type);
        if (displayName == null) {
            when(stack.hasItemMeta()).thenReturn(false);
            return stack;
        }
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.getDisplayName()).thenReturn(displayName);
        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        return stack;
    }

    private static ItemStack key() {
        // Paper's legacy bridge wraps component names in JSON quotes; the match must strip them.
        return stack(Material.COMPASS, "\"" + KEY_NAME + "\"");
    }

    private EntityPickupItemEvent pickup(ItemStack picked) {
        Item entity = mock(Item.class);
        when(entity.getItemStack()).thenReturn(picked);
        return new EntityPickupItemEvent(player, entity, 0);
    }

    private InventoryCloseEvent close(ItemStack... contents) {
        ItemStack[] slots = new ItemStack[36];
        System.arraycopy(contents, 0, slots, 0, contents.length);
        when(inventory.getContents()).thenReturn(slots);
        InventoryView view = mock(InventoryView.class);
        when(view.getPlayer()).thenReturn(player);
        return new InventoryCloseEvent(view);
    }

    private void assertAdvanced() {
        verify(quest).tryAdvanceStateForPlayer(eq(player.getUniqueId()), eq(QuestState.OBJECTIVE_FOUND),
            any(PartyBeatContext.class));
    }

    private void assertNotAdvanced() {
        verify(quest, never()).tryAdvanceStateForPlayer(any(UUID.class), any(QuestState.class), any());
    }

    @Test
    @DisplayName("picking up the named item fires")
    void pickupFires() {
        trigger().onPickup(pickup(key()));
        assertAdvanced();
    }

    @Test
    @DisplayName("picking up a compass with another name does not fire")
    void pickupWrongNameDoesNotFire() {
        trigger().onPickup(pickup(stack(Material.COMPASS, "Old Compass")));
        assertNotAdvanced();
    }

    @Test
    @DisplayName("picking up an unnamed compass does not fire")
    void pickupUnnamedDoesNotFire() {
        trigger().onPickup(pickup(stack(Material.COMPASS, null)));
        assertNotAdvanced();
    }

    @Test
    @DisplayName("closing a chest with the key in the inventory fires")
    void closeWithKeyFires() {
        trigger().onInventoryClose(close(stack(Material.DIRT, null), null, key()));
        assertAdvanced();
    }

    @Test
    @DisplayName("closing a chest without the key does not fire")
    void closeWithoutKeyDoesNotFire() {
        trigger().onInventoryClose(close(stack(Material.DIRT, null), stack(Material.COMPASS, "Old Compass")));
        assertNotAdvanced();
    }

    @Test
    @DisplayName("switching the hotbar onto the key fires")
    void heldFires() {
        ItemStack k = key();
        when(inventory.getItem(4)).thenReturn(k);
        trigger().onItemHeld(new PlayerItemHeldEvent(player, 0, 4));
        assertAdvanced();
    }

    @Test
    @DisplayName("a wrong beat on a passive path is silent and does not advance")
    void wrongBeatPassiveIsSilent() {
        state.set(QuestState.NOT_STARTED);
        trigger().onPickup(pickup(key()));
        trigger().onInventoryClose(close(key()));
        assertNotAdvanced();
        verify(player, never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("the world scope still applies")
    void wrongWorldDoesNotFire() {
        trigger("world", "event").onPickup(pickup(key()));
        assertNotAdvanced();
    }

    @Test
    @DisplayName("matches() strips colour and JSON quotes and uses contains")
    void matchHelper() {
        assertTrue(GenericItemDiscoveryTrigger.matches(stack(Material.COMPASS, "§6" + KEY_NAME),
            Material.COMPASS, KEY_NAME));
        assertFalse(GenericItemDiscoveryTrigger.matches(stack(Material.PAPER, KEY_NAME), Material.COMPASS, KEY_NAME));
        assertTrue(GenericItemDiscoveryTrigger.matches(stack(Material.COMPASS, null), Material.COMPASS, null));
        assertNull(GenericItemDiscoveryTrigger.findMatch(null, Material.COMPASS, KEY_NAME));
    }
}
