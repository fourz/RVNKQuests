package org.fourz.RVNKQuests.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.dto.QuestObjectiveProgressDTO;
import org.fourz.RVNKQuests.data.dto.QuestProgressDTO;
import org.fourz.RVNKQuests.data.dto.QuestRewardClaimedDTO;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * YAML-based fallback implementation of IQuestProgressRepository.
 *
 * <p>Used when database is unavailable. Stores data in YAML files
 * in the plugin data folder.</p>
 */
public class QuestProgressYamlRepository implements IQuestProgressRepository {

    private final RVNKQuests plugin;
    private final LogManager logger;
    private final File dataFolder;

    // In-memory cache for fast access
    private final Map<UUID, Map<String, QuestProgressDTO>> progressCache = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, List<QuestObjectiveProgressDTO>>> objectiveCache = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Set<String>>> rewardsClaimedCache = new ConcurrentHashMap<>();

    private volatile boolean dirty = false;

    /**
     * Creates a new YAML repository.
     *
     * @param plugin The plugin instance
     */
    public QuestProgressYamlRepository(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "QuestProgressYaml");
        this.dataFolder = new File(plugin.getDataFolder(), "data/players");
        this.dataFolder.mkdirs();

        logger.info("YAML fallback repository initialized at: " + dataFolder.getAbsolutePath());
    }

    // ==================== Quest Progress Operations ====================

    @Override
    public CompletableFuture<Boolean> saveProgress(QuestProgressDTO progress) {
        return CompletableFuture.supplyAsync(() -> {
            progressCache.computeIfAbsent(progress.playerUuid(), k -> new ConcurrentHashMap<>())
                .put(progress.questId(), progress);
            dirty = true;
            logger.debug("Cached progress for " + progress.playerUuid() + " on " + progress.questId());
            return true;
        });
    }

    @Override
    public CompletableFuture<Optional<QuestProgressDTO>> getProgress(UUID playerUuid, String questId) {
        return CompletableFuture.supplyAsync(() -> {
            // Try cache first
            Map<String, QuestProgressDTO> playerProgress = progressCache.get(playerUuid);
            if (playerProgress != null && playerProgress.containsKey(questId)) {
                return Optional.of(playerProgress.get(questId));
            }

            // Load from file
            loadPlayerData(playerUuid);
            playerProgress = progressCache.get(playerUuid);
            if (playerProgress != null) {
                return Optional.ofNullable(playerProgress.get(questId));
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<List<QuestProgressDTO>> getAllProgressForPlayer(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            loadPlayerData(playerUuid);
            Map<String, QuestProgressDTO> playerProgress = progressCache.get(playerUuid);
            if (playerProgress != null) {
                return new ArrayList<>(playerProgress.values());
            }
            return new ArrayList<>();
        });
    }

    @Override
    public CompletableFuture<List<QuestProgressDTO>> getAllProgressForQuest(String questId) {
        return CompletableFuture.supplyAsync(() -> {
            List<QuestProgressDTO> results = new ArrayList<>();
            // Load all player files
            File[] playerFiles = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (playerFiles != null) {
                for (File file : playerFiles) {
                    String fileName = file.getName().replace(".yml", "");
                    try {
                        UUID playerUuid = UUID.fromString(fileName);
                        loadPlayerData(playerUuid);
                        Map<String, QuestProgressDTO> playerProgress = progressCache.get(playerUuid);
                        if (playerProgress != null && playerProgress.containsKey(questId)) {
                            results.add(playerProgress.get(questId));
                        }
                    } catch (IllegalArgumentException e) {
                        // Skip invalid UUID files
                    }
                }
            }
            return results;
        });
    }

    @Override
    public CompletableFuture<List<QuestProgressDTO>> getProgressByState(String questId, QuestState state) {
        return getAllProgressForQuest(questId).thenApply(list ->
            list.stream().filter(p -> p.state() == state).toList()
        );
    }

    @Override
    public CompletableFuture<Boolean> deleteProgress(UUID playerUuid, String questId) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, QuestProgressDTO> playerProgress = progressCache.get(playerUuid);
            if (playerProgress != null) {
                playerProgress.remove(questId);
                dirty = true;
                return true;
            }
            return false;
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteAllProgressForPlayer(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            progressCache.remove(playerUuid);
            objectiveCache.remove(playerUuid);
            rewardsClaimedCache.remove(playerUuid);

            // Delete the file
            File playerFile = new File(dataFolder, playerUuid + ".yml");
            if (playerFile.exists()) {
                return playerFile.delete();
            }
            return true;
        });
    }

    // ==================== Objective Progress Operations ====================

    @Override
    public CompletableFuture<Boolean> saveObjectiveProgress(QuestObjectiveProgressDTO objective) {
        return CompletableFuture.supplyAsync(() -> {
            objectiveCache.computeIfAbsent(objective.playerUuid(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(objective.questId(), k -> new ArrayList<>());

            List<QuestObjectiveProgressDTO> questObjectives =
                objectiveCache.get(objective.playerUuid()).get(objective.questId());

            // Update or add
            questObjectives.removeIf(o -> o.objectiveId().equals(objective.objectiveId()));
            questObjectives.add(objective);

            dirty = true;
            return true;
        });
    }

    @Override
    public CompletableFuture<Optional<QuestObjectiveProgressDTO>> getObjectiveProgress(
            UUID playerUuid, String questId, String objectiveId) {

        return CompletableFuture.supplyAsync(() -> {
            loadPlayerData(playerUuid);
            Map<String, List<QuestObjectiveProgressDTO>> playerObjectives = objectiveCache.get(playerUuid);
            if (playerObjectives != null) {
                List<QuestObjectiveProgressDTO> questObjectives = playerObjectives.get(questId);
                if (questObjectives != null) {
                    return questObjectives.stream()
                        .filter(o -> o.objectiveId().equals(objectiveId))
                        .findFirst();
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<List<QuestObjectiveProgressDTO>> getAllObjectiveProgress(
            UUID playerUuid, String questId) {

        return CompletableFuture.supplyAsync(() -> {
            loadPlayerData(playerUuid);
            Map<String, List<QuestObjectiveProgressDTO>> playerObjectives = objectiveCache.get(playerUuid);
            if (playerObjectives != null) {
                List<QuestObjectiveProgressDTO> questObjectives = playerObjectives.get(questId);
                if (questObjectives != null) {
                    return new ArrayList<>(questObjectives);
                }
            }
            return new ArrayList<>();
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteObjectiveProgress(UUID playerUuid, String questId) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, List<QuestObjectiveProgressDTO>> playerObjectives = objectiveCache.get(playerUuid);
            if (playerObjectives != null) {
                playerObjectives.remove(questId);
                dirty = true;
            }
            return true;
        });
    }

    // ==================== Reward Tracking Operations ====================

    @Override
    public CompletableFuture<Boolean> saveRewardClaimed(QuestRewardClaimedDTO rewardClaimed) {
        return CompletableFuture.supplyAsync(() -> {
            rewardsClaimedCache.computeIfAbsent(rewardClaimed.playerUuid(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(rewardClaimed.questId(), k -> ConcurrentHashMap.newKeySet())
                .add(rewardClaimed.rewardId());
            dirty = true;
            return true;
        });
    }

    @Override
    public CompletableFuture<Boolean> hasClaimedReward(UUID playerUuid, String questId, String rewardId) {
        return CompletableFuture.supplyAsync(() -> {
            loadPlayerData(playerUuid);
            Map<String, Set<String>> playerRewards = rewardsClaimedCache.get(playerUuid);
            if (playerRewards != null) {
                Set<String> questRewards = playerRewards.get(questId);
                if (questRewards != null) {
                    return questRewards.contains(rewardId);
                }
            }
            return false;
        });
    }

    @Override
    public CompletableFuture<List<QuestRewardClaimedDTO>> getClaimedRewards(UUID playerUuid, String questId) {
        return CompletableFuture.supplyAsync(() -> {
            loadPlayerData(playerUuid);
            Map<String, Set<String>> playerRewards = rewardsClaimedCache.get(playerUuid);
            if (playerRewards != null) {
                Set<String> questRewards = playerRewards.get(questId);
                if (questRewards != null) {
                    return questRewards.stream()
                        .map(rewardId -> QuestRewardClaimedDTO.create(playerUuid, questId, rewardId))
                        .toList();
                }
            }
            return new ArrayList<>();
        });
    }

    // ==================== Utility Operations ====================

    @Override
    public boolean isInFallbackMode() {
        return true;  // YAML repo is always fallback mode
    }

    @Override
    public CompletableFuture<Void> flush() {
        return CompletableFuture.runAsync(() -> {
            if (!dirty) return;

            // Save all cached players
            for (UUID playerUuid : progressCache.keySet()) {
                savePlayerData(playerUuid);
            }
            dirty = false;
            logger.debug("Flushed YAML data to disk");
        });
    }

    /**
     * Save data for a specific player to their YAML file.
     *
     * @param playerUuid The player's UUID
     */
    public void savePlayerData(UUID playerUuid) {
        File playerFile = new File(dataFolder, playerUuid + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();

        // Save quest progress
        Map<String, QuestProgressDTO> playerProgress = progressCache.get(playerUuid);
        if (playerProgress != null) {
            for (QuestProgressDTO progress : playerProgress.values()) {
                String path = "quests." + progress.questId();
                yaml.set(path + ".state", progress.state().name());
                yaml.set(path + ".pathChoice", progress.pathChoice());
                yaml.set(path + ".startedAt", progress.startedAt() != null ? progress.startedAt().toString() : null);
                yaml.set(path + ".completedAt", progress.completedAt() != null ? progress.completedAt().toString() : null);
            }
        }

        // Save objectives
        Map<String, List<QuestObjectiveProgressDTO>> playerObjectives = objectiveCache.get(playerUuid);
        if (playerObjectives != null) {
            for (Map.Entry<String, List<QuestObjectiveProgressDTO>> entry : playerObjectives.entrySet()) {
                for (QuestObjectiveProgressDTO obj : entry.getValue()) {
                    String path = "objectives." + obj.questId() + "." + obj.objectiveId();
                    yaml.set(path + ".progressCount", obj.progressCount());
                    yaml.set(path + ".targetCount", obj.targetCount());
                    yaml.set(path + ".completed", obj.completed());
                    yaml.set(path + ".completedAt", obj.completedAt() != null ? obj.completedAt().toString() : null);
                }
            }
        }

        // Save rewards claimed
        Map<String, Set<String>> playerRewards = rewardsClaimedCache.get(playerUuid);
        if (playerRewards != null) {
            for (Map.Entry<String, Set<String>> entry : playerRewards.entrySet()) {
                yaml.set("rewards." + entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }

        try {
            yaml.save(playerFile);
            logger.debug("Saved data for player: " + playerUuid);
        } catch (IOException e) {
            logger.error("Failed to save player data: " + playerUuid, e);
        }
    }

    /**
     * Load data for a specific player from their YAML file.
     *
     * @param playerUuid The player's UUID
     */
    public void loadPlayerData(UUID playerUuid) {
        // Skip if already loaded
        if (progressCache.containsKey(playerUuid)) {
            return;
        }

        File playerFile = new File(dataFolder, playerUuid + ".yml");
        if (!playerFile.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(playerFile);

        // Load quest progress
        ConfigurationSection questsSection = yaml.getConfigurationSection("quests");
        if (questsSection != null) {
            Map<String, QuestProgressDTO> playerProgress = new ConcurrentHashMap<>();
            for (String questId : questsSection.getKeys(false)) {
                ConfigurationSection questSection = questsSection.getConfigurationSection(questId);
                if (questSection != null) {
                    QuestState state = QuestState.valueOf(questSection.getString("state", "NOT_STARTED"));
                    String pathChoice = questSection.getString("pathChoice");
                    String startedAtStr = questSection.getString("startedAt");
                    String completedAtStr = questSection.getString("completedAt");

                    Instant startedAt = startedAtStr != null ? Instant.parse(startedAtStr) : null;
                    Instant completedAt = completedAtStr != null ? Instant.parse(completedAtStr) : null;

                    playerProgress.put(questId, QuestProgressDTO.builder()
                        .playerUuid(playerUuid)
                        .questId(questId)
                        .state(state)
                        .pathChoice(pathChoice)
                        .startedAt(startedAt)
                        .completedAt(completedAt)
                        .build());
                }
            }
            progressCache.put(playerUuid, playerProgress);
        }

        // Load objectives
        ConfigurationSection objectivesSection = yaml.getConfigurationSection("objectives");
        if (objectivesSection != null) {
            Map<String, List<QuestObjectiveProgressDTO>> playerObjectives = new ConcurrentHashMap<>();
            for (String questId : objectivesSection.getKeys(false)) {
                ConfigurationSection questSection = objectivesSection.getConfigurationSection(questId);
                if (questSection != null) {
                    List<QuestObjectiveProgressDTO> objectives = new ArrayList<>();
                    for (String objectiveId : questSection.getKeys(false)) {
                        ConfigurationSection objSection = questSection.getConfigurationSection(objectiveId);
                        if (objSection != null) {
                            int progressCount = objSection.getInt("progressCount", 0);
                            int targetCount = objSection.getInt("targetCount", 1);
                            boolean completed = objSection.getBoolean("completed", false);
                            String completedAtStr = objSection.getString("completedAt");
                            Instant completedAt = completedAtStr != null ? Instant.parse(completedAtStr) : null;

                            objectives.add(QuestObjectiveProgressDTO.builder()
                                .playerUuid(playerUuid)
                                .questId(questId)
                                .objectiveId(objectiveId)
                                .progressCount(progressCount)
                                .targetCount(targetCount)
                                .completed(completed)
                                .completedAt(completedAt)
                                .build());
                        }
                    }
                    playerObjectives.put(questId, objectives);
                }
            }
            objectiveCache.put(playerUuid, playerObjectives);
        }

        // Load rewards claimed
        ConfigurationSection rewardsSection = yaml.getConfigurationSection("rewards");
        if (rewardsSection != null) {
            Map<String, Set<String>> playerRewards = new ConcurrentHashMap<>();
            for (String questId : rewardsSection.getKeys(false)) {
                List<String> rewards = rewardsSection.getStringList(questId);
                playerRewards.put(questId, ConcurrentHashMap.newKeySet());
                playerRewards.get(questId).addAll(rewards);
            }
            rewardsClaimedCache.put(playerUuid, playerRewards);
        }

        logger.debug("Loaded data for player: " + playerUuid);
    }

    /**
     * Unload a player's data from cache (call on player quit after saving).
     *
     * @param playerUuid The player's UUID
     */
    public void unloadPlayerData(UUID playerUuid) {
        progressCache.remove(playerUuid);
        objectiveCache.remove(playerUuid);
        rewardsClaimedCache.remove(playerUuid);
    }
}
