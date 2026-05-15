package org.fourz.RVNKQuests.service;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.data.dto.RewardType;
import org.fourz.RVNKQuests.service.reward.*;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the quest reward delivery service.
 *
 * <p>Manages reward processors and handles async reward delivery
 * with comprehensive error handling and logging.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Pluggable processor architecture</li>
 *   <li>Async delivery with CompletableFuture</li>
 *   <li>Batch processing with error recovery</li>
 *   <li>Comprehensive validation</li>
 *   <li>Fallback mode for unavailable integrations</li>
 * </ul>
 *
 * @since 1.0
 */
public class RewardServiceImpl implements IRewardService {

    private final JavaPlugin plugin;
    private final LogManager logger;
    private final Map<RewardType, RewardProcessor> processors;
    private final IQuestService questService;
    private volatile boolean inFallbackMode = false;

    /**
     * Create a new RewardServiceImpl without quest service integration.
     * The QUEST_UNLOCK processor will log unlock requests but will not forward them
     * to the quest system.
     *
     * @param plugin The plugin instance
     */
    public RewardServiceImpl(JavaPlugin plugin) {
        this(plugin, null);
    }

    /**
     * Create a new RewardServiceImpl with quest service integration.
     *
     * @param plugin       The plugin instance
     * @param questService The quest service used by {@link QuestUnlockRewardProcessor}
     *                     to start quests on unlock; may be {@code null} to disable integration
     */
    public RewardServiceImpl(JavaPlugin plugin, IQuestService questService) {
        this.plugin = plugin;
        this.questService = questService;
        this.logger = LogManager.getInstance(plugin, "RewardService");
        this.processors = new ConcurrentHashMap<>();

        // Register default processors
        registerDefaultProcessors();

        logger.debug("RewardService initialized with " + processors.size() + " processors");
    }

    /**
     * Register default reward processors for built-in reward types.
     */
    private void registerDefaultProcessors() {
        // Core reward types
        registerProcessor(RewardType.EXPERIENCE, new ExperienceRewardProcessor());
        registerProcessor(RewardType.ITEM, new ItemRewardProcessor());
        registerProcessor(RewardType.COMMAND, new CommandRewardProcessor());
        registerProcessor(RewardType.CURRENCY, new CurrencyRewardProcessor());
        registerProcessor(RewardType.TITLE, new TitleRewardProcessor());

        // Integration reward types
        registerProcessor(RewardType.PERMISSION, new PermissionRewardProcessor());
        registerProcessor(RewardType.QUEST_UNLOCK, new QuestUnlockRewardProcessor(questService));
        registerProcessor(RewardType.LORE, new LoreRewardProcessor());
        registerProcessor(RewardType.CUSTOM, new CustomRewardProcessor());

        // Log which processors are available
        for (Map.Entry<RewardType, RewardProcessor> entry : processors.entrySet()) {
            if (entry.getValue().isAvailable()) {
                logger.debug("Registered processor for " + entry.getKey() + " (available)");
            } else {
                logger.warning("Registered processor for " + entry.getKey() + " (unavailable - dependencies missing)");
            }
        }
    }

    // ==================== Single Reward Delivery ====================

    @Override
    public CompletableFuture<RewardDeliveryResult> deliverReward(UUID playerId, RewardDTO reward) {
        return deliverReward(playerId, null, reward);
    }

    @Override
    public CompletableFuture<RewardDeliveryResult> deliverReward(UUID playerId, String questId, RewardDTO reward) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(reward, "reward cannot be null");

        RewardProcessor processor = processors.get(reward.type());
        if (processor == null) {
            logger.warning("No processor registered for reward type: " + reward.type());
            return CompletableFuture.completedFuture(
                RewardDeliveryResult.failure(
                    reward,
                    "No processor for reward type: " + reward.type(),
                    "NO_PROCESSOR"
                )
            );
        }

