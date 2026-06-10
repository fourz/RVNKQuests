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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RngItemRewardProcessor — RNG_ITEM reward type.
 * Covers: error paths, success delivery, overflow drop, metadata, isolation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RngItemRewardProcessor — RNG_ITEM delivery")
class RngItemRewardProcessorTest {

    @Mock private Server server;
    @Mock private Player player;
    @Mock private PlayerInventory inventory;
    @Mock private World world;
    @Mock private ILoreIntegration loreIntegration;

    private UUID playerId;
    private RngItemRewardProcessor processor;

    @BeforeEach
    void setUp() throws Exception {
        playerId = UUID.randomUUID();

        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);

        when(server.getPlayer(playerId)).thenReturn(player);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(mock(Location.class));

        processor = new RngItemRewardProcessor(loreIntegration);
    }

    private RewardDTO rngReward(String poolId, String rarityTier) {
        Map<String, String> meta = new HashMap<>();
        if (poolId != null) meta.put("pool_id", poolId);
        if (rarityTier != null) meta.put("rarity_tier", rarityTier);
        return new RewardDTO("r_rng", RewardType.RNG_ITEM, "", 1, "rng reward", meta);
    }

    // ==================== Error paths ====================

    @Test
    @DisplayName("Returns PLAYER_OFFLINE when player not online")
    void returnsPlayerOffline() {
        when(server.getPlayer(playerId)).thenReturn(null);
        assertFalse(processor.deliver(playerId, rngReward("pool1", null)).join().success());
        assertEquals("PLAYER_OFFLINE", processor.deliver(playerId, rngReward("pool1", null)).join().errorCode());
    }

    @Test
    @DisplayName("Returns LORE_UNAVAILABLE when integration is null")
    void returnsLoreUnavailableWhenNull() {
        processor = new RngItemRewardProcessor(null);
        RewardDeliveryResult r = processor.deliver(playerId, rngReward("pool1", null)).join();
        assertFalse(r.success());
        assertEquals("LORE_UNAVAILABLE", r.errorCode());
    }

    @Test
    @DisplayName("Returns LORE_UNAVAILABLE when isLoreAvailable() is false")
    void returnsLoreUnavailableWhenDown() {
        when(loreIntegration.isLoreAvailable()).thenReturn(false);
        RewardDeliveryResult r = processor.deliver(playerId, rngReward("pool1", null)).join();
        assertFalse(r.success());
        assertEquals("LORE_UNAVAILABLE", r.errorCode());
    }

    @Test
    @DisplayName("Returns MISSING_POOL_ID when pool_id metadata absent")
    void returnsMissingPoolId() {
        when(loreIntegration.isLoreAvailable()).thenReturn(true);
        RewardDeliveryResult r = processor.deliver(playerId, rngReward(null, null)).join();
        assertFalse(r.success());
        assertEquals("MISSING_POOL_ID", r.errorCode());
    }

    @Test
    @DisplayName("Returns POOL_EMPTY when roll returns empty Optional")
    void returnsPoolEmpty() {
        when(loreIntegration.isLoreAvailable()).thenReturn(true);
        when(loreIntegration.rollRngItem("empty_pool", null))
            .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        RewardDeliveryResult r = processor.deliver(playerId, rngReward("empty_pool", null)).join();
        assertFalse(r.success());
        assertEquals("POOL_EMPTY", r.errorCode());
    }

    // ==================== Success paths ====================

    @Test
    @DisplayName("Delivers rolled item to inventory — success")
    void deliversRolledItem() {
        ItemStack item = mock(ItemStack.class);
        when(loreIntegration.isLoreAvailable()).thenReturn(true);
        when(loreIntegration.rollRngItem("drops", "RARE"))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(item)));
        when(inventory.addItem(item)).thenReturn(new HashMap<>());

        RewardDeliveryResult r = processor.deliver(playerId, rngReward("drops", "RARE")).join();

        assertTrue(r.success());
        verify(inventory).addItem(item);
    }

    @Test
    @DisplayName("null rarityTier rolls across all tiers")
    void nullRarityTierPassedThrough() {
        ItemStack item = mock(ItemStack.class);
        when(loreIntegration.isLoreAvailable()).thenReturn(true);
        when(loreIntegration.rollRngItem("drops", null))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(item)));
        when(inventory.addItem(item)).thenReturn(new HashMap<>());

        RewardDeliveryResult r = processor.deliver(playerId, rngReward("drops", null)).join();

        assertTrue(r.success());
        verify(loreIntegration).rollRngItem("drops", null);
    }

    @Test
    @DisplayName("Drops overflow when inventory full (default dropOnFull=true)")
    void dropsOverflowWhenFull() {
        ItemStack item = mock(ItemStack.class);
        ItemStack overflow = mock(ItemStack.class);
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        leftover.put(0, overflow);

        when(loreIntegration.isLoreAvailable()).thenReturn(true);
        when(loreIntegration.rollRngItem("drops", "EPIC"))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(item)));
        when(inventory.addItem(item)).thenReturn(leftover);

        RewardDeliveryResult r = processor.deliver(playerId, rngReward("drops", "EPIC")).join();

        assertTrue(r.success());
        verify(world).dropItemNaturally(any(Location.class), eq(overflow));
    }

    @Test
    @DisplayName("Does not drop overflow when dropOnFull=false")
    void noDropWhenDisabled() {
        ItemStack item = mock(ItemStack.class);
        ItemStack overflow = mock(ItemStack.class);
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        leftover.put(0, overflow);

        when(loreIntegration.isLoreAvailable()).thenReturn(true);
        when(loreIntegration.rollRngItem("drops", null))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(item)));
        when(inventory.addItem(item)).thenReturn(leftover);

        Map<String, String> meta = new HashMap<>();
        meta.put("pool_id", "drops");
        meta.put("dropOnFull", "false");
        RewardDTO reward = new RewardDTO("r2", RewardType.RNG_ITEM, "", 1, null, meta);

        processor.deliver(playerId, reward).join();

        verify(world, never()).dropItemNaturally(any(), any());
    }

    // ==================== Processor metadata ====================

    @Test
    @DisplayName("getType() returns RNG_ITEM")
    void getTypeReturnsRngItem() {
        assertEquals(RewardType.RNG_ITEM, processor.getType());
    }

    @Test
    @DisplayName("requiresOnlinePlayer() is true")
    void requiresOnlinePlayer() {
        assertTrue(processor.requiresOnlinePlayer());
    }

    @Test
    @DisplayName("supportsOfflineQueue() is false")
    void doesNotSupportOfflineQueue() {
        assertFalse(processor.supportsOfflineQueue());
    }

    @Test
    @DisplayName("setLoreIntegration wires integration")
    void setLoreIntegrationWorks() {
        processor = new RngItemRewardProcessor(null);
        processor.setLoreIntegration(loreIntegration);
        when(loreIntegration.isLoreAvailable()).thenReturn(false);

        assertEquals("LORE_UNAVAILABLE", processor.deliver(playerId, rngReward("p", null)).join().errorCode());
    }

    @Test
    @DisplayName("RewardType.RNG_ITEM enum value exists")
    void rngItemEnumValueExists() {
        assertDoesNotThrow(() -> RewardType.valueOf("RNG_ITEM"));
        assertEquals(RewardType.RNG_ITEM, RewardType.valueOf("RNG_ITEM"));
    }
}
