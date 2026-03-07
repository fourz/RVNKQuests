package org.fourz.RVNKQuests.service;

import org.fourz.RVNKQuests.data.dto.QuestChainDTO;
import org.fourz.RVNKQuests.data.dto.QuestPrerequisite;
import org.fourz.RVNKQuests.data.dto.RewardDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for quest chain management.
 * Handles chain definitions, progress tracking, and automatic unlocking.
 *
 * <p>Quest chains represent sequences of quests that form a narrative
 * or progression path. This service manages the lifecycle of chains
 * and player progress through them.</p>
 *
 * <h2>Supported Chain Patterns</h2>
 * <ul>
 *   <li><b>Linear</b>: Sequential quest completion</li>
 *   <li><b>Branching</b>: Player choice between paths</li>
 *   <li><b>Parallel</b>: Multiple quests available simultaneously</li>
 * </ul>
 *
 * @since 1.0
 * @see QuestChainDTO
 * @see QuestPrerequisite
 */
public interface IQuestChainService {
    
    // ==================== Chain Registration ====================
    
    /**
     * Registers a new quest chain definition.
     *
     * @param chain The chain definition to register
     * @return CompletableFuture completing with registration success
     */
    CompletableFuture<Boolean> registerChain(QuestChainDTO chain);
    
    /**
     * Unregisters a quest chain.
     *
     * @param chainId The chain identifier
     * @return CompletableFuture completing with removal success
     */
    CompletableFuture<Boolean> unregisterChain(String chainId);
    
    /**
     * Gets a registered chain by ID.
     *
     * @param chainId The chain identifier
     * @return CompletableFuture with Optional containing the chain
     */
    CompletableFuture<Optional<QuestChainDTO>> getChain(String chainId);
    
    /**
     * Gets all registered chains.
     *
     * @return CompletableFuture with list of all chains
     */
    CompletableFuture<List<QuestChainDTO>> getAllChains();
    
    /**
     * Gets chains by category.
     *
     * @param category The category to filter by
     * @return CompletableFuture with list of matching chains
     */
    CompletableFuture<List<QuestChainDTO>> getChainsByCategory(String category);
    
    // ==================== Progress Tracking ====================
    
    /**
     * Gets a player's progress in a chain.
     *
     * @param playerId The player UUID
     * @param chainId The chain identifier
     * @return CompletableFuture with the chain progress
     */
    CompletableFuture<ChainProgress> getProgress(UUID playerId, String chainId);
    
    /**
     * Gets all chain progress for a player.
     *
     * @param playerId The player UUID
     * @return CompletableFuture with list of all progress records
     */
    CompletableFuture<List<ChainProgress>> getAllProgress(UUID playerId);
    
    /**
     * Starts a player on a quest chain.
     *
     * @param playerId The player UUID
     * @param chainId The chain identifier
     * @return CompletableFuture with start result
     */
    CompletableFuture<ChainStartResult> startChain(UUID playerId, String chainId);
    
    /**
     * Updates chain progress when a quest is completed.
     * Called automatically by quest completion handlers.
     *
     * @param playerId The player UUID
     * @param questId The completed quest ID
     * @return CompletableFuture with list of affected chain updates
     */
    CompletableFuture<List<ChainUpdate>> onQuestComplete(UUID playerId, String questId);
    
    /**
     * Resets a player's progress in a chain.
     *
     * @param playerId The player UUID
     * @param chainId The chain identifier
     * @return CompletableFuture completing with success status
     */
    CompletableFuture<Boolean> resetProgress(UUID playerId, String chainId);
    
    // ==================== Prerequisites & Unlocking ====================
    
    /**
     * Checks if a player meets all prerequisites for a chain.
     *
     * @param playerId The player UUID
     * @param chainId The chain identifier
     * @return CompletableFuture with prerequisite validation result
     */
    CompletableFuture<PrerequisiteResult> checkPrerequisites(UUID playerId, String chainId);
    
