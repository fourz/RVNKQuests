package org.fourz.RVNKQuests.service;

import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for quest management operations.
 * Provides cross-plugin access to RVNKQuests quest functionality.
 *
 * <p>Register with RVNKCore ServiceRegistry for use by other plugins:</p>
 * <pre>{@code
 * registry.registerService(IQuestService.class, questManager);
 * }</pre>
 *
 * <p>Pattern compliance:</p>
 * <ul>
 *   <li>Interface uses "I" prefix per RVNK coding standards</li>
 *   <li>All I/O operations return CompletableFuture (async-first)</li>
 *   <li>Thread-safe for concurrent access</li>
 * </ul>
 */
public interface IQuestService {

    // ==================== Quest Registration ====================

    /**
     * Registers a quest with the quest system.
     * @param quest The quest to register
     */
    void registerQuest(Quest quest);

    /**
     * Unregisters a quest from the quest system.
     * @param questId The quest identifier
     * @return true if quest was unregistered
     */
    boolean unregisterQuest(String questId);

    // ==================== Quest Retrieval ====================

    /**
     * Gets a quest by its identifier.
     * @param questId The quest identifier
     * @return The quest, or empty if not found
     */
    Optional<Quest> getQuest(String questId);

    /**
     * Gets all registered quests.
     * @return List of all registered quests
     */
    List<Quest> getAllQuests();

    /**
     * Gets all active quests (quests with at least one player engaged).
     * @return List of active quests
     */
    List<Quest> getActiveQuests();

    /**
     * Gets the number of registered quests.
     * @return Quest count
     */
    int getQuestCount();

    // ==================== Quest State Operations ====================

    /**
     * Gets a player's current state for a quest.
     * @param playerId The player's UUID
     * @param questId The quest identifier
     * @return The quest state (NOT_STARTED if not begun)
     */
    CompletableFuture<QuestState> getPlayerQuestState(UUID playerId, String questId);

    /**
     * Updates a player's quest state.
     * @param playerId The player's UUID
     * @param questId The quest identifier
     * @param newState The new quest state
     * @return CompletableFuture that completes when state is updated
     */
    CompletableFuture<Void> updatePlayerQuestState(UUID playerId, String questId, QuestState newState);

    /**
     * Starts a quest for a player.
     * @param playerId The player's UUID
     * @param questId The quest identifier
     * @return true if quest was started successfully
     */
    CompletableFuture<Boolean> startQuest(UUID playerId, String questId);

    /**
     * Completes a quest for a player.
     * @param playerId The player's UUID
     * @param questId The quest identifier
     * @return true if quest was completed successfully
     */
    CompletableFuture<Boolean> completeQuest(UUID playerId, String questId);

    /**
     * Abandons a quest for a player.
     * @param playerId The player's UUID
     * @param questId The quest identifier
     * @return true if quest was abandoned successfully
     */
    CompletableFuture<Boolean> abandonQuest(UUID playerId, String questId);

    /**
     * Resets a player's progress on a quest.
     * @param playerId The player's UUID
     * @param questId The quest identifier
     * @return true if reset succeeded
     */
    CompletableFuture<Boolean> resetQuest(UUID playerId, String questId);

    // ==================== Player Engagement ====================

    /**
     * Gets all quests a player has started.
     * @param playerId The player's UUID
     * @return List of quest IDs the player has started
     */
    CompletableFuture<List<String>> getPlayerActiveQuests(UUID playerId);

    /**
     * Gets all quests a player has completed.
     * @param playerId The player's UUID
     * @return List of quest IDs the player has completed
     */
    CompletableFuture<List<String>> getPlayerCompletedQuests(UUID playerId);

    /**
     * Checks if a player is eligible to start a quest.
     * @param playerId The player's UUID
     * @param questId The quest identifier
     * @return true if player can start the quest
     */
    CompletableFuture<Boolean> canStartQuest(UUID playerId, String questId);

    // ==================== Service Status ====================

    /**
     * Checks if the service is operating in fallback mode.
     * @return true if database unavailable and using fallback storage
     */
    boolean isInFallbackMode();

    /**
     * Reloads all quests and their configurations.
     */
    void reloadQuests();

    /**
     * Cleans up all quests and listeners (call on plugin disable).
     */
    void shutdown();
}
