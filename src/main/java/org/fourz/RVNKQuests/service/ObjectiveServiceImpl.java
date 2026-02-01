package org.fourz.RVNKQuests.service;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.dto.ObjectiveCondition;
import org.fourz.RVNKQuests.data.dto.ObjectiveCondition.ConditionType;
import org.fourz.RVNKQuests.data.dto.ObjectiveDTO;
import org.fourz.RVNKQuests.data.dto.ObjectiveGroup;
import org.fourz.RVNKQuests.data.dto.ObjectiveGroup.CompletionType;
import org.fourz.RVNKQuests.data.dto.QuestObjectiveProgressDTO;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Implementation of IObjectiveService for managing quest objectives, conditions, and groups.
 *
 * <p>Provides operations for:</p>
 * <ul>
 *   <li>Objective progress tracking and updates</li>
 *   <li>Condition evaluation for conditional objectives</li>
 *   <li>Group completion calculation for parallel/nested objectives</li>
 *   <li>Active objective determination based on conditions and order</li>
 * </ul>
 *
 * <p>Pattern compliance:</p>
 * <ul>
 *   <li>Async-first with CompletableFuture returns</li>
 *   <li>Delegates persistence to IQuestProgressService</li>
 *   <li>Thread-safe for concurrent access</li>
 * </ul>
 */
public class ObjectiveServiceImpl implements IObjectiveService {

    private final RVNKQuests plugin;
    private final LogManager logger;
    private final IQuestProgressService progressService;

