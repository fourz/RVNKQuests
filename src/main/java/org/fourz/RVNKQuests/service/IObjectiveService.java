package org.fourz.RVNKQuests.service;

import org.fourz.RVNKQuests.data.dto.ObjectiveCondition;
import org.fourz.RVNKQuests.data.dto.ObjectiveDTO;
import org.fourz.RVNKQuests.data.dto.ObjectiveGroup;
import org.fourz.RVNKQuests.data.dto.QuestObjectiveProgressDTO;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for managing quest objectives, conditions, and groups.
 * Provides operations for objective progress, condition evaluation, and group completion.
 *
 * <p>This service handles:</p>
 * <ul>
 *   <li>Objective progress tracking and updates</li>
 *   <li>Condition evaluation for conditional objectives</li>
 *   <li>Group completion calculation for parallel/nested objectives</li>
 *   <li>Active objective determination based on conditions and order</li>
 * </ul>
 *
 * @see ObjectiveDTO
 * @see ObjectiveGroup
 * @see ObjectiveCondition
 */
public interface IObjectiveService {

    // ========================================
    // Objective Progress Operations
    // ========================================

    /**
     * Gets the current progress for a specific objective.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param objectiveId The objective identifier
     * @return CompletableFuture with the progress, or empty if not started
     */
    CompletableFuture<java.util.Optional<QuestObjectiveProgressDTO>> getObjectiveProgress(
        UUID playerUuid, String questId, String objectiveId);

    /**
     * Gets all objective progress for a quest.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @return CompletableFuture with list of objective progress records
     */
    CompletableFuture<List<QuestObjectiveProgressDTO>> getQuestObjectiveProgress(
        UUID playerUuid, String questId);

    /**
     * Increments progress on an objective.
     * Handles completion detection and triggers.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param objectiveId The objective identifier
     * @param amount The amount to increment
     * @return CompletableFuture with the updated progress
     */
    CompletableFuture<QuestObjectiveProgressDTO> incrementProgress(
        UUID playerUuid, String questId, String objectiveId, int amount);

    /**
     * Sets absolute progress on an objective.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param objectiveId The objective identifier
     * @param progress The new progress value
     * @return CompletableFuture with the updated progress
     */
    CompletableFuture<QuestObjectiveProgressDTO> setProgress(
        UUID playerUuid, String questId, String objectiveId, int progress);

    /**
     * Marks an objective as completed.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param objectiveId The objective identifier
     * @return CompletableFuture that completes when marked
     */
    CompletableFuture<Void> markCompleted(UUID playerUuid, String questId, String objectiveId);

    /**
     * Resets progress on an objective.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param objectiveId The objective identifier
     * @return CompletableFuture that completes when reset
     */
    CompletableFuture<Void> resetProgress(UUID playerUuid, String questId, String objectiveId);

    // ========================================
    // Condition Evaluation
    // ========================================

    /**
     * Evaluates whether a condition is met for a player.
     *
     * @param playerUuid The player's UUID
     * @param condition The condition to evaluate
     * @return CompletableFuture with true if condition is met
     */
    CompletableFuture<Boolean> evaluateCondition(UUID playerUuid, ObjectiveCondition condition);

    /**
     * Evaluates multiple conditions (all must pass).
     *
     * @param playerUuid The player's UUID
     * @param conditions The conditions to evaluate
     * @return CompletableFuture with true if all conditions are met
     */
    CompletableFuture<Boolean> evaluateConditions(UUID playerUuid, List<ObjectiveCondition> conditions);

    /**
     * Gets detailed evaluation result for a condition.
     *
     * @param playerUuid The player's UUID
     * @param condition The condition to evaluate
     * @return CompletableFuture with detailed result
     */
    CompletableFuture<ConditionResult> evaluateConditionWithDetails(
        UUID playerUuid, ObjectiveCondition condition);

    // ========================================
    // Group Operations
    // ========================================

