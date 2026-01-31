package org.fourz.RVNKQuests.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.dto.ObjectiveCondition;
import org.fourz.RVNKQuests.data.dto.ObjectiveCondition.ConditionType;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.service.IObjectiveService.ConditionResult;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Evaluates {@link ObjectiveCondition} instances against player state.
 * Supports all standard condition types with extensibility for custom conditions.
 *
 * <p>Thread-safe and async-compatible. Condition evaluation is optimized
 * for frequent calls during gameplay.</p>
 */
public class ConditionEvaluator {

    private final RVNKQuests plugin;
    private final LogManager logger;

    /**
     * Creates a new condition evaluator.
     *
     * @param plugin The main plugin instance
     */
    public ConditionEvaluator(RVNKQuests plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.logger = LogManager.getInstance(plugin, ConditionEvaluator.class);
    }

    /**
     * Evaluates a condition for a player.
     *
     * @param playerUuid The player's UUID
     * @param condition The condition to evaluate
     * @return CompletableFuture with true if condition passes
     */
    public CompletableFuture<Boolean> evaluate(UUID playerUuid, ObjectiveCondition condition) {
        return evaluateWithDetails(playerUuid, condition)
            .thenApply(ConditionResult::passed);
    }

    /**
     * Evaluates a condition with detailed result.
     *
     * @param playerUuid The player's UUID
     * @param condition The condition to evaluate
     * @return CompletableFuture with detailed evaluation result
     */
    public CompletableFuture<ConditionResult> evaluateWithDetails(UUID playerUuid, ObjectiveCondition condition) {
        if (condition == null) {
            return CompletableFuture.completedFuture(
                ConditionResult.pass("null", "No condition to evaluate"));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                ConditionResult result = evaluateConditionType(playerUuid, condition);

                // Handle inversion
                if (condition.isInverted()) {
                    return new ConditionResult(
                        !result.passed(),
                        result.conditionId(),
                        result.passed() ? "Inverted: condition passed but required to fail"
                                       : "Inverted: condition failed as required",
                        result.actualValue(),
                        result.expectedValue()
                    );
                }

                return result;
            } catch (Exception e) {
                logger.error("Error evaluating condition " + condition.conditionId() + ": " + e.getMessage());
                return ConditionResult.fail(condition.conditionId(),
                    "Evaluation error: " + e.getMessage());
            }
        });
    }

    /**
     * Evaluates the specific condition type.
     */
    private ConditionResult evaluateConditionType(UUID playerUuid, ObjectiveCondition condition) {
        return switch (condition.type()) {
            case TIME_RANGE -> evaluateTimeRange(condition);
            case LOCATION -> evaluateLocation(playerUuid, condition);
            case QUEST_STATE -> evaluateQuestState(playerUuid, condition);
            case OBJECTIVE_COMPLETE -> evaluateObjectiveComplete(playerUuid, condition);
            case PERMISSION -> evaluatePermission(playerUuid, condition);
            case ITEM_IN_INVENTORY -> evaluateItemInInventory(playerUuid, condition);
            case PLAYER_LEVEL -> evaluatePlayerLevel(playerUuid, condition);
            case WEATHER -> evaluateWeather(playerUuid, condition);
            case EQUIPMENT -> evaluateEquipment(playerUuid, condition);
            case CUSTOM -> evaluateCustom(playerUuid, condition);
        };
    }

    /**
     * Evaluates TIME_RANGE condition.
     */
    private ConditionResult evaluateTimeRange(ObjectiveCondition condition) {
        int startTime = condition.getIntParameter("startTime", 0);
        int endTime = condition.getIntParameter("endTime", 24000);
        boolean isRealTime = Boolean.parseBoolean(condition.getParameter("isRealTime", "false"));

        long currentTime;
        if (isRealTime) {
            // Real-world time (hour of day)
            currentTime = java.time.LocalTime.now().getHour() * 1000;
        } else {
            // Use first world's time or default
            World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            currentTime = world != null ? world.getTime() : 0;
        }

        boolean inRange;
        if (startTime <= endTime) {
            inRange = currentTime >= startTime && currentTime <= endTime;
        } else {
            // Handles overnight ranges (e.g., 22000 to 6000)
            inRange = currentTime >= startTime || currentTime <= endTime;
        }

        if (inRange) {
            return ConditionResult.pass(condition.conditionId(),
                "Time " + currentTime + " is within range [" + startTime + ", " + endTime + "]");
        } else {
            return ConditionResult.fail(condition.conditionId(),
                "Time not in range",
                String.valueOf(currentTime),
                "[" + startTime + ", " + endTime + "]");
        }
    }

    /**
     * Evaluates LOCATION condition.
     */
    private ConditionResult evaluateLocation(UUID playerUuid, ObjectiveCondition condition) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            return ConditionResult.fail(condition.conditionId(), "Player not online");
        }

        String requiredWorld = condition.getParameter("world", null);
        double x = condition.getDoubleParameter("x", 0);
        double y = condition.getDoubleParameter("y", 0);
        double z = condition.getDoubleParameter("z", 0);
        double radius = condition.getDoubleParameter("radius", 10);

        Location playerLoc = player.getLocation();

        // Check world if specified
        if (requiredWorld != null && !requiredWorld.isEmpty()) {
            if (!requiredWorld.equals(playerLoc.getWorld().getName())) {
                return ConditionResult.fail(condition.conditionId(),
                    "Wrong world",
                    playerLoc.getWorld().getName(),
                    requiredWorld);
            }
        }

        // Check distance
        Location targetLoc = new Location(playerLoc.getWorld(), x, y, z);
        double distance = playerLoc.distance(targetLoc);

        if (distance <= radius) {
            return ConditionResult.pass(condition.conditionId(),
                "Within " + String.format("%.1f", distance) + " blocks of target (radius: " + radius + ")");
        } else {
            return ConditionResult.fail(condition.conditionId(),
                "Too far from location",
                String.format("%.1f blocks away", distance),
                "Within " + radius + " blocks");
        }
    }

    /**
     * Evaluates QUEST_STATE condition.
     */
    private ConditionResult evaluateQuestState(UUID playerUuid, ObjectiveCondition condition) {
        String questId = condition.getParameter("questId", null);
        String requiredState = condition.getParameter("requiredState", "COMPLETED");

        if (questId == null || questId.isEmpty()) {
            return ConditionResult.fail(condition.conditionId(), "No questId specified");
        }

        IQuestProgressService progressService = plugin.getQuestProgressService();
        if (progressService == null) {
            logger.warning("QuestProgressService not available for condition evaluation");
            return ConditionResult.fail(condition.conditionId(), "Service unavailable");
        }

        try {
            QuestState actualState = progressService.getQuestState(playerUuid, questId).join();
            QuestState required = QuestState.valueOf(requiredState.toUpperCase());

            if (actualState == required) {
                return ConditionResult.pass(condition.conditionId(),
                    "Quest '" + questId + "' is " + actualState);
            } else {
                return ConditionResult.fail(condition.conditionId(),
                    "Quest state mismatch",
                    actualState.name(),
                    required.name());
            }
        } catch (IllegalArgumentException e) {
            return ConditionResult.fail(condition.conditionId(),
                "Invalid state: " + requiredState);
        }
    }

    /**
     * Evaluates OBJECTIVE_COMPLETE condition.
     */
    private ConditionResult evaluateObjectiveComplete(UUID playerUuid, ObjectiveCondition condition) {
        String questId = condition.getParameter("questId", null);
        String objectiveId = condition.getParameter("objectiveId", null);

        if (objectiveId == null || objectiveId.isEmpty()) {
            return ConditionResult.fail(condition.conditionId(), "No objectiveId specified");
        }

        IQuestProgressService progressService = plugin.getQuestProgressService();
        if (progressService == null) {
            return ConditionResult.fail(condition.conditionId(), "Service unavailable");
        }

        try {
            // If no questId, we can't check - would need context
            if (questId == null || questId.isEmpty()) {
                return ConditionResult.fail(condition.conditionId(),
                    "questId required for objective check");
            }

            var progressOpt = progressService.getObjectiveProgress(playerUuid, questId, objectiveId).join();
            if (progressOpt.isEmpty()) {
                return ConditionResult.fail(condition.conditionId(),
                    "Objective not started",
                    "not started",
                    "completed");
            }

            var progress = progressOpt.get();
            if (progress.completed()) {
                return ConditionResult.pass(condition.conditionId(),
                    "Objective '" + objectiveId + "' is complete");
            } else {
                return ConditionResult.fail(condition.conditionId(),
                    "Objective not complete",
                    progress.progressCount() + "/" + progress.targetCount(),
                    "completed");
            }
        } catch (Exception e) {
            return ConditionResult.fail(condition.conditionId(),
                "Error checking objective: " + e.getMessage());
        }
    }

    /**
     * Evaluates PERMISSION condition.
     */
    private ConditionResult evaluatePermission(UUID playerUuid, ObjectiveCondition condition) {
        String permission = condition.getParameter("permission", null);
        if (permission == null || permission.isEmpty()) {
            return ConditionResult.fail(condition.conditionId(), "No permission specified");
        }

        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) {
            return ConditionResult.fail(condition.conditionId(), "Player not online");
        }

        if (player.hasPermission(permission)) {
            return ConditionResult.pass(condition.conditionId(),
                "Player has permission: " + permission);
        } else {
            return ConditionResult.fail(condition.conditionId(),
                "Missing permission",
                "denied",
                permission);
        }
    }

    /**
     * Evaluates ITEM_IN_INVENTORY condition.
     */
    private ConditionResult evaluateItemInInventory(UUID playerUuid, ObjectiveCondition condition) {
        String itemType = condition.getParameter("itemType", null);
        int minAmount = condition.getIntParameter("minAmount", 1);

        if (itemType == null || itemType.isEmpty()) {
            return ConditionResult.fail(condition.conditionId(), "No itemType specified");
        }

        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) {
            return ConditionResult.fail(condition.conditionId(), "Player not online");
        }

        try {
            org.bukkit.Material material = org.bukkit.Material.valueOf(itemType.toUpperCase());
            int count = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == material) {
                    count += item.getAmount();
                }
            }

            if (count >= minAmount) {
                return ConditionResult.pass(condition.conditionId(),
                    "Has " + count + "x " + itemType + " (need " + minAmount + ")");
            } else {
                return ConditionResult.fail(condition.conditionId(),
                    "Insufficient items",
                    count + "x " + itemType,
                    minAmount + "x " + itemType);
            }
        } catch (IllegalArgumentException e) {
            return ConditionResult.fail(condition.conditionId(),
                "Invalid item type: " + itemType);
        }
    }

    /**
     * Evaluates PLAYER_LEVEL condition.
     */
    private ConditionResult evaluatePlayerLevel(UUID playerUuid, ObjectiveCondition condition) {
        int minLevel = condition.getIntParameter("minLevel", 0);
        int maxLevel = condition.getIntParameter("maxLevel", Integer.MAX_VALUE);

        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) {
            return ConditionResult.fail(condition.conditionId(), "Player not online");
        }

        int level = player.getLevel();
        if (level >= minLevel && level <= maxLevel) {
            return ConditionResult.pass(condition.conditionId(),
                "Player level " + level + " in range [" + minLevel + ", " + maxLevel + "]");
        } else {
            return ConditionResult.fail(condition.conditionId(),
                "Level out of range",
                String.valueOf(level),
                "[" + minLevel + ", " + maxLevel + "]");
        }
    }

    /**
     * Evaluates WEATHER condition.
     */
    private ConditionResult evaluateWeather(UUID playerUuid, ObjectiveCondition condition) {
        String requiredWeather = condition.getParameter("weather", "CLEAR");

        Player player = Bukkit.getPlayer(playerUuid);
        World world = player != null ? player.getWorld() : Bukkit.getWorlds().get(0);

        if (world == null) {
            return ConditionResult.fail(condition.conditionId(), "No world available");
        }

        String actualWeather;
        if (world.isThundering()) {
            actualWeather = "THUNDER";
        } else if (world.hasStorm()) {
            actualWeather = "RAIN";
        } else {
            actualWeather = "CLEAR";
        }

        if (actualWeather.equalsIgnoreCase(requiredWeather)) {
            return ConditionResult.pass(condition.conditionId(),
                "Weather is " + actualWeather);
        } else {
            return ConditionResult.fail(condition.conditionId(),
                "Wrong weather",
                actualWeather,
                requiredWeather.toUpperCase());
        }
    }

    /**
     * Evaluates EQUIPMENT condition.
     */
    private ConditionResult evaluateEquipment(UUID playerUuid, ObjectiveCondition condition) {
        String slot = condition.getParameter("equipmentSlot", "HAND");
        String itemType = condition.getParameter("itemType", null);

        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) {
            return ConditionResult.fail(condition.conditionId(), "Player not online");
        }

        ItemStack equipped = switch (slot.toUpperCase()) {
            case "HAND", "MAIN_HAND" -> player.getInventory().getItemInMainHand();
            case "OFF_HAND" -> player.getInventory().getItemInOffHand();
            case "HEAD", "HELMET" -> player.getInventory().getHelmet();
            case "CHEST", "CHESTPLATE" -> player.getInventory().getChestplate();
            case "LEGS", "LEGGINGS" -> player.getInventory().getLeggings();
            case "FEET", "BOOTS" -> player.getInventory().getBoots();
            default -> null;
        };

        if (equipped == null || equipped.getType() == org.bukkit.Material.AIR) {
            return ConditionResult.fail(condition.conditionId(),
                "Nothing equipped in " + slot,
                "empty",
                itemType != null ? itemType : "any item");
        }

        if (itemType == null || itemType.isEmpty()) {
            // Any item is acceptable
            return ConditionResult.pass(condition.conditionId(),
                "Has item in " + slot + ": " + equipped.getType().name());
        }

        try {
            org.bukkit.Material required = org.bukkit.Material.valueOf(itemType.toUpperCase());
            if (equipped.getType() == required) {
                return ConditionResult.pass(condition.conditionId(),
                    "Has " + required.name() + " equipped in " + slot);
            } else {
                return ConditionResult.fail(condition.conditionId(),
                    "Wrong item equipped",
                    equipped.getType().name(),
                    required.name());
            }
        } catch (IllegalArgumentException e) {
            return ConditionResult.fail(condition.conditionId(),
                "Invalid item type: " + itemType);
        }
    }

    /**
     * Evaluates CUSTOM condition via plugin extension point.
     */
    private ConditionResult evaluateCustom(UUID playerUuid, ObjectiveCondition condition) {
        String evaluatorClass = condition.getParameter("evaluatorClass", null);
        if (evaluatorClass == null || evaluatorClass.isEmpty()) {
            return ConditionResult.fail(condition.conditionId(),
                "No evaluatorClass specified for custom condition");
        }

        // Custom conditions would need a registration system
        // For now, return failure indicating extension needed
        logger.debug("Custom condition evaluation requested: " + evaluatorClass);
        return ConditionResult.fail(condition.conditionId(),
            "Custom evaluator not registered: " + evaluatorClass);
    }
}
