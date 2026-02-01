package org.fourz.RVNKQuests.data.repository;

import org.fourz.RVNKQuests.data.dto.LeaderboardEntryDTO;
import org.fourz.RVNKQuests.data.dto.LeaderboardEntryDTO.LeaderboardType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for quest leaderboard persistence.
 *
 * <p>Provides async data access operations for leaderboard entries with
 * ranking and competitive tracking capabilities.</p>
 */
public interface ILeaderboardRepository {

    /**
     * Save or update a leaderboard entry.
     *
     * @param entry The leaderboard entry to save
     * @return CompletableFuture with the saved entry
     */
    CompletableFuture<LeaderboardEntryDTO> save(LeaderboardEntryDTO entry);

    /**
     * Find a leaderboard entry for a specific player and type.
     *
     * @param playerUuid The player's UUID
     * @param leaderboardType The leaderboard type
     * @return CompletableFuture with optional leaderboard entry
     */
    CompletableFuture<Optional<LeaderboardEntryDTO>> findByPlayerAndType(
        UUID playerUuid, LeaderboardType leaderboardType);

    /**
     * Get top entries for a leaderboard type.
     *
     * @param leaderboardType The leaderboard type
     * @param limit Maximum number of entries to return
     * @return CompletableFuture with list of top entries ordered by score descending
     */
    CompletableFuture<List<LeaderboardEntryDTO>> findTopByType(LeaderboardType leaderboardType, int limit);

    /**
     * Get all entries for a specific leaderboard type.
     *
     * @param leaderboardType The leaderboard type
     * @return CompletableFuture with list of entries ordered by score descending
     */
    CompletableFuture<List<LeaderboardEntryDTO>> findAllByType(LeaderboardType leaderboardType);

    /**
     * Get all leaderboard entries for a player.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture with list of entries
     */
    CompletableFuture<List<LeaderboardEntryDTO>> findByPlayer(UUID playerUuid);

    /**
     * Get player rank on a specific leaderboard.
     * Returns 0 if player not found on leaderboard.
     *
     * @param playerUuid The player's UUID
     * @param leaderboardType The leaderboard type
     * @return CompletableFuture with rank (1-based index)
     */
    CompletableFuture<Integer> getPlayerRank(UUID playerUuid, LeaderboardType leaderboardType);

    /**
     * Get entries within a score range for a leaderboard.
     *
     * @param leaderboardType The leaderboard type
     * @param minScore Minimum score (inclusive)
     * @param maxScore Maximum score (inclusive)
     * @return CompletableFuture with list of entries
     */
    CompletableFuture<List<LeaderboardEntryDTO>> findByTypeAndScoreRange(
        LeaderboardType leaderboardType, long minScore, long maxScore);

    /**
     * Increment a player's score on a leaderboard.
     *
     * @param playerUuid The player's UUID
     * @param leaderboardType The leaderboard type
     * @param amount Amount to increment by
     * @return CompletableFuture with updated entry
     */
    CompletableFuture<LeaderboardEntryDTO> incrementScore(
        UUID playerUuid, LeaderboardType leaderboardType, long amount);

    /**
     * Reset a player's score on a leaderboard.
     *
     * @param playerUuid The player's UUID
     * @param leaderboardType The leaderboard type
     * @return CompletableFuture with true if reset succeeded
     */
    CompletableFuture<Boolean> resetScore(UUID playerUuid, LeaderboardType leaderboardType);

    /**
     * Reset all entries for a specific leaderboard type.
     *
     * @param leaderboardType The leaderboard type
     * @return CompletableFuture with number of entries reset
     */
    CompletableFuture<Integer> resetLeaderboard(LeaderboardType leaderboardType);

    /**
     * Delete entries last updated before a specified time.
     * Useful for cleaning up inactive entries.
     *
     * @param leaderboardType The leaderboard type
     * @param beforeTime Delete entries last updated before this time
     * @return CompletableFuture with number of entries deleted
     */
    CompletableFuture<Integer> deleteInactiveEntries(LeaderboardType leaderboardType, Instant beforeTime);

    /**
     * Delete all leaderboard entries for a player.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture with number of entries deleted
     */
    CompletableFuture<Integer> deleteByPlayer(UUID playerUuid);

    /**
     * Count total entries on a leaderboard.
     *
     * @param leaderboardType The leaderboard type
     * @return CompletableFuture with count of entries
     */
    CompletableFuture<Long> countByType(LeaderboardType leaderboardType);

    /**
     * Get entries around a player's rank (for context display).
     *
     * @param playerUuid The player's UUID
     * @param leaderboardType The leaderboard type
     * @param contextSize Number of entries above and below player
     * @return CompletableFuture with list of entries around player
     */
    CompletableFuture<List<LeaderboardEntryDTO>> findAroundPlayer(
        UUID playerUuid, LeaderboardType leaderboardType, int contextSize);

    /**
     * Check if the repository is available.
     *
     * @return true if repository can perform operations
     */
    boolean isAvailable();
}
