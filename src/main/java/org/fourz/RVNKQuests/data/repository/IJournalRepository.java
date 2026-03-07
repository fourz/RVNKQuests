package org.fourz.RVNKQuests.data.repository;

import org.fourz.RVNKQuests.data.dto.JournalEntryDTO;
import org.fourz.RVNKQuests.data.dto.JournalEntryDTO.JournalAction;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for quest journal entry persistence.
 *
 * <p>Provides async data access operations for journal entries with
 * filtering and querying capabilities.</p>
 */
public interface IJournalRepository {

    /**
     * Save a new journal entry.
     *
     * @param entry The journal entry to save
     * @return CompletableFuture with the saved entry (with generated ID)
     */
    CompletableFuture<JournalEntryDTO> save(JournalEntryDTO entry);

    /**
     * Get all journal entries for a player.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture with list of journal entries
     */
    CompletableFuture<List<JournalEntryDTO>> findByPlayer(UUID playerUuid);

    /**
     * Get journal entries for a specific quest.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return CompletableFuture with list of journal entries
     */
    CompletableFuture<List<JournalEntryDTO>> findByPlayerAndQuest(UUID playerUuid, String questId);

    /**
     * Get journal entries filtered by action type.
     *
     * @param playerUuid The player's UUID
     * @param action The journal action to filter by
     * @return CompletableFuture with list of journal entries
     */
    CompletableFuture<List<JournalEntryDTO>> findByPlayerAndAction(UUID playerUuid, JournalAction action);

    /**
     * Get journal entries within a time range.
     *
     * @param playerUuid The player's UUID
     * @param startTime The start of the time range
     * @param endTime The end of the time range
     * @return CompletableFuture with list of journal entries
     */
    CompletableFuture<List<JournalEntryDTO>> findByPlayerAndTimeRange(
        UUID playerUuid, Instant startTime, Instant endTime);

    /**
     * Get the most recent journal entries for a player.
     *
     * @param playerUuid The player's UUID
     * @param limit Maximum number of entries to return
     * @return CompletableFuture with list of journal entries
     */
    CompletableFuture<List<JournalEntryDTO>> findRecentByPlayer(UUID playerUuid, int limit);

    /**
     * Delete all journal entries for a player.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture with number of entries deleted
     */
    CompletableFuture<Integer> deleteByPlayer(UUID playerUuid);

    /**
     * Delete journal entries for a specific quest.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return CompletableFuture with number of entries deleted
     */
    CompletableFuture<Integer> deleteByPlayerAndQuest(UUID playerUuid, String questId);

    /**
     * Delete journal entries older than a specified time.
     *
     * @param beforeTime Delete entries before this time
     * @return CompletableFuture with number of entries deleted
     */
    CompletableFuture<Integer> deleteOlderThan(Instant beforeTime);

    /**
     * Count total journal entries for a player.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture with count of entries
     */
    CompletableFuture<Long> countByPlayer(UUID playerUuid);

    /**
     * Check if the repository is available.
     *
     * @return true if repository can perform operations
     */
    boolean isAvailable();
}
