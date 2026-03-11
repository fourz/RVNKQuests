package org.fourz.RVNKQuests.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.dto.ObjectiveDTO;
import org.fourz.RVNKQuests.data.dto.ObjectiveType;
import org.fourz.RVNKQuests.data.dto.QuestDTO;
import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.data.dto.RewardType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * YAML fallback implementation of IQuestRepository.
 * Stores quest definitions as individual YAML files in plugins/RVNKQuests/quests/.
 */
public class QuestYamlRepository implements IQuestRepository {

    private final RVNKQuests plugin;
    private final LogManager logger;
    private final File questsDir;
    private final Map<String, QuestDTO> cache = new ConcurrentHashMap<>();

    public QuestYamlRepository(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "QuestYamlRepository");
        this.questsDir = new File(plugin.getDataFolder(), "quests");

        if (!questsDir.exists()) {
            questsDir.mkdirs();
        }

        loadAllFromDisk();
    }

    private void loadAllFromDisk() {
        cache.clear();
        File[] files = questsDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) return;

        for (File file : files) {
            try {
                QuestDTO quest = loadQuestFromFile(file);
                if (quest != null) {
                    cache.put(quest.questId(), quest);
                }
            } catch (Exception e) {
                logger.error("Failed to load quest from file: " + file.getName(), e);
            }
        }

        logger.info("Loaded " + cache.size() + " quest definitions from YAML");
    }

    private QuestDTO loadQuestFromFile(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        String questId = yaml.getString("quest_id");
        if (questId == null) return null;

        String name = yaml.getString("name", questId);
        String description = yaml.getString("description");
        String category = yaml.getString("category");
        boolean repeatable = yaml.getBoolean("repeatable", false);
        int cooldownMinutes = yaml.getInt("cooldown_minutes", 0);
        List<String> prerequisites = yaml.getStringList("prerequisites");

        // Load metadata
        Map<String, Object> metadata = Map.of();
        ConfigurationSection metaSection = yaml.getConfigurationSection("metadata");
        if (metaSection != null) {
            metadata = new LinkedHashMap<>(metaSection.getValues(true));
        }

        // Load objectives
        List<ObjectiveDTO> objectives = new ArrayList<>();
        ConfigurationSection objSection = yaml.getConfigurationSection("objectives");
        if (objSection != null) {
            for (String key : objSection.getKeys(false)) {
                ConfigurationSection obj = objSection.getConfigurationSection(key);
                if (obj == null) continue;

                ObjectiveType type;
                try {
                    type = ObjectiveType.valueOf(obj.getString("type", "CUSTOM"));
                } catch (IllegalArgumentException e) {
                    type = ObjectiveType.CUSTOM;
                }

                Map<String, String> objMeta = Map.of();
                ConfigurationSection objMetaSection = obj.getConfigurationSection("metadata");
                if (objMetaSection != null) {
                    objMeta = new LinkedHashMap<>();
                    for (String mk : objMetaSection.getKeys(false)) {
                        objMeta.put(mk, objMetaSection.getString(mk, ""));
                    }
                    objMeta = Map.copyOf(objMeta);
                }

                objectives.add(new ObjectiveDTO(
                    key,
                    type,
                    obj.getString("target"),
                    obj.getInt("required_amount", 1),
                    obj.getString("description"),
                    obj.getInt("order", 0),
                    objMeta
                ));
            }
        }

        // Load rewards
        List<RewardDTO> rewards = new ArrayList<>();
        ConfigurationSection rwdSection = yaml.getConfigurationSection("rewards");
        if (rwdSection != null) {
            for (String key : rwdSection.getKeys(false)) {
                ConfigurationSection rwd = rwdSection.getConfigurationSection(key);
                if (rwd == null) continue;

                RewardType type;
                try {
                    type = RewardType.valueOf(rwd.getString("type", "CUSTOM"));
                } catch (IllegalArgumentException e) {
                    type = RewardType.CUSTOM;
                }

                Map<String, String> rwdMeta = Map.of();
                ConfigurationSection rwdMetaSection = rwd.getConfigurationSection("metadata");
                if (rwdMetaSection != null) {
                    rwdMeta = new LinkedHashMap<>();
                    for (String mk : rwdMetaSection.getKeys(false)) {
                        rwdMeta.put(mk, rwdMetaSection.getString(mk, ""));
                    }
                    rwdMeta = Map.copyOf(rwdMeta);
                }

                rewards.add(new RewardDTO(
                    key,
                    type,
                    rwd.getString("value"),
                    rwd.getInt("amount", 1),
                    rwd.getString("description"),
                    rwdMeta
                ));
            }
        }

        return new QuestDTO(questId, name, description, category, repeatable,
                           cooldownMinutes, objectives, rewards, prerequisites,
                           Instant.now(), metadata);
    }

    private void saveQuestToFile(QuestDTO quest) throws IOException {
        File file = new File(questsDir, quest.questId() + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("quest_id", quest.questId());
        yaml.set("name", quest.name());
        yaml.set("description", quest.description());
        yaml.set("category", quest.category());
        yaml.set("repeatable", quest.repeatable());
        yaml.set("cooldown_minutes", quest.cooldownMinutes());

        if (!quest.prerequisites().isEmpty()) {
            yaml.set("prerequisites", quest.prerequisites());
        }

        if (!quest.metadata().isEmpty()) {
            for (Map.Entry<String, Object> entry : quest.metadata().entrySet()) {
                yaml.set("metadata." + entry.getKey(), entry.getValue());
            }
        }

        // Save objectives
        for (ObjectiveDTO obj : quest.objectives()) {
            String path = "objectives." + obj.objectiveId();
            yaml.set(path + ".type", obj.type().name());
            yaml.set(path + ".target", obj.target());
            yaml.set(path + ".required_amount", obj.requiredAmount());
            yaml.set(path + ".description", obj.description());
            yaml.set(path + ".order", obj.order());
            if (!obj.metadata().isEmpty()) {
                for (Map.Entry<String, String> entry : obj.metadata().entrySet()) {
                    yaml.set(path + ".metadata." + entry.getKey(), entry.getValue());
                }
            }
        }

        // Save rewards
        for (RewardDTO rwd : quest.rewards()) {
            String path = "rewards." + rwd.rewardId();
            yaml.set(path + ".type", rwd.type().name());
            yaml.set(path + ".value", rwd.value());
            yaml.set(path + ".amount", rwd.amount());
            yaml.set(path + ".description", rwd.description());
            if (!rwd.metadata().isEmpty()) {
                for (Map.Entry<String, String> entry : rwd.metadata().entrySet()) {
                    yaml.set(path + ".metadata." + entry.getKey(), entry.getValue());
                }
            }
        }

        yaml.save(file);
    }

    // ==================== Quest CRUD Operations ====================

    @Override
    public CompletableFuture<Boolean> save(QuestDTO quest) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                saveQuestToFile(quest);
                cache.put(quest.questId(), quest);
                return true;
            } catch (IOException e) {
                logger.error("Failed to save quest to YAML: " + quest.questId(), e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Optional<QuestDTO>> findById(String questId) {
        return CompletableFuture.completedFuture(Optional.ofNullable(cache.get(questId)));
    }

    @Override
    public CompletableFuture<Optional<QuestDTO>> findByName(String name) {
        return CompletableFuture.completedFuture(
            cache.values().stream()
                .filter(q -> q.name().equalsIgnoreCase(name))
                .findFirst()
        );
    }

    @Override
    public CompletableFuture<List<QuestDTO>> findAll() {
        return CompletableFuture.completedFuture(
            cache.values().stream()
                .sorted(Comparator.comparing(QuestDTO::name))
                .collect(Collectors.toList())
        );
    }

    @Override
    public CompletableFuture<Boolean> deleteById(String questId) {
        return CompletableFuture.supplyAsync(() -> {
            cache.remove(questId);
            File file = new File(questsDir, questId + ".yml");
            if (file.exists()) {
                return file.delete();
            }
            return true;
        });
    }

    @Override
    public CompletableFuture<Boolean> exists(String questId) {
        return CompletableFuture.completedFuture(cache.containsKey(questId));
    }

    // ==================== Query Operations ====================

    @Override
    public CompletableFuture<List<QuestDTO>> findByCategory(String category) {
        return CompletableFuture.completedFuture(
            cache.values().stream()
                .filter(q -> category.equals(q.category()))
                .sorted(Comparator.comparing(QuestDTO::name))
                .collect(Collectors.toList())
        );
    }

    @Override
    public CompletableFuture<List<QuestDTO>> findRepeatable() {
        return CompletableFuture.completedFuture(
            cache.values().stream()
                .filter(QuestDTO::repeatable)
                .sorted(Comparator.comparing(QuestDTO::name))
                .collect(Collectors.toList())
        );
    }

    @Override
    public CompletableFuture<List<QuestDTO>> findByPrerequisite(String prerequisiteQuestId) {
        return CompletableFuture.completedFuture(
            cache.values().stream()
                .filter(q -> q.prerequisites().contains(prerequisiteQuestId))
                .sorted(Comparator.comparing(QuestDTO::name))
                .collect(Collectors.toList())
        );
    }

    @Override
    public CompletableFuture<List<QuestDTO>> search(String searchTerm) {
        String lower = searchTerm.toLowerCase();
        return CompletableFuture.completedFuture(
            cache.values().stream()
                .filter(q -> q.name().toLowerCase().contains(lower) ||
                            (q.description() != null && q.description().toLowerCase().contains(lower)))
                .sorted(Comparator.comparing(QuestDTO::name))
                .collect(Collectors.toList())
        );
    }

    @Override
    public CompletableFuture<List<String>> findAllCategories() {
        return CompletableFuture.completedFuture(
            cache.values().stream()
                .map(QuestDTO::category)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList())
        );
    }

    @Override
    public CompletableFuture<List<QuestDTO>> findStarterQuests() {
        return CompletableFuture.completedFuture(
            cache.values().stream()
                .filter(q -> q.prerequisites().isEmpty())
                .sorted(Comparator.comparing(QuestDTO::name))
                .collect(Collectors.toList())
        );
    }

    // ==================== Objective Operations ====================

    @Override
    public CompletableFuture<Boolean> saveObjectives(String questId, List<ObjectiveDTO> objectives) {
        QuestDTO quest = cache.get(questId);
        if (quest == null) return CompletableFuture.completedFuture(false);
        return save(quest.withObjectives(objectives));
    }

    @Override
    public CompletableFuture<List<ObjectiveDTO>> findObjectives(String questId) {
        QuestDTO quest = cache.get(questId);
        return CompletableFuture.completedFuture(quest != null ? quest.objectives() : List.of());
    }

    @Override
    public CompletableFuture<Boolean> addObjective(String questId, ObjectiveDTO objective) {
        QuestDTO quest = cache.get(questId);
        if (quest == null) return CompletableFuture.completedFuture(false);
        return save(quest.withObjective(objective));
    }

    @Override
    public CompletableFuture<Boolean> removeObjective(String questId, String objectiveId) {
        QuestDTO quest = cache.get(questId);
        if (quest == null) return CompletableFuture.completedFuture(false);

        List<ObjectiveDTO> filtered = quest.objectives().stream()
            .filter(o -> !o.objectiveId().equals(objectiveId))
            .collect(Collectors.toList());
        return save(quest.withObjectives(filtered));
    }

    // ==================== Reward Operations ====================

    @Override
    public CompletableFuture<Boolean> saveRewards(String questId, List<RewardDTO> rewards) {
        QuestDTO quest = cache.get(questId);
        if (quest == null) return CompletableFuture.completedFuture(false);
        return save(quest.withRewards(rewards));
    }

    @Override
    public CompletableFuture<List<RewardDTO>> findRewards(String questId) {
        QuestDTO quest = cache.get(questId);
        return CompletableFuture.completedFuture(quest != null ? quest.rewards() : List.of());
    }

    @Override
    public CompletableFuture<Boolean> addReward(String questId, RewardDTO reward) {
        QuestDTO quest = cache.get(questId);
        if (quest == null) return CompletableFuture.completedFuture(false);
        return save(quest.withReward(reward));
    }

    @Override
    public CompletableFuture<Boolean> removeReward(String questId, String rewardId) {
        QuestDTO quest = cache.get(questId);
        if (quest == null) return CompletableFuture.completedFuture(false);

        List<RewardDTO> filtered = quest.rewards().stream()
            .filter(r -> !r.rewardId().equals(rewardId))
            .collect(Collectors.toList());
        return save(quest.withRewards(filtered));
    }

    // ==================== Bulk Operations ====================

    @Override
    public CompletableFuture<Integer> saveAll(List<QuestDTO> quests) {
        return CompletableFuture.supplyAsync(() -> {
            int saved = 0;
            for (QuestDTO quest : quests) {
                try {
                    saveQuestToFile(quest);
                    cache.put(quest.questId(), quest);
                    saved++;
                } catch (IOException e) {
                    logger.error("Failed to save quest in batch: " + quest.questId(), e);
                }
            }
            return saved;
        });
    }

    @Override
    public CompletableFuture<Integer> deleteByCategory(String category) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> toDelete = cache.values().stream()
                .filter(q -> category.equals(q.category()))
                .map(QuestDTO::questId)
                .collect(Collectors.toList());

            int deleted = 0;
            for (String questId : toDelete) {
                cache.remove(questId);
                File file = new File(questsDir, questId + ".yml");
                if (file.exists() && file.delete()) {
                    deleted++;
                }
            }
            return deleted;
        });
    }

    @Override
    public CompletableFuture<Long> count() {
        return CompletableFuture.completedFuture((long) cache.size());
    }

    @Override
    public CompletableFuture<Long> countByCategory(String category) {
        return CompletableFuture.completedFuture(
            cache.values().stream()
                .filter(q -> category.equals(q.category()))
                .count()
        );
    }

    // ==================== Utility Operations ====================

    @Override
    public boolean isInFallbackMode() {
        return true; // YAML is always fallback mode
    }

    @Override
    public CompletableFuture<Void> flush() {
        return CompletableFuture.runAsync(() -> {
            for (QuestDTO quest : cache.values()) {
                try {
                    saveQuestToFile(quest);
                } catch (IOException e) {
                    logger.error("Failed to flush quest: " + quest.questId(), e);
                }
            }
        });
    }

    @Override
    public CompletableFuture<Void> reload() {
        return CompletableFuture.runAsync(this::loadAllFromDisk);
    }
}
