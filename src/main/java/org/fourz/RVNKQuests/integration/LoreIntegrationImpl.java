package org.fourz.RVNKQuests.integration;

import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.integration.dto.LoreEntryDTO;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of ILoreIntegration for RVNKLore cross-plugin communication.
 *
 * <p>Thin async wrapper over {@link LoreServiceFacade}, which owns all RVNKLore
 * reflection (#1494). This class adds only the async envelope and the ILoreIntegration
 * business semantics (naming patterns, default fallbacks). All actual RVNKLore invocation
 * lives in the facade.</p>
 *
 * <p>Discovery is no longer stubbed (#1650). {@code grantLoreDiscovery}, {@code hasDiscovered}
 * and {@code getPlayerDiscoveries} now go through RVNKLore's {@code IDiscoveryService} and write
 * to {@code lore_discovery}. Against RVNKLore older than 1.0.133 that service is absent, and the
 * three methods report failure or empty rather than claiming a success they cannot deliver.</p>
 *
 * <p>Gracefully degrades when RVNKLore is unavailable — all lore lookups return
 * empty Optional/false and quests continue to function without lore features.</p>
 */
public class LoreIntegrationImpl implements ILoreIntegration {

    private final LogManager logger;
    private final LoreServiceFacade facade;

    public LoreIntegrationImpl(RVNKQuests plugin) {
        this.logger = LogManager.getInstance(plugin, getClass());
        this.facade = new LoreServiceFacade(plugin);
    }

    @Override
    public boolean isLoreAvailable() {
        return facade.isAvailable();
    }

    @Override
    public CompletableFuture<Optional<LoreEntryDTO>> getLoreForQuest(String questId) {
        // Quest lore uses naming pattern: quest_<questId>
        return getLoreByName("quest_" + questId);
    }

    @Override
    public CompletableFuture<Optional<LoreEntryDTO>> getLoreByName(String loreName) {
        if (!facade.isAvailable()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.supplyAsync(() -> facade.getLoreEntryByName(loreName));
    }

    @Override
    public CompletableFuture<Optional<LoreEntryDTO>> getLoreById(UUID loreId) {
        if (!facade.isAvailable()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.supplyAsync(() -> facade.getLoreEntryById(loreId));
    }

    @Override
    public CompletableFuture<Boolean> grantLoreDiscovery(UUID playerId, String loreId) {
        if (!facade.isAvailable()) {
            logger.debug("Cannot grant lore discovery - RVNKLore unavailable");
            return CompletableFuture.completedFuture(false);
        }
        // #1650: this used to return true having written nothing, so a LORE reward reported success
        // to the dispatcher and to the player while no row was ever created. A silent false success
        // is worse than a failure, because nothing upstream can detect it. It now persists through
        // RVNKLore's IDiscoveryService, which handles offline players too (RVNKQuests #1983).
        if (!facade.isDiscoveryAvailable()) {
            logger.warning("LORE reward not persisted for " + playerId + " (" + loreId + ") - "
                    + "RVNKLore does not expose IDiscoveryService; needs RVNKLore 1.0.133+");
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() ->
                facade.grantDiscovery(playerId, loreId, "QUEST_COMPLETE"));
    }

    @Override
    public CompletableFuture<Optional<String>> getNPCDialogue(String npcId, String context) {
        // NPC dialogue uses naming pattern: npc_<npcId>_<context>
        return getLoreByName("npc_" + npcId + "_" + context)
            .thenApply(opt -> opt.map(LoreEntryDTO::description));
    }

    @Override
    public CompletableFuture<String> getNPCDialogueOrDefault(String npcId, String context, String fallback) {
        return getNPCDialogue(npcId, context)
            .thenApply(opt -> opt.orElse(fallback));
    }

    @Override
    public CompletableFuture<Boolean> hasDiscovered(UUID playerId, String loreId) {
        if (!facade.isAvailable() || !facade.isDiscoveryAvailable()) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> facade.hasDiscovered(playerId, loreId));
    }

    @Override
    public CompletableFuture<List<String>> getPlayerDiscoveries(UUID playerId) {
        if (!facade.isAvailable() || !facade.isDiscoveryAvailable()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        return CompletableFuture.supplyAsync(() -> facade.getDiscoveredEntryIds(playerId));
    }

    @Override
    public CompletableFuture<Optional<ItemStack>> getOrCreateQuestBook(
            String questItemKey, String title, String description) {
        return CompletableFuture.supplyAsync(() -> facade.getOrCreateQuestBook(questItemKey, title, description));
    }

    @Override
    public CompletableFuture<Optional<ItemStack>> rollRngItem(String poolId, String rarityTier) {
        return CompletableFuture.supplyAsync(() -> facade.rollRngItem(poolId, rarityTier));
    }

    @Override
    public CompletableFuture<Optional<ItemStack>> spawnItemByName(String name) {
        return CompletableFuture.supplyAsync(() -> facade.createLoreItemByName(name));
    }

    @Override
    public CompletableFuture<Optional<ItemStack>> spawnItemById(int itemId) {
        return CompletableFuture.supplyAsync(() -> facade.createLoreItemById(itemId));
    }

    @Override
    public CompletableFuture<List<ItemStack>> getQuestPresetItems(String questId) {
        return CompletableFuture.supplyAsync(() -> facade.getQuestPresetItems(questId));
    }

    @Override
    public String resolveItemId(ItemStack item) {
        return facade.resolveItemId(item);
    }
}
