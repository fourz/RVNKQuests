package org.fourz.RVNKQuests.service;

import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.DatabaseManager;
import org.fourz.RVNKQuests.data.IQuestProgressRepository;
import org.fourz.RVNKQuests.data.QuestProgressRepositoryImpl;
import org.fourz.RVNKQuests.data.QuestProgressYamlRepository;
import org.fourz.RVNKQuests.data.dto.QuestObjectiveProgressDTO;
import org.fourz.RVNKQuests.data.dto.QuestProgressDTO;
import org.fourz.RVNKQuests.data.dto.QuestRewardClaimedDTO;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service implementation for quest progress management.
 *
 * <p>Provides caching layer over repository with automatic fallback
 * between SQL and YAML storage.</p>
 */
public class QuestProgressServiceImpl implements IQuestProgressService {

    private final RVNKQuests plugin;
    private final LogManager logger;
    private final DatabaseManager databaseManager;

    private IQuestProgressRepository primaryRepo;
    private final QuestProgressYamlRepository fallbackRepo;

    // In-memory cache for online players
    private final Map<UUID, Map<String, QuestProgressDTO>> progressCache = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Map<String, QuestObjectiveProgressDTO>>> objectiveCache = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, java.util.Set<String>>> rewardCache = new ConcurrentHashMap<>();

    // Autosave scheduler
    private final ScheduledExecutorService autosaveScheduler;
    private final int autosaveIntervalSeconds;

    /**
     * Creates a new quest progress service.
     *
     * @param plugin The plugin instance
     * @param databaseManager The database manager
     */
    public QuestProgressServiceImpl(RVNKQuests plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "QuestProgressService");
        this.databaseManager = databaseManager;

        // Initialize repositories
        this.fallbackRepo = new QuestProgressYamlRepository(plugin);

        if (databaseManager.isAvailable()) {
            this.primaryRepo = new QuestProgressRepositoryImpl(plugin, databaseManager);
            logger.info("Using SQL repository as primary storage");
        } else {
            this.primaryRepo = fallbackRepo;
            logger.info("Using YAML repository (database not available)");
        }

