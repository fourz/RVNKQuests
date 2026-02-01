package org.fourz.RVNKQuests.service;

import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.dto.JournalEntryDTO;
import org.fourz.RVNKQuests.data.dto.JournalEntryDTO.JournalAction;
import org.fourz.RVNKQuests.data.repository.IJournalRepository;
import org.fourz.RVNKQuests.data.repository.JournalRepositoryImpl;
import org.fourz.RVNKQuests.journal.QuestStatistics;
import org.fourz.rvnkcore.util.log.LogManager;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of IJournalService for quest journal management.
 *
 * <p>Provides high-level operations for recording quest events and
 * computing player statistics. Delegates persistence to IJournalRepository.</p>
 *
 * <p>Pattern compliance:</p>
 * <ul>
 *   <li>Async-first with CompletableFuture returns</li>
 *   <li>Uses LogManager for comprehensive logging</li>
 *   <li>Thread-safe for concurrent access</li>
 *   <li>Validates inputs before delegating to repository</li>
 * </ul>
 */
public class JournalServiceImpl implements IJournalService {

    private final RVNKQuests plugin;
    private final LogManager logger;
    private final IJournalRepository journalRepository;

    /**
     * Creates a new JournalServiceImpl.
     *
     * @param plugin The plugin instance
     */
    public JournalServiceImpl(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "JournalService");
        this.journalRepository = new JournalRepositoryImpl(plugin, plugin.getDatabaseManager());
        logger.info("JournalService initialized");
    }

    /**
     * Creates a new JournalServiceImpl with custom repository.
     * Useful for testing with mock repository.
     *
     * @param plugin The plugin instance
     * @param journalRepository The journal repository
     */
    public JournalServiceImpl(RVNKQuests plugin, IJournalRepository journalRepository) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "JournalService");
        this.journalRepository = journalRepository;
        logger.info("JournalService initialized with custom repository");
    }

    // ==================== Journal Entry Recording ====================

    @Override
    public CompletableFuture<JournalEntryDTO> recordQuestStart(UUID playerUuid, String questId) {
        return recordQuestStart(playerUuid, questId, null);
    }

    @Override
    public CompletableFuture<JournalEntryDTO> recordQuestStart(UUID playerUuid, String questId, String details) {
        return recordAction(playerUuid, questId, JournalAction.STARTED, details);
    }

    @Override
    public CompletableFuture<JournalEntryDTO> recordQuestComplete(UUID playerUuid, String questId) {
        return recordQuestComplete(playerUuid, questId, null);
    }

    @Override
    public CompletableFuture<JournalEntryDTO> recordQuestComplete(UUID playerUuid, String questId, String details) {
        return recordAction(playerUuid, questId, JournalAction.COMPLETED, details);
    }

    @Override
    public CompletableFuture<JournalEntryDTO> recordQuestAbandon(UUID playerUuid, String questId) {
        return recordQuestAbandon(playerUuid, questId, null);
    }

    @Override
    public CompletableFuture<JournalEntryDTO> recordQuestAbandon(UUID playerUuid, String questId, String reason) {
        return recordAction(playerUuid, questId, JournalAction.ABANDONED, reason);
    }

    @Override
    public CompletableFuture<JournalEntryDTO> recordQuestFailed(UUID playerUuid, String questId) {
        return recordQuestFailed(playerUuid, questId, null);
    }

    @Override
    public CompletableFuture<JournalEntryDTO> recordQuestFailed(UUID playerUuid, String questId, String reason) {
        return recordAction(playerUuid, questId, JournalAction.FAILED, reason);
    }

    @Override
    public CompletableFuture<JournalEntryDTO> recordObjectiveComplete(
            UUID playerUuid, String questId, String objectiveId) {
        String details = "Objective: " + objectiveId;
        return recordAction(playerUuid, questId, JournalAction.OBJECTIVE_COMPLETE, details);
    }

    @Override
    public CompletableFuture<JournalEntryDTO> recordPathChoice(
            UUID playerUuid, String questId, String pathChoice) {
        String details = "Path: " + pathChoice;
        return recordAction(playerUuid, questId, JournalAction.PATH_CHOSEN, details);
    }

    @Override
    public CompletableFuture<JournalEntryDTO> recordRewardClaimed(
            UUID playerUuid, String questId, String rewardDetails) {
        return recordAction(playerUuid, questId, JournalAction.REWARD_CLAIMED, rewardDetails);
    }

    @Override
    public CompletableFuture<JournalEntryDTO> recordAction(
            UUID playerUuid, String questId, JournalAction action, String details) {
        // Validate inputs
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(questId, "questId cannot be null");
        Objects.requireNonNull(action, "action cannot be null");

        logger.debug("Recording journal action: " + action + " for player " + playerUuid + " quest " + questId);

        // Create journal entry
        JournalEntryDTO entry = JournalEntryDTO.create(playerUuid, questId, action, details);

        // Save to repository
        return journalRepository.save(entry)
            .whenComplete((savedEntry, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to record journal action: " + action + " for player " + playerUuid, throwable);
                } else {
                    logger.debug("Successfully recorded journal entry ID: " + savedEntry.id());
                }
            });
    }

    // ==================== Journal Retrieval ====================

    @Override
    public CompletableFuture<List<JournalEntryDTO>> getPlayerJournal(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        logger.debug("Retrieving journal for player: " + playerUuid);
        return journalRepository.findByPlayer(playerUuid);
    }

    @Override
    public CompletableFuture<List<JournalEntryDTO>> getQuestJournal(UUID playerUuid, String questId) {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(questId, "questId cannot be null");
        logger.debug("Retrieving journal for player " + playerUuid + " quest " + questId);
        return journalRepository.findByPlayerAndQuest(playerUuid, questId);
    }

    @Override
    public CompletableFuture<List<JournalEntryDTO>> getRecentJournal(UUID playerUuid, int limit) {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        if (limit < 1) {
            limit = 10; // Default limit
        }
        logger.debug("Retrieving recent " + limit + " journal entries for player: " + playerUuid);
        return journalRepository.findRecentByPlayer(playerUuid, limit);
    }

    @Override
    public CompletableFuture<List<JournalEntryDTO>> getJournalByAction(UUID playerUuid, JournalAction action) {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
        logger.debug("Retrieving journal entries for player " + playerUuid + " action " + action);
        return journalRepository.findByPlayerAndAction(playerUuid, action);
    }

    @Override
    public CompletableFuture<List<JournalEntryDTO>> getJournalByTimeRange(
            UUID playerUuid, Instant startTime, Instant endTime) {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(startTime, "startTime cannot be null");
        Objects.requireNonNull(endTime, "endTime cannot be null");

        if (endTime.isBefore(startTime)) {
            logger.warning("endTime is before startTime, swapping values");
            Instant temp = startTime;
            startTime = endTime;
            endTime = temp;
        }

        logger.debug("Retrieving journal entries for player " + playerUuid +
                     " between " + startTime + " and " + endTime);
        return journalRepository.findByPlayerAndTimeRange(playerUuid, startTime, endTime);
    }

    // ==================== Statistics ====================

    @Override
    public CompletableFuture<QuestStatistics> getPlayerStatistics(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        logger.debug("Computing statistics for player: " + playerUuid);

        return getPlayerJournal(playerUuid)
            .thenApply(entries -> {
                QuestStatistics stats = QuestStatistics.fromJournalEntries(playerUuid, entries);
                logger.debug("Computed statistics for player " + playerUuid +
                            ": " + stats.totalCompleted() + " completed, " +
                            stats.totalStarted() + " started");
                return stats;
            });
    }

    @Override
    public CompletableFuture<Long> getEntryCount(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        return journalRepository.countByPlayer(playerUuid);
    }

    // ==================== Maintenance ====================

    @Override
    public CompletableFuture<Integer> clearPlayerJournal(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        logger.info("Clearing journal for player: " + playerUuid);

        return journalRepository.deleteByPlayer(playerUuid)
            .whenComplete((deleted, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to clear journal for player " + playerUuid, throwable);
                } else {
                    logger.info("Cleared " + deleted + " journal entries for player " + playerUuid);
                }
            });
    }

    @Override
    public CompletableFuture<Integer> clearQuestJournal(UUID playerUuid, String questId) {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(questId, "questId cannot be null");
        logger.info("Clearing journal for player " + playerUuid + " quest " + questId);

        return journalRepository.deleteByPlayerAndQuest(playerUuid, questId)
            .whenComplete((deleted, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to clear quest journal for player " + playerUuid +
                                " quest " + questId, throwable);
                } else {
                    logger.debug("Cleared " + deleted + " journal entries for player " + playerUuid +
                                " quest " + questId);
                }
            });
    }

    @Override
    public CompletableFuture<Integer> purgeOldEntries(Instant beforeTime) {
        Objects.requireNonNull(beforeTime, "beforeTime cannot be null");
        logger.info("Purging journal entries older than: " + beforeTime);

        return journalRepository.deleteOlderThan(beforeTime)
            .whenComplete((deleted, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to purge old journal entries", throwable);
                } else {
                    logger.info("Purged " + deleted + " old journal entries");
                }
            });
    }

    @Override
    public boolean isAvailable() {
        return journalRepository.isAvailable();
    }
}
