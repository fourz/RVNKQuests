package org.fourz.RVNKQuests.integration;

import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKQuests.integration.dto.LoreEntryDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for Phase 2 bridge methods — spawnItemByName and spawnItemById.
 * Verifies interface declarations, async return types, and graceful degradation.
 */
@DisplayName("ILoreIntegration — Phase 2 spawn bridge methods")
class LoreIntegrationSpawnTest {

    // ==================== Minimal stub for contract verification ====================

    private static class StubLoreIntegration implements ILoreIntegration {
        @Override public boolean isLoreAvailable() { return true; }
        @Override public CompletableFuture<Optional<LoreEntryDTO>> getLoreForQuest(String q) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        @Override public CompletableFuture<Optional<LoreEntryDTO>> getLoreByName(String n) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        @Override public CompletableFuture<Optional<LoreEntryDTO>> getLoreById(UUID id) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        @Override public CompletableFuture<Boolean> grantLoreDiscovery(UUID p, String l) {
            return CompletableFuture.completedFuture(false);
        }
        @Override public CompletableFuture<Optional<String>> getNPCDialogue(String n, String c) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        @Override public CompletableFuture<String> getNPCDialogueOrDefault(String n, String c, String f) {
            return CompletableFuture.completedFuture(f);
        }
        @Override public CompletableFuture<Boolean> hasDiscovered(UUID p, String l) {
            return CompletableFuture.completedFuture(false);
        }
        @Override public CompletableFuture<List<String>> getPlayerDiscoveries(UUID p) {
            return CompletableFuture.completedFuture(List.of());
        }
        @Override public CompletableFuture<Optional<ItemStack>> getOrCreateQuestBook(String k, String t, String d) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        @Override public CompletableFuture<Optional<ItemStack>> rollRngItem(String poolId, String rarityTier) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        @Override public CompletableFuture<Optional<ItemStack>> spawnItemByName(String name) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        @Override public CompletableFuture<Optional<ItemStack>> spawnItemById(int itemId) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        @Override public CompletableFuture<List<ItemStack>> getQuestPresetItems(String questId) {
            return CompletableFuture.completedFuture(List.of());
        }
    }

    private static class UnavailableLoreIntegration extends StubLoreIntegration {
        @Override public boolean isLoreAvailable() { return false; }
        @Override public CompletableFuture<Optional<ItemStack>> spawnItemByName(String name) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        @Override public CompletableFuture<Optional<ItemStack>> spawnItemById(int itemId) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    // ==================== Interface declaration tests ====================

    @Nested
    @DisplayName("Interface declarations")
    class InterfaceDeclarations {

        @Test
        @DisplayName("ILoreIntegration declares spawnItemByName(String)")
        void interfaceDeclaresSpawnByName() {
            assertTrue(hasMethod(ILoreIntegration.class, "spawnItemByName", String.class),
                "ILoreIntegration must declare spawnItemByName(String)");
        }

        @Test
        @DisplayName("ILoreIntegration declares spawnItemById(int)")
        void interfaceDeclaresSpawnById() {
            assertTrue(hasMethod(ILoreIntegration.class, "spawnItemById", int.class),
                "ILoreIntegration must declare spawnItemById(int)");
        }

        @Test
        @DisplayName("spawnItemByName returns CompletableFuture")
        void spawnByNameReturnsCompletableFuture() throws NoSuchMethodException {
            Method m = ILoreIntegration.class.getMethod("spawnItemByName", String.class);
            assertEquals(CompletableFuture.class, m.getReturnType());
        }

        @Test
        @DisplayName("spawnItemById returns CompletableFuture")
        void spawnByIdReturnsCompletableFuture() throws NoSuchMethodException {
            Method m = ILoreIntegration.class.getMethod("spawnItemById", int.class);
            assertEquals(CompletableFuture.class, m.getReturnType());
        }
    }

    // ==================== Contract behaviour tests ====================

    @Nested
    @DisplayName("spawnItemByName contract")
    class SpawnByNameContract {

        @Test
        @DisplayName("Returns non-null Future")
        void returnsNonNullFuture() {
            ILoreIntegration integration = new StubLoreIntegration();
            assertNotNull(integration.spawnItemByName("test_item"));
        }

        @Test
        @DisplayName("Joins without throwing")
        void joinsWithoutThrowing() {
            ILoreIntegration integration = new StubLoreIntegration();
            assertDoesNotThrow(() -> integration.spawnItemByName("test_item").join());
        }

        @Test
        @DisplayName("Returns empty Optional when lore unavailable")
        void returnsEmptyWhenUnavailable() {
            ILoreIntegration integration = new UnavailableLoreIntegration();
            Optional<ItemStack> result = integration.spawnItemByName("test_item").join();
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Can be chained with thenApply")
        void supportsAsyncChaining() {
            ILoreIntegration integration = new StubLoreIntegration();
            boolean found = integration.spawnItemByName("item")
                .thenApply(Optional::isPresent)
                .join();
            assertFalse(found);
        }
    }

    @Nested
    @DisplayName("spawnItemById contract")
    class SpawnByIdContract {

        @Test
        @DisplayName("Returns non-null Future")
        void returnsNonNullFuture() {
            ILoreIntegration integration = new StubLoreIntegration();
            assertNotNull(integration.spawnItemById(42));
        }

        @Test
        @DisplayName("Joins without throwing")
        void joinsWithoutThrowing() {
            ILoreIntegration integration = new StubLoreIntegration();
            assertDoesNotThrow(() -> integration.spawnItemById(1).join());
        }

        @Test
        @DisplayName("Returns empty Optional when lore unavailable")
        void returnsEmptyWhenUnavailable() {
            ILoreIntegration integration = new UnavailableLoreIntegration();
            Optional<ItemStack> result = integration.spawnItemById(99).join();
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Can be chained with thenApply")
        void supportsAsyncChaining() {
            ILoreIntegration integration = new StubLoreIntegration();
            boolean found = integration.spawnItemById(0)
                .thenApply(Optional::isPresent)
                .join();
            assertFalse(found);
        }
    }

    // ==================== Full interface implementable ====================

    @Test
    @DisplayName("Interface can be fully implemented (stub compiles)")
    void interfaceIsImplementable() {
        ILoreIntegration integration = new StubLoreIntegration();
        assertNotNull(integration);
        assertTrue(integration.isLoreAvailable());
    }

    private boolean hasMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        return Arrays.stream(clazz.getMethods())
            .anyMatch(m -> m.getName().equals(name) &&
                Arrays.equals(m.getParameterTypes(), paramTypes));
    }
}