    /**
     * Creates a new ObjectiveServiceImpl.
     *
     * @param plugin The plugin instance
     */
    public ObjectiveServiceImpl(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "ObjectiveService");
        this.progressService = plugin.getQuestProgressService();
        logger.info("ObjectiveService initialized");
    }

    // ========================================
    // Objective Progress Operations
    // ========================================

    @Override
    public CompletableFuture<Optional<QuestObjectiveProgressDTO>> getObjectiveProgress(
            UUID playerUuid, String questId, String objectiveId) {
        return progressService.getObjectiveProgress(playerUuid, questId, objectiveId);
    }

    @Override
    public CompletableFuture<List<QuestObjectiveProgressDTO>> getQuestObjectiveProgress(
            UUID playerUuid, String questId) {
        return progressService.getAllObjectives(playerUuid, questId);
    }

    @Override
    public CompletableFuture<QuestObjectiveProgressDTO> incrementProgress(
            UUID playerUuid, String questId, String objectiveId, int amount) {
        logger.debug("Incrementing progress for " + playerUuid + " on " + questId + "/" + objectiveId + " by " + amount);
        return progressService.incrementObjectiveProgress(playerUuid, questId, objectiveId, amount);
    }

    @Override
    public CompletableFuture<QuestObjectiveProgressDTO> setProgress(
            UUID playerUuid, String questId, String objectiveId, int progress) {
        logger.debug("Setting progress for " + playerUuid + " on " + questId + "/" + objectiveId + " to " + progress);

        return progressService.getObjectiveProgress(playerUuid, questId, objectiveId)
            .thenCompose(opt -> {
                if (opt.isEmpty()) {
                    logger.warning("Attempted to set progress on non-existent objective: " + objectiveId);
                    return CompletableFuture.completedFuture(null);
                }

                QuestObjectiveProgressDTO current = opt.get();
                QuestObjectiveProgressDTO updated = current.withProgressCount(progress);

                // Use underlying service to persist
                return progressService.incrementObjectiveProgress(
                    playerUuid, questId, objectiveId,
                    progress - current.progressCount()
                );
            });
    }

    @Override
    public CompletableFuture<Void> markCompleted(UUID playerUuid, String questId, String objectiveId) {
        logger.debug("Marking objective completed: " + playerUuid + "/" + questId + "/" + objectiveId);
        return progressService.completeObjective(playerUuid, questId, objectiveId)
            .thenApply(v -> null);
    }

    @Override
    public CompletableFuture<Void> resetProgress(UUID playerUuid, String questId, String objectiveId) {
        logger.debug("Resetting progress: " + playerUuid + "/" + questId + "/" + objectiveId);

        return progressService.getObjectiveProgress(playerUuid, questId, objectiveId)
            .thenCompose(opt -> {
                if (opt.isEmpty()) {
                    return CompletableFuture.completedFuture(null);
                }

                QuestObjectiveProgressDTO current = opt.get();
                // Re-initialize with same target
                return progressService.initializeObjective(
                    playerUuid, questId, objectiveId, current.targetCount()
                ).thenApply(v -> null);
            });
    }

    // ========================================
    // Condition Evaluation
    // ========================================

    @Override
    public CompletableFuture<Boolean> evaluateCondition(UUID playerUuid, ObjectiveCondition condition) {
        return evaluateConditionWithDetails(playerUuid, condition)
            .thenApply(ConditionResult::passed);
    }

    @Override
    public CompletableFuture<Boolean> evaluateConditions(UUID playerUuid, List<ObjectiveCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }

        List<CompletableFuture<Boolean>> futures = conditions.stream()
            .map(c -> evaluateCondition(playerUuid, c))
            .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream().allMatch(CompletableFuture::join));
    }

    @Override
    public CompletableFuture<ConditionResult> evaluateConditionWithDetails(
            UUID playerUuid, ObjectiveCondition condition) {

        return CompletableFuture.supplyAsync(() -> {
            boolean result = evaluateConditionSync(playerUuid, condition);

            // Apply inversion if needed
            boolean passed = condition.inverted() ? !result : result;

            if (passed) {
                return ConditionResult.pass(condition.conditionId(),
                    condition.description() != null ? condition.description() : "Condition met");
            } else {
                return ConditionResult.fail(condition.conditionId(),
                    condition.description() != null ? condition.description() : "Condition not met");
            }
        });
    }

    /**
     * Synchronously evaluates a condition.
     */
    private boolean evaluateConditionSync(UUID playerUuid, ObjectiveCondition condition) {
        Player player = Bukkit.getPlayer(playerUuid);

        switch (condition.type()) {
            case TIME_RANGE:
                return evaluateTimeCondition(condition);

            case LOCATION:
                return player != null && evaluateLocationCondition(player, condition);

            case QUEST_STATE:
                return evaluateQuestStateCondition(playerUuid, condition);

            case OBJECTIVE_COMPLETE:
                return evaluateObjectiveCompleteCondition(playerUuid, condition);

            case PERMISSION:
                return player != null && evaluatePermissionCondition(player, condition);

            case ITEM_IN_INVENTORY:
                return player != null && evaluateItemCondition(player, condition);

            case PLAYER_LEVEL:
                return player != null && evaluatePlayerLevelCondition(player, condition);

            case WEATHER:
                return player != null && evaluateWeatherCondition(player, condition);

            case EQUIPMENT:
                return player != null && evaluateEquipmentCondition(player, condition);

            case CUSTOM:
                logger.warning("Custom condition evaluation not implemented: " + condition.conditionId());
                return false;

            default:
                logger.warning("Unknown condition type: " + condition.type());
                return false;
        }
    }

    private boolean evaluateTimeCondition(ObjectiveCondition condition) {
        int startTime = condition.getIntParameter("startTime", 0);
        int endTime = condition.getIntParameter("endTime", 24000);
        boolean isRealTime = "true".equals(condition.getParameter("isRealTime", "false"));

        if (isRealTime) {
            // Real-world time check (hours 0-23)
            int currentHour = java.time.LocalTime.now().getHour();
            return currentHour >= startTime && currentHour < endTime;
        } else {
            // Game time check (ticks 0-24000)
            long worldTime = Bukkit.getWorlds().get(0).getTime();
            return worldTime >= startTime && worldTime < endTime;
        }
    }

    private boolean evaluateLocationCondition(Player player, ObjectiveCondition condition) {
        String world = condition.getParameter("world", null);
        if (world != null && !player.getWorld().getName().equals(world)) {
            return false;
        }

        double x = condition.getDoubleParameter("x", 0);
        double y = condition.getDoubleParameter("y", 0);
        double z = condition.getDoubleParameter("z", 0);
        double radius = condition.getDoubleParameter("radius", 10);

        double distance = player.getLocation().distance(
            new org.bukkit.Location(player.getWorld(), x, y, z)
        );

        return distance <= radius;
    }

    private boolean evaluateQuestStateCondition(UUID playerUuid, ObjectiveCondition condition) {
        String questId = condition.getParameter("questId", null);
        String requiredStateStr = condition.getParameter("requiredState", "COMPLETED");

        if (questId == null) {
            return false;
        }

        try {
            QuestState requiredState = QuestState.valueOf(requiredStateStr);
            QuestState currentState = plugin.getQuestProgressService()
                .getQuestState(playerUuid, questId).join();
            return currentState == requiredState;
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid quest state in condition: " + requiredStateStr);
            return false;
        }
    }

    private boolean evaluateObjectiveCompleteCondition(UUID playerUuid, ObjectiveCondition condition) {
        String questId = condition.getParameter("questId", null);
        String objectiveId = condition.getParameter("objectiveId", null);

        if (objectiveId == null) {
            return false;
        }

        // If questId not specified, we can't check
        if (questId == null) {
            return false;
        }

        Optional<QuestObjectiveProgressDTO> progress = progressService
            .getObjectiveProgress(playerUuid, questId, objectiveId).join();

        return progress.map(QuestObjectiveProgressDTO::completed).orElse(false);
    }

    private boolean evaluatePermissionCondition(Player player, ObjectiveCondition condition) {
        String permission = condition.getParameter("permission", null);
        return permission != null && player.hasPermission(permission);
    }

    private boolean evaluateItemCondition(Player player, ObjectiveCondition condition) {
        String itemTypeStr = condition.getParameter("itemType", null);
        int minAmount = condition.getIntParameter("minAmount", 1);

        if (itemTypeStr == null) {
            return false;
        }

        try {
            Material material = Material.valueOf(itemTypeStr.toUpperCase());
            int count = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == material) {
                    count += item.getAmount();
                }
            }
            return count >= minAmount;
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid material in condition: " + itemTypeStr);
            return false;
        }
    }

    private boolean evaluatePlayerLevelCondition(Player player, ObjectiveCondition condition) {
        int minLevel = condition.getIntParameter("minLevel", 0);
        int maxLevel = condition.getIntParameter("maxLevel", Integer.MAX_VALUE);
        int playerLevel = player.getLevel();
        return playerLevel >= minLevel && playerLevel <= maxLevel;
    }

    private boolean evaluateWeatherCondition(Player player, ObjectiveCondition condition) {
        String weather = condition.getParameter("weather", "CLEAR");
        boolean hasStorm = player.getWorld().hasStorm();
        boolean hasThunder = player.getWorld().isThundering();

        return switch (weather.toUpperCase()) {
            case "CLEAR" -> !hasStorm && !hasThunder;
            case "RAIN" -> hasStorm && !hasThunder;
            case "THUNDER" -> hasThunder;
            default -> false;
        };
    }

    private boolean evaluateEquipmentCondition(Player player, ObjectiveCondition condition) {
        String slot = condition.getParameter("equipmentSlot", "HAND");
        String itemTypeStr = condition.getParameter("itemType", null);

        if (itemTypeStr == null) {
            return false;
        }

        try {
            Material required = Material.valueOf(itemTypeStr.toUpperCase());
            ItemStack equipped = switch (slot.toUpperCase()) {
                case "HAND" -> player.getInventory().getItemInMainHand();
                case "OFF_HAND" -> player.getInventory().getItemInOffHand();
                case "HEAD" -> player.getInventory().getHelmet();
                case "CHEST" -> player.getInventory().getChestplate();
                case "LEGS" -> player.getInventory().getLeggings();
                case "FEET" -> player.getInventory().getBoots();
                default -> null;
            };
            return equipped != null && equipped.getType() == required;
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid material in equipment condition: " + itemTypeStr);
            return false;
        }
    }

    // ========================================
    // Group Operations
    // ========================================

    @Override
    public CompletableFuture<Boolean> isGroupComplete(UUID playerUuid, String questId, ObjectiveGroup group) {
        return getGroupStatus(playerUuid, questId, group)
            .thenApply(GroupStatus::completed);
    }

    @Override
    public CompletableFuture<GroupStatus> getGroupStatus(UUID playerUuid, String questId, ObjectiveGroup group) {
        return getQuestObjectiveProgress(playerUuid, questId)
            .thenCompose(allProgress -> {
                // Check conditions
                return evaluateConditions(playerUuid, group.conditions())
                    .thenApply(conditionsMet -> {
                        if (!conditionsMet) {
                            // Group conditions not met - return empty status
                            return new GroupStatus(
                                group.groupId(),
                                false,
                                0,
                                group.getRequiredCompletions(),
                                group.getTotalItemCount(),
                                List.of(),
                                List.of(),
                                group.objectives().stream().map(ObjectiveDTO::objectiveId).collect(Collectors.toList())
                            );
                        }

                        // Calculate completion status
                        List<String> completedIds = new ArrayList<>();
                        List<String> activeIds = new ArrayList<>();
                        List<String> pendingIds = new ArrayList<>();

                        for (ObjectiveDTO obj : group.objectives()) {
                            Optional<QuestObjectiveProgressDTO> progress = allProgress.stream()
                                .filter(p -> p.objectiveId().equals(obj.objectiveId()))
                                .findFirst();

                            if (progress.isPresent() && progress.get().completed()) {
                                completedIds.add(obj.objectiveId());
                            } else if (isObjectiveActive(group, obj, completedIds)) {
                                activeIds.add(obj.objectiveId());
                            } else {
                                pendingIds.add(obj.objectiveId());
                            }
                        }

                        int completedCount = completedIds.size();
                        int required = group.getRequiredCompletions();
                        boolean complete = switch (group.completionType()) {
                            case ALL, ORDERED -> completedCount >= group.getTotalItemCount();
                            case ANY -> completedCount >= 1;
                            case COUNT -> completedCount >= group.requiredCount();
                        };

                        return new GroupStatus(
                            group.groupId(),
                            complete,
                            completedCount,
                            required,
                            group.getTotalItemCount(),
                            activeIds,
                            completedIds,
                            pendingIds
                        );
                    });
            });
    }

    /**
     * Determines if an objective is currently active based on group type and completed objectives.
     */
    private boolean isObjectiveActive(ObjectiveGroup group, ObjectiveDTO objective, List<String> completedIds) {
        if (group.completionType() == CompletionType.ORDERED) {
            // In ordered groups, only the next uncompleted objective is active
            for (ObjectiveDTO obj : group.objectives()) {
                if (!completedIds.contains(obj.objectiveId())) {
                    return obj.objectiveId().equals(objective.objectiveId());
                }
            }
            return false;
        } else {
            // In other groups, all uncompleted objectives are active
            return !completedIds.contains(objective.objectiveId());
        }
    }

    @Override
    public CompletableFuture<List<ObjectiveDTO>> getActiveObjectives(
            UUID playerUuid, String questId, ObjectiveGroup group) {
        return getGroupStatus(playerUuid, questId, group)
            .thenApply(status -> group.objectives().stream()
                .filter(obj -> status.activeObjectiveIds().contains(obj.objectiveId()))
                .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<List<ObjectiveDTO>> getUpcomingObjectives(
            UUID playerUuid, String questId, ObjectiveGroup group) {
        return getGroupStatus(playerUuid, questId, group)
            .thenApply(status -> group.objectives().stream()
                .filter(obj -> status.pendingObjectiveIds().contains(obj.objectiveId()))
                .collect(Collectors.toList()));
    }

    // ========================================
    // Bulk Operations
    // ========================================

    @Override
    public CompletableFuture<Void> initializeGroupProgress(UUID playerUuid, String questId, ObjectiveGroup group) {
        logger.debug("Initializing group progress for " + playerUuid + "/" + questId + "/" + group.groupId());

        List<CompletableFuture<QuestObjectiveProgressDTO>> futures = group.objectives().stream()
            .map(obj -> progressService.initializeObjective(
                playerUuid, questId, obj.objectiveId(), obj.requiredAmount()))
            .collect(Collectors.toList());

        // Also initialize subgroups recursively
        for (ObjectiveGroup subGroup : group.subGroups()) {
            futures.add(initializeGroupProgress(playerUuid, questId, subGroup)
                .thenApply(v -> null));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    @Override
    public CompletableFuture<Void> resetGroupProgress(UUID playerUuid, String questId, ObjectiveGroup group) {
        logger.debug("Resetting group progress for " + playerUuid + "/" + questId + "/" + group.groupId());

        List<CompletableFuture<Void>> futures = group.objectives().stream()
            .map(obj -> resetProgress(playerUuid, questId, obj.objectiveId()))
            .collect(Collectors.toList());

        // Also reset subgroups recursively
        for (ObjectiveGroup subGroup : group.subGroups()) {
            futures.add(resetGroupProgress(playerUuid, questId, subGroup));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    // ========================================
    // Fallback Mode
    // ========================================

    @Override
    public boolean isInFallbackMode() {
        return progressService.isInFallbackMode();
    }
}