    /**
     * Gets available (unlockable) chains for a player.
     *
     * @param playerId The player UUID
     * @return CompletableFuture with list of available chains
     */
    CompletableFuture<List<QuestChainDTO>> getAvailableChains(UUID playerId);
    
    /**
     * Gets the next available quests in a chain for a player.
     *
     * @param playerId The player UUID
     * @param chainId The chain identifier
     * @return CompletableFuture with list of next quest IDs
     */
    CompletableFuture<List<String>> getNextQuests(UUID playerId, String chainId);
    
    // ==================== Chain Completion ====================
    
    /**
     * Gets completed chains for a player.
     *
     * @param playerId The player UUID
     * @return CompletableFuture with list of completed chain IDs
     */
    CompletableFuture<List<String>> getCompletedChains(UUID playerId);
    
    /**
     * Checks if a player has completed a chain.
     *
     * @param playerId The player UUID
     * @param chainId The chain identifier
     * @return CompletableFuture with completion status
     */
    CompletableFuture<Boolean> hasCompletedChain(UUID playerId, String chainId);
    
    /**
     * Gets the completion count for a repeatable chain.
     *
     * @param playerId The player UUID
     * @param chainId The chain identifier
     * @return CompletableFuture with completion count
     */
    CompletableFuture<Integer> getCompletionCount(UUID playerId, String chainId);
    
    // ==================== Result Records ====================
    
    /**
     * Represents a player's progress in a quest chain.
     */
    record ChainProgress(
        UUID playerId,
        String chainId,
        ChainStatus status,
        List<String> completedQuests,
        List<String> activeQuests,
        List<String> lockedQuests,
        int completionCount,
        long startedAt,
        long lastUpdated
    ) {
        /**
         * Gets the completion percentage.
         */
        public double getCompletionPercentage() {
            int total = completedQuests.size() + activeQuests.size() + lockedQuests.size();
            if (total == 0) return 0.0;
            return (completedQuests.size() * 100.0) / total;
        }
        
        /**
         * Checks if any quests are currently active.
         */
        public boolean hasActiveQuests() {
            return !activeQuests.isEmpty();
        }
    }
    
    /**
     * Status of a player in a chain.
     */
    enum ChainStatus {
        /**
         * Chain not started.
         */
        NOT_STARTED,
        
        /**
         * Chain in progress.
         */
        IN_PROGRESS,
        
        /**
         * Chain completed.
         */
        COMPLETED,
        
        /**
         * Chain on cooldown (repeatable).
         */
        ON_COOLDOWN,
        
        /**
         * Chain locked (prerequisites not met).
         */
        LOCKED
    }
    
    /**
     * Result of starting a chain.
     */
    record ChainStartResult(
        boolean success,
        String chainId,
        String message,
        List<String> availableQuests
    ) {
        public static ChainStartResult success(String chainId, List<String> availableQuests) {
            return new ChainStartResult(true, chainId, "Chain started successfully", availableQuests);
        }
        
        public static ChainStartResult failure(String chainId, String reason) {
            return new ChainStartResult(false, chainId, reason, List.of());
        }
    }
    
    /**
     * Result of a chain update (e.g., quest completion).
     */
    record ChainUpdate(
        String chainId,
        UpdateType type,
        List<String> unlockedQuests,
        List<RewardDTO> deliveredRewards,
        String message
    ) {
        public enum UpdateType {
            QUEST_COMPLETED,
            NODE_COMPLETED,
            CHAIN_COMPLETED,
            QUESTS_UNLOCKED
        }
    }
    
    /**
     * Result of prerequisite validation.
     */
    record PrerequisiteResult(
        boolean satisfied,
        List<QuestPrerequisite> metPrerequisites,
        List<QuestPrerequisite> unmetPrerequisites,
        String message
    ) {
        public static PrerequisiteResult success() {
            return new PrerequisiteResult(true, List.of(), List.of(), "All prerequisites met");
        }
        
        public static PrerequisiteResult failure(List<QuestPrerequisite> unmet) {
            return new PrerequisiteResult(false, List.of(), unmet, 
                "Missing " + unmet.size() + " prerequisite(s)");
        }
    }
}
