package org.fourz.RVNKQuests.service;

import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.DatabaseManager;
import org.fourz.RVNKQuests.data.dto.PlayerQuestRepeatDTO;
import org.fourz.RVNKQuests.data.dto.QuestRepeatConfigDTO;
import org.fourz.RVNKQuests.data.dto.QuestRepeatConfigDTO.RepeatType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service implementation for repeatable quest management.
 *
 * <p>Provides caching and async operations for repeat configuration
 * and player tracking.</p>
 */
public class RepeatableQuestServiceImpl implements IRepeatableQuestService {

    private final RVNKQuests plugin;
    private final LogManager logger;
    private final DatabaseManager databaseManager;
    private final IQuestProgressService questProgressService;
    private final String tblRepeatConfig;

    // In-memory cache for repeat configurations
    private final Map<String, QuestRepeatConfigDTO> configCache = new ConcurrentHashMap<>();
    // In-memory cache for player repeat data
    private final Map<UUID, Map<String, PlayerQuestRepeatDTO>> playerCache = new ConcurrentHashMap<>();

    /**
     * Creates a new repeatable quest service.
     *
     * @param plugin The plugin instance
     * @param databaseManager The database manager
     * @param questProgressService The quest progress service
     */
    public RepeatableQuestServiceImpl(
            RVNKQuests plugin,
            DatabaseManager databaseManager,
            IQuestProgressService questProgressService) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "RepeatableQuestService");
        this.databaseManager = databaseManager;
        this.questProgressService = questProgressService;
        this.tblRepeatConfig = databaseManager.table("quest_repeat_config");
    }

    // ==================== Configuration Management ====================

    @Override
    public CompletableFuture<Optional<QuestRepeatConfigDTO>> getRepeatConfig(String questId) {
        // Check cache first
        if (configCache.containsKey(questId)) {
            return CompletableFuture.completedFuture(Optional.of(configCache.get(questId)));
        }

        // Fallback to database
        if (!databaseManager.isAvailable()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT quest_id, repeat_type, cooldown_seconds, max_completions " +
                        "FROM " + tblRepeatConfig + " WHERE quest_id = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, questId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        QuestRepeatConfigDTO config = QuestRepeatConfigDTO.builder()
                            .questId(rs.getString("quest_id"))
                            .repeatType(RepeatType.valueOf(rs.getString("repeat_type")))
                            .cooldownSeconds(rs.getInt("cooldown_seconds"))
                            .maxCompletions(rs.getInt("max_completions"))
                            .build();

                        // Cache it
                        configCache.put(questId, config);
                        return Optional.of(config);
                    }
                }
            } catch (SQLException e) {
                logger.error("Failed to load repeat config for quest: " + questId, e);
            }

            return Optional.empty();
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Boolean> saveRepeatConfig(QuestRepeatConfigDTO config) {
        // Update cache immediately
        configCache.put(config.questId(), config);

        if (!databaseManager.isAvailable()) {
            logger.warning("Database not available - config cached only");
            return CompletableFuture.completedFuture(true);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT OR REPLACE INTO " + tblRepeatConfig + " " +
                        "(quest_id, repeat_type, cooldown_seconds, max_completions, updated_at) " +
                        "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, config.questId());
                stmt.setString(2, config.repeatType().name());
                stmt.setInt(3, config.cooldownSeconds());
                stmt.setInt(4, config.maxCompletions());

                int rows = stmt.executeUpdate();
                logger.debug("Saved repeat config for quest: " + config.questId());
                return rows > 0;

            } catch (SQLException e) {
                logger.error("Failed to save repeat config for quest: " + config.questId(), e);
                return false;
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Boolean> deleteRepeatConfig(String questId) {
        // Remove from cache
        configCache.remove(questId);

        if (!databaseManager.isAvailable()) {
            return CompletableFuture.completedFuture(true);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + tblRepeatConfig + " WHERE quest_id = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, questId);
                int rows = stmt.executeUpdate();
                logger.debug("Deleted repeat config for quest: " + questId);
                return rows > 0;

            } catch (SQLException e) {
                logger.error("Failed to delete repeat config for quest: " + questId, e);
                return false;
            }
        }, databaseManager.getExecutor());
    }

    // ==================== Player Repeat Tracking ====================

    @Override
    public CompletableFuture<Optional<PlayerQuestRepeatDTO>> getPlayerRepeatData(UUID playerUuid, String questId) {
        // Check cache first
        Map<String, PlayerQuestRepeatDTO> playerData = playerCache.get(playerUuid);
        if (playerData != null && playerData.containsKey(questId)) {
            return CompletableFuture.completedFuture(Optional.of(playerData.get(questId)));
        }

        if (!databaseManager.isAvailable()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT player_uuid, quest_id, completion_count, last_completion, next_available " +
                        "FROM player_quest_repeats WHERE player_uuid = ? AND quest_id = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, questId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        PlayerQuestRepeatDTO data = PlayerQuestRepeatDTO.builder()
                            .playerUuid(UUID.fromString(rs.getString("player_uuid")))
                            .questId(rs.getString("quest_id"))
                            .completionCount(rs.getInt("completion_count"))
                            .lastCompletion(rs.getString("last_completion") != null ?
                                Instant.parse(rs.getString("last_completion")) : null)
                            .nextAvailable(rs.getString("next_available") != null ?
                                Instant.parse(rs.getString("next_available")) : null)
                            .build();

                        // Cache it
                        playerCache.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                            .put(questId, data);
                        return Optional.of(data);
                    }
                }
            } catch (SQLException e) {
                logger.error("Failed to load player repeat data: " + playerUuid + ", " + questId, e);
            }

            return Optional.empty();
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<PlayerQuestRepeatDTO> recordCompletion(UUID playerUuid, String questId) {
        return getRepeatConfig(questId).thenCompose(configOpt -> {
            if (configOpt.isEmpty()) {
                logger.warning("No repeat config for quest: " + questId);
                return CompletableFuture.completedFuture(null);
            }

            QuestRepeatConfigDTO config = configOpt.get();
            Instant completionTime = Instant.now();
            Instant nextAvailable = config.getNextAvailableTime(completionTime);

            return getPlayerRepeatData(playerUuid, questId).thenCompose(dataOpt -> {
                PlayerQuestRepeatDTO currentData = dataOpt.orElse(
                    PlayerQuestRepeatDTO.createNew(playerUuid, questId)
                );

                PlayerQuestRepeatDTO updatedData = currentData.recordCompletion(completionTime, nextAvailable);

                // Update cache
                playerCache.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                    .put(questId, updatedData);

                // Save to database
                return savePlayerRepeatData(updatedData).thenApply(success -> updatedData);
            });
        });
    }

    private CompletableFuture<Boolean> savePlayerRepeatData(PlayerQuestRepeatDTO data) {
        if (!databaseManager.isAvailable()) {
            return CompletableFuture.completedFuture(true);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT OR REPLACE INTO player_quest_repeats " +
                        "(player_uuid, quest_id, completion_count, last_completion, next_available, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, data.playerUuid().toString());
                stmt.setString(2, data.questId());
                stmt.setInt(3, data.completionCount());
                stmt.setString(4, data.lastCompletion() != null ? data.lastCompletion().toString() : null);
                stmt.setString(5, data.nextAvailable() != null ? data.nextAvailable().toString() : null);

                int rows = stmt.executeUpdate();
                logger.debug("Saved player repeat data: " + data.playerUuid() + ", " + data.questId());
                return rows > 0;

            } catch (SQLException e) {
                logger.error("Failed to save player repeat data", e);
                return false;
            }
        }, databaseManager.getExecutor());
    }

    @Override
    public CompletableFuture<Boolean> isQuestAvailable(UUID playerUuid, String questId) {
        return getRepeatConfig(questId).thenCompose(configOpt -> {
            if (configOpt.isEmpty()) {
                // No config means assume one-time quest
                return getPlayerRepeatData(playerUuid, questId)
                    .thenApply(dataOpt -> dataOpt.isEmpty() || dataOpt.get().completionCount() == 0);
            }

            QuestRepeatConfigDTO config = configOpt.get();

            // Check if repeatable
            if (!config.isRepeatable()) {
                return getPlayerRepeatData(playerUuid, questId)
                    .thenApply(dataOpt -> dataOpt.isEmpty() || dataOpt.get().completionCount() == 0);
            }

            // Check max completions
            if (config.hasCompletionLimit()) {
                return getPlayerRepeatData(playerUuid, questId).thenApply(dataOpt -> {
                    if (dataOpt.isEmpty()) {
                        return true;
                    }
                    return dataOpt.get().completionCount() < config.maxCompletions();
                });
            }

            // Check cooldown
            return getPlayerRepeatData(playerUuid, questId).thenApply(dataOpt -> {
                if (dataOpt.isEmpty()) {
                    return true;
                }
                return dataOpt.get().isAvailable();
            });
        });
    }

    @Override
    public CompletableFuture<Long> getRemainingCooldown(UUID playerUuid, String questId) {
        return getPlayerRepeatData(playerUuid, questId).thenApply(dataOpt -> {
            if (dataOpt.isEmpty()) {
                return 0L;
            }
            return dataOpt.get().getRemainingCooldownSeconds();
        });
    }

    @Override
    public CompletableFuture<Integer> getCompletionCount(UUID playerUuid, String questId) {
        return getPlayerRepeatData(playerUuid, questId).thenApply(dataOpt -> {
            if (dataOpt.isEmpty()) {
                return 0;
            }
            return dataOpt.get().completionCount();
        });
    }

    // ==================== Quest Reset ====================

    @Override
    public CompletableFuture<Boolean> resetQuestForPlayer(UUID playerUuid, String questId) {
        logger.info("Resetting quest " + questId + " for player " + playerUuid);

        // Reset quest progress (objectives, rewards, state)
        return questProgressService.resetQuestProgress(playerUuid, questId).thenCompose(resetSuccess -> {
            if (!resetSuccess) {
                logger.warning("Failed to reset quest progress for: " + questId);
                return CompletableFuture.completedFuture(false);
            }

            // Clear repeat tracking data
            Map<String, PlayerQuestRepeatDTO> playerData = playerCache.get(playerUuid);
            if (playerData != null) {
                playerData.remove(questId);
            }

            if (!databaseManager.isAvailable()) {
                return CompletableFuture.completedFuture(true);
            }

            // Delete from database
            return CompletableFuture.supplyAsync(() -> {
                String sql = "DELETE FROM player_quest_repeats WHERE player_uuid = ? AND quest_id = ?";

                try (Connection conn = databaseManager.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, questId);
                    stmt.executeUpdate();

                    logger.info("Quest reset complete: " + questId);
                    return true;

                } catch (SQLException e) {
                    logger.error("Failed to delete player repeat data during reset", e);
                    return false;
                }
            }, databaseManager.getExecutor());
        });
    }

    @Override
    public CompletableFuture<Boolean> canRepeatQuest(UUID playerUuid, String questId) {
        return getRepeatConfig(questId).thenCompose(configOpt -> {
            if (configOpt.isEmpty()) {
                return CompletableFuture.completedFuture(false); // No config = one-time
            }

            QuestRepeatConfigDTO config = configOpt.get();
            if (!config.isRepeatable()) {
                return CompletableFuture.completedFuture(false);
            }

            // Check if max completions reached
            if (config.hasCompletionLimit()) {
                return getCompletionCount(playerUuid, questId).thenApply(count ->
                    count < config.maxCompletions()
                );
            }

            return CompletableFuture.completedFuture(true); // Repeatable and no limit
        });
    }

    // ==================== Utility ====================

    @Override
    public boolean isInFallbackMode() {
        return !databaseManager.isAvailable();
    }

    @Override
    public void shutdown() {
        configCache.clear();
        playerCache.clear();
    }
}