    /**
     * Checks if an objective group is completed for a player.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param group The objective group
     * @return CompletableFuture with true if group is complete
     */
    CompletableFuture<Boolean> isGroupComplete(UUID playerUuid, String questId, ObjectiveGroup group);

    /**
     * Gets completion status for a group with details.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param group The objective group
     * @return CompletableFuture with detailed group status
     */
    CompletableFuture<GroupStatus> getGroupStatus(UUID playerUuid, String questId, ObjectiveGroup group);

    /**
     * Gets the currently active objectives in a group.
     * Takes into account ordered groups and conditions.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param group The objective group
     * @return CompletableFuture with list of active objectives
     */
    CompletableFuture<List<ObjectiveDTO>> getActiveObjectives(
        UUID playerUuid, String questId, ObjectiveGroup group);

    /**
     * Gets the next objectives that will become active.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param group The objective group
     * @return CompletableFuture with list of upcoming objectives
     */
    CompletableFuture<List<ObjectiveDTO>> getUpcomingObjectives(
        UUID playerUuid, String questId, ObjectiveGroup group);

    // ========================================
    // Bulk Operations
    // ========================================

    /**
     * Initializes progress tracking for all objectives in a group.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param group The objective group
     * @return CompletableFuture that completes when initialized
     */
    CompletableFuture<Void> initializeGroupProgress(UUID playerUuid, String questId, ObjectiveGroup group);

    /**
     * Resets all progress for objectives in a group.
     *
     * @param playerUuid The player's UUID
     * @param questId The quest identifier
     * @param group The objective group
     * @return CompletableFuture that completes when reset
     */
    CompletableFuture<Void> resetGroupProgress(UUID playerUuid, String questId, ObjectiveGroup group);

    // ========================================
    // Fallback Mode
    // ========================================

    /**
     * Checks if the service is operating in fallback mode.
     *
     * @return true if in fallback mode
     */
    boolean isInFallbackMode();

    // ========================================
    // Supporting Records
    // ========================================

    /**
     * Result of condition evaluation with details.
     *
     * @param passed Whether the condition passed
     * @param conditionId The evaluated condition's ID
     * @param message Human-readable explanation
     * @param actualValue The actual value found (if applicable)
     * @param expectedValue The expected value (if applicable)
     */
    record ConditionResult(
        boolean passed,
        String conditionId,
        String message,
        String actualValue,
        String expectedValue
    ) {
        /**
         * Creates a passing result.
         */
        public static ConditionResult pass(String conditionId, String message) {
            return new ConditionResult(true, conditionId, message, null, null);
        }

        /**
         * Creates a failing result.
         */
        public static ConditionResult fail(String conditionId, String message) {
            return new ConditionResult(false, conditionId, message, null, null);
        }

        /**
         * Creates a failing result with value comparison.
         */
        public static ConditionResult fail(String conditionId, String message,
                                            String actualValue, String expectedValue) {
            return new ConditionResult(false, conditionId, message, actualValue, expectedValue);
        }
    }

    /**
     * Status of an objective group.
     *
     * @param groupId The group identifier
     * @param completed Whether the group is complete
     * @param completedCount Number of completed items
     * @param requiredCount Number required for completion
     * @param totalCount Total items in group
     * @param activeObjectiveIds IDs of currently active objectives
     * @param completedObjectiveIds IDs of completed objectives
     * @param pendingObjectiveIds IDs of not-yet-active objectives
     */
    record GroupStatus(
        String groupId,
        boolean completed,
        int completedCount,
        int requiredCount,
        int totalCount,
        List<String> activeObjectiveIds,
        List<String> completedObjectiveIds,
        List<String> pendingObjectiveIds
    ) {
        /**
         * Gets completion percentage (0.0 to 1.0).
         */
        public double getCompletionPercentage() {
            if (requiredCount == 0) return 1.0;
            return Math.min(1.0, (double) completedCount / requiredCount);
        }

        /**
         * Gets remaining objectives needed.
         */
        public int getRemainingCount() {
            return Math.max(0, requiredCount - completedCount);
        }
    }
}