        if (!processor.isAvailable()) {
            logger.warning("Processor unavailable for " + reward.type() + " - missing dependencies");
            return CompletableFuture.completedFuture(
                RewardDeliveryResult.failure(
                    reward,
                    "Reward processor unavailable: " + reward.type(),
                    "PROCESSOR_UNAVAILABLE"
                )
            );
        }

        logger.debug("Delivering " + reward.type() + " reward to player " + playerId +
                    (questId != null ? " (quest: " + questId + ")" : ""));

        return processor.deliver(playerId, reward)
            .whenComplete((result, error) -> {
                if (error != null) {
                    logger.error("Exception delivering reward: " + error.getMessage());
                } else if (!result.success()) {
                    logger.warning("Reward delivery failed: " + result.message() +
                                  " (error: " + result.errorCode() + ")");
                } else {
                    logger.debug("Reward delivered successfully: " + result.message());
                }
            });
    }

    // ==================== Batch Reward Delivery ====================

    @Override
    public CompletableFuture<BatchRewardResult> deliverRewards(UUID playerId, List<RewardDTO> rewards) {
        return deliverRewards(playerId, null, rewards, true);
    }

    @Override
    public CompletableFuture<BatchRewardResult> deliverRewards(
            UUID playerId,
            String questId,
            List<RewardDTO> rewards,
            boolean continueOnError) {

        Objects.requireNonNull(playerId, "playerId cannot be null");

        if (rewards == null || rewards.isEmpty()) {
            return CompletableFuture.completedFuture(
                new BatchRewardResult(0, 0, 0, List.of(), false)
            );
        }

        // Sort rewards by processor priority (higher first)
        List<RewardDTO> sortedRewards = new ArrayList<>(rewards);
        sortedRewards.sort((a, b) -> {
            int priorityA = getPriorityForType(a.type());
            int priorityB = getPriorityForType(b.type());
            return Integer.compare(priorityB, priorityA); // Descending
        });

        logger.info("Delivering batch of " + sortedRewards.size() + " rewards to player " + playerId);

        // Process rewards sequentially to maintain order and handle errors
        return processRewardsSequentially(playerId, questId, sortedRewards, continueOnError);
    }

    /**
     * Process rewards sequentially.
     */
    private CompletableFuture<BatchRewardResult> processRewardsSequentially(
            UUID playerId,
            String questId,
            List<RewardDTO> rewards,
            boolean continueOnError) {

        List<RewardDeliveryResult> results = new ArrayList<>();
        int[] counts = {0, 0}; // [success, failure]
        boolean[] stoppedEarly = {false};

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        for (RewardDTO reward : rewards) {
            chain = chain.thenCompose(ignored -> {
                if (stoppedEarly[0]) {
                    // Skip remaining rewards if stopped early
                    results.add(RewardDeliveryResult.failure(
                        reward, "Skipped due to earlier failure", "SKIPPED"));
                    counts[1]++;
                    return CompletableFuture.completedFuture(null);
                }

                return deliverReward(playerId, questId, reward)
                    .thenAccept(result -> {
                        results.add(result);
                        if (result.success()) {
                            counts[0]++;
                        } else {
                            counts[1]++;
                            if (!continueOnError && isCriticalError(result.errorCode())) {
                                stoppedEarly[0] = true;
                                logger.warning("Stopping batch delivery due to critical error: " + result.errorCode());
                            }
                        }
                    });
            });
        }

        return chain.thenApply(ignored -> {
            BatchRewardResult result = new BatchRewardResult(
                rewards.size(),
                counts[0],
                counts[1],
                results,
                stoppedEarly[0]
            );

            logger.info(String.format("Batch delivery complete: %d/%d successful%s",
                counts[0], rewards.size(), stoppedEarly[0] ? " (stopped early)" : ""));

            return result;
        });
    }

    /**
     * Check if an error code indicates a critical failure that should stop batch processing.
     */
    private boolean isCriticalError(String errorCode) {
        if (errorCode == null) {
            return false;
        }
        return switch (errorCode) {
            case "PLAYER_OFFLINE", "PLAYER_NOT_FOUND" -> true;
            default -> false;
        };
    }

    /**
     * Get the priority for a reward type.
     */
    private int getPriorityForType(RewardType type) {
        RewardProcessor processor = processors.get(type);
        return processor != null ? processor.getPriority() : 0;
    }

    // ==================== Validation ====================

    @Override
    public CompletableFuture<RewardValidationResult> validateReward(UUID playerId, RewardDTO reward) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(reward, "reward cannot be null");

        RewardProcessor processor = processors.get(reward.type());
        if (processor == null) {
            return CompletableFuture.completedFuture(
                RewardValidationResult.invalid(
                    reward,
                    "No processor for reward type: " + reward.type(),
                    "Valid processor required"
                )
            );
        }

        if (!processor.isAvailable()) {
            return CompletableFuture.completedFuture(
                RewardValidationResult.invalid(
                    reward,
                    "Processor unavailable: " + reward.type(),
                    "Processor dependencies required"
                )
            );
        }

        return processor.validate(playerId, reward);
    }

    @Override
    public CompletableFuture<List<RewardValidationResult>> validateRewards(UUID playerId, List<RewardDTO> rewards) {
        if (rewards == null || rewards.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        // Validate all rewards in parallel
        List<CompletableFuture<RewardValidationResult>> futures = rewards.stream()
            .map(reward -> validateReward(playerId, reward))
            .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(ignored -> futures.stream()
                .map(CompletableFuture::join)
                .toList());
    }

    // ==================== Processor Management ====================

    @Override
    public void registerProcessor(RewardType type, RewardProcessor processor) {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(processor, "processor cannot be null");

        if (processor.getType() != type) {
            throw new IllegalArgumentException(
                "Processor type mismatch: expected " + type + " but got " + processor.getType());
        }

        RewardProcessor previous = processors.put(type, processor);
        if (previous != null) {
            logger.info("Replaced processor for " + type);
        } else {
            logger.debug("Registered new processor for " + type);
        }
    }

    @Override
    public boolean hasProcessor(RewardType type) {
        return processors.containsKey(type);
    }

    @Override
    public RewardProcessor getProcessor(RewardType type) {
        return processors.get(type);
    }

    // ==================== Service Status ====================

    @Override
    public boolean isInFallbackMode() {
        return inFallbackMode;
    }

    /**
     * Set fallback mode.
     *
     * @param fallbackMode Whether to operate in fallback mode
     */
    public void setFallbackMode(boolean fallbackMode) {
        if (this.inFallbackMode != fallbackMode) {
            this.inFallbackMode = fallbackMode;
            if (fallbackMode) {
                logger.warning("RewardService entering fallback mode");
            } else {
                logger.info("RewardService exiting fallback mode");
            }
        }
    }

    @Override
    public List<RewardType> getSupportedTypes() {
        return processors.entrySet().stream()
            .filter(entry -> entry.getValue().isAvailable())
            .map(Map.Entry::getKey)
            .toList();
    }

    /**
     * Get all registered reward types (including unavailable).
     *
     * @return List of all registered types
     */
    public List<RewardType> getAllRegisteredTypes() {
        return new ArrayList<>(processors.keySet());
    }

    /**
     * Get status information about the service.
     *
     * @return Map of status information
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("fallbackMode", inFallbackMode);
        status.put("totalProcessors", processors.size());
        status.put("availableProcessors", getSupportedTypes().size());
        status.put("supportedTypes", getSupportedTypes().stream()
            .map(RewardType::name)
            .toList());

        Map<String, Boolean> processorStatus = new LinkedHashMap<>();
        for (Map.Entry<RewardType, RewardProcessor> entry : processors.entrySet()) {
            processorStatus.put(entry.getKey().name(), entry.getValue().isAvailable());
        }
        status.put("processorAvailability", processorStatus);

        return status;
    }

    /**
     * Shutdown the service (cleanup resources).
     */
    public void shutdown() {
        processors.clear();
    }
}