        // Setup autosave
        this.autosaveIntervalSeconds = plugin.getConfigManager().getConfig()
            .getInt("database.autosave_interval", 300);
        this.autosaveScheduler = Executors.newSingleThreadScheduledExecutor();
        startAutosave();
    }

    /**
     * Gets the active repository, falling back to YAML if in fallback mode.
     */
    private IQuestProgressRepository getActiveRepo() {
        if (primaryRepo.isInFallbackMode()) {
            return fallbackRepo;
        }
        return primaryRepo;
    }

    // ==================== Player Session Management ====================

    @Override
    public CompletableFuture<Void> loadPlayerProgress(UUID playerUuid) {
        logger.debug("Loading progress for player: " + playerUuid);

        return getActiveRepo().getAllProgressForPlayer(playerUuid)
            .thenCompose(progressList -> {
                // Cache quest progress
                Map<String, QuestProgressDTO> playerProgress = new ConcurrentHashMap<>();
                for (QuestProgressDTO p : progressList) {
                    playerProgress.put(p.questId(), p);
                }
                progressCache.put(playerUuid, playerProgress);

                // Load objectives for each quest
                List<CompletableFuture<Void>> objectiveFutures = progressList.stream()
                    .map(p -> loadObjectivesForQuest(playerUuid, p.questId()))
                    .toList();

                return CompletableFuture.allOf(objectiveFutures.toArray(new CompletableFuture[0]));
            })
            .thenRun(() -> logger.debug("Loaded progress for player: " + playerUuid));
    }

    private CompletableFuture<Void> loadObjectivesForQuest(UUID playerUuid, String questId) {
        return getActiveRepo().getAllObjectiveProgress(playerUuid, questId)
            .thenAccept(objectives -> {
                objectiveCache.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(questId, k -> new ConcurrentHashMap<>());

                Map<String, QuestObjectiveProgressDTO> questObjectives =
                    objectiveCache.get(playerUuid).get(questId);

                for (QuestObjectiveProgressDTO obj : objectives) {
                    questObjectives.put(obj.objectiveId(), obj);
                }
            });
    }

    @Override
    public CompletableFuture<Void> saveAndUnloadPlayerProgress(UUID playerUuid) {
        logger.debug("Saving and unloading progress for player: " + playerUuid);

        // Save all cached progress
        Map<String, QuestProgressDTO> playerProgress = progressCache.get(playerUuid);
        if (playerProgress == null || playerProgress.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Boolean>> saveFutures = playerProgress.values().stream()
            .map(progress -> getActiveRepo().saveProgress(progress))
            .toList();

        // Save objectives
        Map<String, Map<String, QuestObjectiveProgressDTO>> playerObjectives = objectiveCache.get(playerUuid);
        if (playerObjectives != null) {
            for (Map<String, QuestObjectiveProgressDTO> questObjectives : playerObjectives.values()) {
                for (QuestObjectiveProgressDTO obj : questObjectives.values()) {
                    saveFutures = new java.util.ArrayList<>(saveFutures);
                    saveFutures.add(getActiveRepo().saveObjectiveProgress(obj));
                }
            }
        }

        return CompletableFuture.allOf(saveFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                // Clear caches
                progressCache.remove(playerUuid);
                objectiveCache.remove(playerUuid);
                rewardCache.remove(playerUuid);

                // If using YAML, unload from there too
                if (getActiveRepo() instanceof QuestProgressYamlRepository yamlRepo) {
                    yamlRepo.unloadPlayerData(playerUuid);
                }

                logger.debug("Saved and unloaded progress for player: " + playerUuid);
            });
    }

    // ==================== Quest State Operations ====================

    @Override
    public CompletableFuture<Optional<QuestProgressDTO>> getProgress(UUID playerUuid, String questId) {
        // Check cache first
        Map<String, QuestProgressDTO> playerProgress = progressCache.get(playerUuid);
        if (playerProgress != null && playerProgress.containsKey(questId)) {
            return CompletableFuture.completedFuture(Optional.of(playerProgress.get(questId)));
        }

        // Fallback to repository
        return getActiveRepo().getProgress(playerUuid, questId);
    }

    @Override
    public CompletableFuture<QuestState> getQuestState(UUID playerUuid, String questId) {
        return getProgress(playerUuid, questId)
            .thenApply(opt -> opt.map(QuestProgressDTO::state).orElse(QuestState.NOT_STARTED));
    }

    @Override
    public CompletableFuture<QuestProgressDTO> updateQuestState(UUID playerUuid, String questId, QuestState newState) {
        return getProgress(playerUuid, questId)
            .thenCompose(opt -> {
                QuestProgressDTO progress = opt.orElse(QuestProgressDTO.createNew(playerUuid, questId));
                QuestProgressDTO updated = progress.withState(newState);

                // Update cache
                progressCache.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                    .put(questId, updated);

                // Save to repository
                return getActiveRepo().saveProgress(updated).thenApply(success -> updated);
            });
    }

    @Override
    public CompletableFuture<QuestProgressDTO> setPathChoice(UUID playerUuid, String questId, String pathChoice) {
        return getProgress(playerUuid, questId)
            .thenCompose(opt -> {
                QuestProgressDTO progress = opt.orElse(QuestProgressDTO.createNew(playerUuid, questId));
                QuestProgressDTO updated = progress.withPathChoice(pathChoice);

                // Update cache
                progressCache.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                    .put(questId, updated);

                // Record journal entry
                IJournalService journal = plugin.getJournalService();
                if (journal != null && journal.isAvailable()) {
                    journal.recordPathChoice(playerUuid, questId, pathChoice);
                }

                // Save to repository
                return getActiveRepo().saveProgress(updated).thenApply(success -> updated);
            });
    }

    @Override
    public CompletableFuture<Boolean> resetQuestProgress(UUID playerUuid, String questId) {
        // Clear from caches
        Map<String, QuestProgressDTO> playerProgress = progressCache.get(playerUuid);
        if (playerProgress != null) {
            playerProgress.remove(questId);
        }

        Map<String, Map<String, QuestObjectiveProgressDTO>> playerObjectives = objectiveCache.get(playerUuid);
        if (playerObjectives != null) {
            playerObjectives.remove(questId);
        }

        // Delete from repository
        return getActiveRepo().deleteProgress(playerUuid, questId)
            .thenCompose(deleted -> getActiveRepo().deleteObjectiveProgress(playerUuid, questId));
    }

    @Override
    public CompletableFuture<List<QuestProgressDTO>> getAllProgress(UUID playerUuid) {
        Map<String, QuestProgressDTO> playerProgress = progressCache.get(playerUuid);
        if (playerProgress != null && !playerProgress.isEmpty()) {
            return CompletableFuture.completedFuture(new java.util.ArrayList<>(playerProgress.values()));
        }
        return getActiveRepo().getAllProgressForPlayer(playerUuid);
    }

    // ==================== Objective Operations ====================

    @Override
    public CompletableFuture<Optional<QuestObjectiveProgressDTO>> getObjectiveProgress(
            UUID playerUuid, String questId, String objectiveId) {

        // Check cache
        Map<String, Map<String, QuestObjectiveProgressDTO>> playerObjectives = objectiveCache.get(playerUuid);
        if (playerObjectives != null) {
            Map<String, QuestObjectiveProgressDTO> questObjectives = playerObjectives.get(questId);
            if (questObjectives != null && questObjectives.containsKey(objectiveId)) {
                return CompletableFuture.completedFuture(Optional.of(questObjectives.get(objectiveId)));
            }
        }

        return getActiveRepo().getObjectiveProgress(playerUuid, questId, objectiveId);
    }

    @Override
    public CompletableFuture<QuestObjectiveProgressDTO> incrementObjectiveProgress(
            UUID playerUuid, String questId, String objectiveId, int amount) {

        return getObjectiveProgress(playerUuid, questId, objectiveId)
            .thenCompose(opt -> {
                if (opt.isEmpty()) {
                    logger.warning("Attempted to increment non-existent objective: " + objectiveId);
                    return CompletableFuture.completedFuture(null);
                }

                QuestObjectiveProgressDTO updated = opt.get().incrementProgress(amount);

                // Update cache
                objectiveCache.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(questId, k -> new ConcurrentHashMap<>())
                    .put(objectiveId, updated);

                // Save to repository
                return getActiveRepo().saveObjectiveProgress(updated).thenApply(success -> updated);
            });
    }

    @Override
    public CompletableFuture<QuestObjectiveProgressDTO> initializeObjective(
            UUID playerUuid, String questId, String objectiveId, int targetCount) {

        QuestObjectiveProgressDTO objective = QuestObjectiveProgressDTO.createNew(
            playerUuid, questId, objectiveId, targetCount);

        // Update cache
        objectiveCache.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(questId, k -> new ConcurrentHashMap<>())
            .put(objectiveId, objective);

        // Save to repository
        return getActiveRepo().saveObjectiveProgress(objective).thenApply(success -> objective);
    }

    @Override
    public CompletableFuture<QuestObjectiveProgressDTO> completeObjective(
            UUID playerUuid, String questId, String objectiveId) {

        return getObjectiveProgress(playerUuid, questId, objectiveId)
            .thenCompose(opt -> {
                if (opt.isEmpty()) {
                    logger.warning("Attempted to complete non-existent objective: " + objectiveId);
                    return CompletableFuture.completedFuture(null);
                }

                QuestObjectiveProgressDTO updated = opt.get().markCompleted();

                // Update cache
                objectiveCache.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(questId, k -> new ConcurrentHashMap<>())
                    .put(objectiveId, updated);

                // Save to repository
                return getActiveRepo().saveObjectiveProgress(updated).thenApply(success -> updated);
            });
    }

    @Override
    public CompletableFuture<List<QuestObjectiveProgressDTO>> getAllObjectives(UUID playerUuid, String questId) {
        Map<String, Map<String, QuestObjectiveProgressDTO>> playerObjectives = objectiveCache.get(playerUuid);
        if (playerObjectives != null) {
            Map<String, QuestObjectiveProgressDTO> questObjectives = playerObjectives.get(questId);
            if (questObjectives != null) {
                return CompletableFuture.completedFuture(new java.util.ArrayList<>(questObjectives.values()));
            }
        }
        return getActiveRepo().getAllObjectiveProgress(playerUuid, questId);
    }

    // ==================== Reward Operations ====================

    @Override
    public CompletableFuture<Boolean> hasClaimedReward(UUID playerUuid, String questId, String rewardId) {
        // Check cache
        Map<String, java.util.Set<String>> playerRewards = rewardCache.get(playerUuid);
        if (playerRewards != null) {
            java.util.Set<String> questRewards = playerRewards.get(questId);
            if (questRewards != null && questRewards.contains(rewardId)) {
                return CompletableFuture.completedFuture(true);
            }
        }

        return getActiveRepo().hasClaimedReward(playerUuid, questId, rewardId);
    }

    @Override
    public CompletableFuture<Boolean> claimReward(UUID playerUuid, String questId, String rewardId) {
        // Check if already claimed
        return hasClaimedReward(playerUuid, questId, rewardId)
            .thenCompose(alreadyClaimed -> {
                if (alreadyClaimed) {
                    logger.debug("Reward already claimed: " + rewardId);
                    return CompletableFuture.completedFuture(false);
                }

                QuestRewardClaimedDTO claim = QuestRewardClaimedDTO.create(playerUuid, questId, rewardId);

                // Update cache
                rewardCache.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(questId, k -> ConcurrentHashMap.newKeySet())
                    .add(rewardId);

                // Save to repository
                return getActiveRepo().saveRewardClaimed(claim);
            });
    }

    // ==================== Utility ====================

    @Override
    public boolean isInFallbackMode() {
        return getActiveRepo().isInFallbackMode();
    }

    @Override
    public CompletableFuture<Void> flush() {
        // Flush all caches to repository
        List<CompletableFuture<?>> futures = new java.util.ArrayList<>();

        for (Map.Entry<UUID, Map<String, QuestProgressDTO>> entry : progressCache.entrySet()) {
            for (QuestProgressDTO progress : entry.getValue().values()) {
                futures.add(getActiveRepo().saveProgress(progress));
            }
        }

        for (Map.Entry<UUID, Map<String, Map<String, QuestObjectiveProgressDTO>>> entry : objectiveCache.entrySet()) {
            for (Map<String, QuestObjectiveProgressDTO> questObjectives : entry.getValue().values()) {
                for (QuestObjectiveProgressDTO obj : questObjectives.values()) {
                    futures.add(getActiveRepo().saveObjectiveProgress(obj));
                }
            }
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenCompose(v -> getActiveRepo().flush());
    }

    @Override
    public void shutdown() {
        // Stop autosave
        autosaveScheduler.shutdown();
        try {
            autosaveScheduler.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Final flush
        flush().join();
    }

    private void startAutosave() {
        if (autosaveIntervalSeconds > 0) {
            autosaveScheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        flush().join();
                        logger.debug("Autosave completed");
                    } catch (Exception e) {
                        logger.error("Autosave failed", e);
                    }
                },
                autosaveIntervalSeconds,
                autosaveIntervalSeconds,
                TimeUnit.SECONDS
            );
            logger.info("Autosave scheduled every " + autosaveIntervalSeconds + " seconds");
        }
    }
}
