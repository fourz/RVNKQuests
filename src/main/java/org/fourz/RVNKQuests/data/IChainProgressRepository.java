package org.fourz.RVNKQuests.data;

import org.fourz.RVNKQuests.data.dto.QuestChainProgressDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for quest chain progress persistence.
 *
 * <p>All methods return {@link CompletableFuture} so callers never block
 * the Minecraft main thread. The in-memory map in
 * {@code QuestChainServiceImpl} acts as the read cache; this repository
 * is the write-through store.</p>
 *
 * @since 1.1
 * @see QuestChainProgressDTO
 */
public interface IChainProgressRepository {

    /**
     * Loads a player's progress for a specific chain.
     *
     * @param playerUuid The player's UUID
     * @param chainId    The chain identifier
     * @return CompletableFuture with the progress record, or empty if not found
     */
    CompletableFuture<Optional<QuestChainProgressDTO>> loadProgress(UUID playerUuid, String chainId);

    /**
     * Loads all chain progress records for a player.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture with the list of all progress records (may be empty)
     */
    CompletableFuture<List<QuestChainProgressDTO>> loadAllProgress(UUID playerUuid);

    /**
     * Saves (inserts or updates) a chain progress record.
     *
     * @param progress The progress record to persist
     * @return CompletableFuture completing with {@code true} if the write succeeded
     */
    CompletableFuture<Boolean> saveProgress(QuestChainProgressDTO progress);

    /**
     * Deletes a player's progress for a specific chain.
     *
     * @param playerUuid The player's UUID
     * @param chainId    The chain identifier
     * @return CompletableFuture completing with {@code true} if a row was deleted
     */
    CompletableFuture<Boolean> deleteProgress(UUID playerUuid, String chainId);
}
