package org.fourz.RVNKQuests.service.reward;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.data.dto.RewardType;
import org.fourz.RVNKQuests.integration.ILoreIntegration;
import org.fourz.RVNKQuests.service.IRewardService.RewardDeliveryResult;
import org.junit.jupiter.api.*;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ItemRewardProcessor — preset delivery path.
 * Covers: LORE_UNAVAILABLE, MISSING_QUEST_ID, NO_PRESETS, success,
 * inventory-full overflow drop, vanilla path isolation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ItemRewardProcessor — preset delivery")
class ItemRewardProcessorPresetTest {

    @Mock private Server server;
    @Mock private Player player;
    @Mock private PlayerInventory inventory;
    @Mock private World world;
    @Mock private ILoreIntegration loreIntegration;

    private UUID playerId;
    private ItemRewardProcessor processor;

    @BeforeEach
    void setUp() throws Exception {
        playerId = UUID.randomUUID();

        // Wire Bukkit static singleton — same pattern as QuestCompletionDispatchTest
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);

        when(server.getPlayer(playerId)).thenReturn(player);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(mock(Location.class));

        processor = new ItemRewardProcessor(loreIntegration);
    }

    // ==================== Helper ====================

    private RewardDTO presetReward(String questId) {
        Map<String, String> meta = new HashMap<>();
        meta.put("item_source", "preset");
        if (questId != null) {
            meta.put("quest_id", questId);
        }
        return new RewardDTO("r1", RewardType.ITEM, "PRESET", 1, "preset reward", meta);
    }

    // ==================== Error paths ====================

    @Test
    @DisplayName("Returns PLAYER_OFFLINE when player is not online")
    void returnsPlayerOfflineWhenNotOnline() {
        when(server.getPlayer(playerId)).thenReturn(null);

        RewardDeliveryResult result = processor.deliver(playerId, presetReward("q1")).join();

        assertFalse(result.success());
        assertEquals("PLAYER_OFFLINE", result.errorCode());
    }

    @Test
    @DisplayName("Returns LORE_UNAVAILABLE when loreIntegration is null")
    void returnsLoreUnavailableWhenIntegrationIsNull() {
        processor = new ItemRewardProcessor(null);

        RewardDeliveryResult result = processor.deliver(playerId, presetReward("q1")).join();

        assertFalse(result.success());
        assertEquals("LORE_UNAVAILABLE", result.errorCode());
    }

    @Test
    @DisplayName("Returns LORE_UNAVAILABLE when isLoreAvailable() returns false")
    void returnsLoreUnavailableWhenLoreDown() {
        when(loreIntegration.isLoreAvailable()).thenReturn(false);

        RewardDeliveryResult result = processor.deliver(playerId, presetReward("q1")).join();

        assertFalse(result.success());
        assertEquals("LORE_UNAVAILABLE", result.errorCode());
    }

    @Test
    @DisplayName("Returns MISSING_QUEST_ID when quest_id metadata is absent")
    void returnsMissingQuestIdWhenAbsent() {
        when(loreIntegration.isLoreAvailable()).thenReturn(true);

        RewardDeliveryResult result = processor.deliver(playerId, presetReward(null)).join();

        assertFalse(result.success());
        assertEquals("MISSING_QUEST_ID", result.errorCode());
    }

    @Test
    @DisplayName("Returns MISSING_QUEST_ID when quest_id metadata is empty string")
    void returnsMissingQuestIdWhenEmpty() {
        when(loreIntegration.isLoreAvailable()).thenReturn(true);

        RewardDeliveryResult result = processor.deliver(playerId, presetReward("")).join();

        assertFalse(result.success());
        assertEquals("MISSING_QUEST_ID", result.errorCode());
    }

    @Test
    @DisplayName("Returns NO_PRESETS when getQuestPresetItems returns empty list")
    void returnsNoPresetsWhenListEmpty() {
        when(loreIntegration.isLoreAvailable()).thenReturn(true);
        when(loreIntegration.getQuestPresetItems("q1"))
            .thenReturn(CompletableFuture.completedFuture(List.of()));

        RewardDeliveryResult result = processor.deliver(playerId, presetReward("q1")).join();

        assertFalse(result.success());
        assertEquals("NO_PRESETS", result.errorCode());
    }

    // ==================== Success paths ====================

    @Test
    @DisplayName("Delivers single preset item to inventory — success")
    void deliversSinglePresetItem() {
        ItemStack item = mock(ItemStack.class);
        when(loreIntegration.isLoreAvailable()).thenReturn(true);
        when(loreIntegration.getQuestPresetItems("q2"))
            .thenReturn(CompletableFuture.completedFuture(List.of(item)));
        when(inventory.addItem(item)).thenReturn(new HashMap<>());

        RewardDeliveryResult result = processor.deliver(playerId, presetReward("q2")).join();

        assertTrue(result.success());
        verify(inventory).addItem(item);
        verify(world, never()).dropItemNaturally(any(), any());
    }

    @Test
    @DisplayName("Delivers multiple preset items — all fit in inventory")
    void deliversMultiplePresetItems() {
        ItemStack item1 = mock(ItemStack.class);
        ItemStack item2 = mock(ItemStack.class);
        when(loreIntegration.isLoreAvailable()).thenReturn(true);
        when(loreIntegration.getQuestPresetItems("q3"))
            .thenReturn(CompletableFuture.completedFuture(List.of(item1, item2)));
        when(inventory.addItem(item1)).thenReturn(new HashMap<>());
        when(inventory.addItem(item2)).thenReturn(new HashMap<>());

        RewardDeliveryResult result = processor.deliver(playerId, presetReward("q3")).join();

        assertTrue(result.success());
        verify(inventory).addItem(item1);
        verify(inventory).addItem(item2);
    }

    @Test
    @DisplayName("Drops overflow when inventory full and dropOnFull=true (default)")
    void dropsOverflowWhenInventoryFull() {
        ItemStack item = mock(ItemStack.class);
        ItemStack overflow = mock(ItemStack.class);
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        leftover.put(0, overflow);

        when(loreIntegration.isLoreAvailable()).thenReturn(true);
        when(loreIntegration.getQuestPresetItems("q4"))
            .thenReturn(CompletableFuture.completedFuture(List.of(item)));
        when(inventory.addItem(item)).thenReturn(leftover);

        RewardDeliveryResult result = processor.deliver(playerId, presetReward("q4")).join();

        assertTrue(result.success(), "Should succeed even when dropping overflow");
        verify(world).dropItemNaturally(any(Location.class), eq(overflow));
    }

    @Test
    @DisplayName("Does NOT drop overflow when dropOnFull=false and inventory full")
    void doesNotDropOverflowWhenDisabled() {
        ItemStack item = mock(ItemStack.class);
        ItemStack overflow = mock(ItemStack.class);
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        leftover.put(0, overflow);

        when(loreIntegration.isLoreAvailable()).thenReturn(true);
        when(loreIntegration.getQuestPresetItems("q5"))
            .thenReturn(CompletableFuture.completedFuture(List.of(item)));
        when(inventory.addItem(item)).thenReturn(leftover);

        Map<String, String> meta = new HashMap<>();
        meta.put("item_source", "preset");
        meta.put("quest_id", "q5");
        meta.put("dropOnFull", "false");
        RewardDTO reward = new RewardDTO("r_noDrop", RewardType.ITEM, "PRESET", 1, null, meta);

        processor.deliver(playerId, reward).join();

        verify(world, never()).dropItemNaturally(any(), any());
    }

    // ==================== Isolation: vanilla path unaffected ====================

    @Test
    @DisplayName("Vanilla path still triggers when item_source is absent")
    void vanillaPathUnaffectedWhenNoItemSource() {
        RewardDTO vanillaReward = RewardDTO.create("r_vanilla", RewardType.ITEM, "DIAMOND", 1);

        RewardDeliveryResult result = processor.deliver(playerId, vanillaReward).join();

        // DIAMOND is a valid material — vanilla path runs, not the preset path
        // loreIntegration should never be consulted
        verify(loreIntegration, never()).isLoreAvailable();
        verify(loreIntegration, never()).getQuestPresetItems(any());
    }

    @Test
    @DisplayName("Vanilla path returns INVALID_MATERIAL for bad material name")
    void vanillaPathReturnsBadMaterialError() {
        RewardDTO badReward = RewardDTO.create("r_bad", RewardType.ITEM, "NOT_A_REAL_MATERIAL", 1);

        RewardDeliveryResult result = processor.deliver(playerId, badReward).join();

        assertFalse(result.success());
        assertEquals("INVALID_MATERIAL", result.errorCode());
    }

    // ==================== Processor metadata ====================

    @Test
    @DisplayName("getType() returns ITEM")
    void getTypeReturnsItem() {
        assertEquals(RewardType.ITEM, processor.getType());
    }

    @Test
    @DisplayName("requiresOnlinePlayer() is true")
    void requiresOnlinePlayer() {
        assertTrue(processor.requiresOnlinePlayer());
    }

    @Test
    @DisplayName("setLoreIntegration replaces integration reference")
    void setLoreIntegrationWorks() {
        processor = new ItemRewardProcessor(null);
        processor.setLoreIntegration(loreIntegration);
        when(loreIntegration.isLoreAvailable()).thenReturn(false);

        RewardDeliveryResult result = processor.deliver(playerId, presetReward("q1")).join();

        // If setLoreIntegration worked, we get LORE_UNAVAILABLE not NPE
        assertEquals("LORE_UNAVAILABLE", result.errorCode());
    }
}
