package org.fourz.RVNKQuests.service;

import org.fourz.RVNKQuests.data.dto.JournalEntryDTO;
import org.fourz.RVNKQuests.data.dto.JournalEntryDTO.JournalAction;
import org.fourz.RVNKQuests.journal.QuestStatistics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for quest journal management and statistics.
 *
 * <p>Provides high-level operations for recording quest events,
 * retrieving journal history, and computing player statistics.</p>
 *
 * <p>Pattern compliance:</p>
 * <ul>
 *   <li>Interface uses "I" prefix per RVNK coding standards</li>
 *   <li>All database operations return CompletableFuture for async execution</li>
 *   <li>Delegates persistence to IJournalRepository</li>
 * </ul>
 */
public interface IJournalService {

    // ==================== Journal Entry Recording ====================

    /**
     * Records a quest start event in the player's journal.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return CompletableFuture with the created journal entry
     */
    CompletableFuture<JournalEntryDTO> recordQuestStart(UUID playerUuid, String questId);

    /**
     * Records a quest start event with additional details.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param details Additional details about the start event
     * @return CompletableFuture with the created journal entry
     */
    CompletableFuture<JournalEntryDTO> recordQuestStart(UUID playerUuid, String questId, String details);

    /**
     * Records a quest completion event in the player's journal.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return CompletableFuture with the created journal entry
     */
    CompletableFuture<JournalEntryDTO> recordQuestComplete(UUID playerUuid, String questId);

    /**
     * Records a quest completion event with additional details.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param details Additional details about the completion
     * @return CompletableFuture with the created journal entry
     */
    CompletableFuture<JournalEntryDTO> recordQuestComplete(UUID playerUuid, String questId, String details);

    /**
     * Records a quest abandonment event in the player's journal.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return CompletableFuture with the created journal entry
     */
    CompletableFuture<JournalEntryDTO> recordQuestAbandon(UUID playerUuid, String questId);

    /**
     * Records a quest abandonment event with reason.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param reason Reason for abandonment
     * @return CompletableFuture with the created journal entry
     */
    CompletableFuture<JournalEntryDTO> recordQuestAbandon(UUID playerUuid, String questId, String reason);

    /**
     * Records a quest failure event in the player's journal.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return CompletableFuture with the created journal entry
     */
    CompletableFuture<JournalEntryDTO> recordQuestFailed(UUID playerUuid, String questId);

    /**
     * Records a quest failure event with reason.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param reason Failure reason
     * @return CompletableFuture with the created journal entry
     */
    CompletableFuture<JournalEntryDTO> recordQuestFailed(UUID playerUuid, String questId, String reason);

    /**
     * Records an objective completion event in the player's journal.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param objectiveId The objective identifier
     * @return CompletableFuture with the created journal entry
     */
    CompletableFuture<JournalEntryDTO> recordObjectiveComplete(
        UUID playerUuid, String questId, String objectiveId);

    /**
     * Records a quest path choice event in the player's journal.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param pathChoice The chosen path
     * @return CompletableFuture with the created journal entry
     */
    CompletableFuture<JournalEntryDTO> recordPathChoice(
        UUID playerUuid, String questId, String pathChoice);

    /**
     * Records a reward claim event in the player's journal.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param rewardDetails Details about the claimed reward
     * @return CompletableFuture with the created journal entry
     */
    CompletableFuture<JournalEntryDTO> recordRewardClaimed(
        UUID playerUuid, String questId, String rewardDetails);

    /**
     * Records a custom journal action.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param action The journal action type
     * @param details Optional details
     * @return CompletableFuture with the created journal entry
     */
    CompletableFuture<JournalEntryDTO> recordAction(
        UUID playerUuid, String questId, JournalAction action, String details);

    // ==================== Journal Retrieval ====================

    /**
     * Gets all journal entries for a player.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture with list of journal entries
     */
    CompletableFuture<List<JournalEntryDTO>> getPlayerJournal(UUID playerUuid);

    /**
     * Gets journal entries for a specific quest.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return CompletableFuture with list of journal entries
     */
    CompletableFuture<List<JournalEntryDTO>> getQuestJournal(UUID playerUuid, String questId);

    /**
     * Gets recent journal entries for a player.
     *
     * @param playerUuid The player's UUID
     * @param limit Maximum number of entries to return
     * @return CompletableFuture with list of journal entries
     */
    CompletableFuture<List<JournalEntryDTO>> getRecentJournal(UUID playerUuid, int limit);

    /**
     * Gets journal entries filtered by action type.
     *
     * @param playerUuid The player's UUID
     * @param action The journal action to filter by
     * @return CompletableFuture with list of journal entries
     */
    CompletableFuture<List<JournalEntryDTO>> getJournalByAction(UUID playerUuid, JournalAction action);

    /**
     * Gets journal entries within a time range.
     *
     * @param playerUuid The player's UUID
     * @param startTime The start of the time range
     * @param endTime The end of the time range
     * @return CompletableFuture with list of journal entries
     */
    CompletableFuture<List<JournalEntryDTO>> getJournalByTimeRange(
        UUID playerUuid, Instant startTime, Instant endTime);

    // ==================== Statistics ====================

    /**
     * Computes aggregated statistics from a player's journal.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture with computed statistics
     */
    CompletableFuture<QuestStatistics> getPlayerStatistics(UUID playerUuid);

    /**
     * Gets the total number of journal entries for a player.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture with entry count
     */
    CompletableFuture<Long> getEntryCount(UUID playerUuid);

    // ==================== Maintenance ====================

    /**
     * Deletes all journal entries for a player.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture with number of entries deleted
     */
    CompletableFuture<Integer> clearPlayerJournal(UUID playerUuid);

    /**
     * Deletes journal entries for a specific quest.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return CompletableFuture with number of entries deleted
     */
    CompletableFuture<Integer> clearQuestJournal(UUID playerUuid, String questId);

    /**
     * Deletes journal entries older than a specified time.
     *
     * @param beforeTime Delete entries before this time
     * @return CompletableFuture with number of entries deleted
     */
    CompletableFuture<Integer> purgeOldEntries(Instant beforeTime);

    /**
     * Checks if the journal service is available and operational.
     *
     * @return true if service can perform operations
     */
    boolean isAvailable();
}
