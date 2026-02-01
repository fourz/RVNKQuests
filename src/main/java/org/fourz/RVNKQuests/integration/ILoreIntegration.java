package org.fourz.rvnkquests.integration;

import org.fourz.rvnkquests.integration.dto.LoreEntryDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Integration service for RVNKLore cross-plugin communication.
 *
 * <p>Provides quest-related lore functionality including:</p>
 * <ul>
 *   <li>Lore entry retrieval for quest narratives</li>
 *   <li>Lore discovery grants as quest rewards</li>
 *   <li>NPC dialogue from lore database</li>
 *   <li>Graceful degradation when RVNKLore unavailable</li>
 * </ul>
 *
 * <p>All methods return CompletableFuture for async-safe operations.</p>
 *
 * <h2>Service Discovery</h2>
 * <p>This integration uses reflection to discover RVNKLore services via
 * RVNKCore's ServiceRegistry, avoiding compile-time dependencies.</p>
 *
 * <h2>Error Handling</h2>
 * <p>When RVNKLore is unavailable:</p>
 * <ul>
 *   <li>Lore lookups return {@code Optional.empty()}</li>
 *   <li>NPC dialogue methods use fallback strings</li>
 *   <li>Discovery grants fail gracefully without breaking quests</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Get lore for quest narrative
 * loreIntegration.getLoreForQuest("ancient_ruins_quest")
 *     .thenAccept(loreOpt -> {
 *         loreOpt.ifPresent(lore ->
 *             sendQuestNarrative(player, lore.description()));
 *     });
 *
 * // Grant lore discovery as reward
 * loreIntegration.grantLoreDiscovery(playerId, "ancient_civilization_01")
 *     .thenAccept(success -> {
 *         if (success) {
 *             notifyPlayer(playerId, "New lore discovered!");
 *         }
 *     });
 *
 * // Get NPC dialogue with fallback
 * loreIntegration.getNPCDialogueOrDefault("merchant_john", "greeting", "Hello!")
 *     .thenAccept(dialogue -> sendMessage(player, dialogue));
 * }</pre>
 *
 * @since 1.0
 * @see org.fourz.rvnklore.service.ILoreService
 * @see org.fourz.rvnklore.service.IItemService
 */
public interface ILoreIntegration {

    /**
     * Check if RVNKLore integration is available.
     *
     * <p>This method checks if the RVNKLore plugin is loaded and
     * its services are accessible via the ServiceRegistry.</p>
     *
     * @return true if RVNKLore plugin is loaded and services accessible
     */
    boolean isLoreAvailable();

    /**
     * Get lore entry for quest narrative context.
     *
     * <p>Quest designers can reference lore entries to provide narrative
     * background for quest objectives and storylines. The quest ID is
     * mapped to a lore entry name using the pattern {@code "quest_<questId>"}.</p>
     *
     * <p>If RVNKLore is unavailable, returns an empty Optional.</p>
     *
     * @param questId The quest ID to lookup associated lore
     * @return Future containing lore entry if found, empty otherwise
     */
    CompletableFuture<Optional<LoreEntryDTO>> getLoreForQuest(String questId);

    /**
     * Get lore entry by name.
     *
     * <p>Allows quests to reference specific lore entries by name
     * for dialogue, objectives, or reward descriptions.</p>
     *
     * <p>If RVNKLore is unavailable, returns an empty Optional.</p>
     *
     * @param loreName The name of the lore entry
     * @return Future containing lore entry if found, empty otherwise
     */
    CompletableFuture<Optional<LoreEntryDTO>> getLoreByName(String loreName);

    /**
     * Get lore entry by UUID.
     *
     * <p>Direct lookup by lore entry UUID.</p>
     *
     * @param loreId The UUID of the lore entry
     * @return Future containing lore entry if found, empty otherwise
     */
    CompletableFuture<Optional<LoreEntryDTO>> getLoreById(UUID loreId);

    /**
     * Grant lore discovery to player as quest reward.
     *
     * <p>When a player completes a quest, this unlocks lore entries
     * in their discovery journal, encouraging exploration of the
     * world's narrative content.</p>
     *
     * <p>If RVNKLore is unavailable, logs a warning and returns false.</p>
     *
     * @param playerId The player UUID
     * @param loreId The lore entry ID to unlock
     * @return Future containing true if discovery granted successfully
     */
    CompletableFuture<Boolean> grantLoreDiscovery(UUID playerId, String loreId);

    /**
     * Get NPC dialogue from lore database.
     *
     * <p>Quest NPCs can pull dialogue from the lore system, enabling
     * dynamic quest conversations that reference world lore.</p>
     *
     * <p>The NPC dialogue is stored in lore entries with names following
     * the pattern {@code "npc_<npcId>_<context>"}.</p>
     *
     * <p>If RVNKLore is unavailable, returns an empty Optional.</p>
     *
     * @param npcId The NPC identifier
     * @param context The dialogue context (e.g., "greeting", "quest_start")
     * @return Future containing dialogue text if found, empty otherwise
     */
    CompletableFuture<Optional<String>> getNPCDialogue(String npcId, String context);

    /**
     * Get NPC dialogue with fallback.
     *
     * <p>Same as {@link #getNPCDialogue(String, String)} but never returns
     * empty - uses the provided fallback text when lore is unavailable or
     * dialogue not found.</p>
     *
     * @param npcId The NPC identifier
     * @param context The dialogue context
     * @param fallback Default dialogue if lore not found
     * @return Future containing dialogue text (from lore or fallback)
     */
    CompletableFuture<String> getNPCDialogueOrDefault(String npcId, String context, String fallback);

    /**
     * Check if player has discovered a lore entry.
     *
     * <p>Useful for quest prerequisites or conditional objectives
     * based on lore discovery state.</p>
     *
     * <p>If RVNKLore is unavailable, returns false.</p>
     *
     * @param playerId The player UUID
     * @param loreId The lore entry ID
     * @return Future containing true if player has discovered the lore
     */
    CompletableFuture<Boolean> hasDiscovered(UUID playerId, String loreId);

    /**
     * Get all discovered lore entries for a player.
     *
     * <p>Returns a list of lore entry IDs that the player has discovered.</p>
     *
     * <p>If RVNKLore is unavailable, returns an empty list.</p>
     *
     * @param playerId The player UUID
     * @return Future containing list of discovered lore entry IDs
     */
    CompletableFuture<List<String>> getPlayerDiscoveries(UUID playerId);
}
