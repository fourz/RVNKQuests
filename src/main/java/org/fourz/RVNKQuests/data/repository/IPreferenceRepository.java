package org.fourz.RVNKQuests.data.repository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for player preference database operations.
 *
 * <p>Manages CRUD operations for quest notification preferences stored locally
 * in the RVNKQuests database. This provides plugin-local preference persistence
 * independent of RVNKCore's centralized PlayerPreferencesService.</p>
 *
 * <p>All operations are asynchronous via CompletableFuture to prevent blocking
 * the main server thread.</p>
 *
 * @since 1.0-SNAPSHOT
 */
public interface IPreferenceRepository {

    /**
     * Saves a single preference key-value pair for a player.
     *
     * @param playerUuid The player's UUID
     * @param prefKey The preference key (e.g., "master_enabled", "quest_start_enabled")
     * @param prefValue The preference value as string (JSON if complex)
     * @return CompletableFuture that completes when the preference is saved
     */
    CompletableFuture<Void> savePreference(UUID playerUuid, String prefKey, String prefValue);

    /**
     * Retrieves a single preference value for a player.
     *
     * @param playerUuid The player's UUID
     * @param prefKey The preference key
     * @return CompletableFuture containing the preference value, or null if not found
     */
    CompletableFuture<String> getPreference(UUID playerUuid, String prefKey);

    /**
     * Retrieves all preferences for a player.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture containing map of preference key → value
     */
    CompletableFuture<Map<String, String>> getAllPreferences(UUID playerUuid);

    /**
     * Deletes a specific preference for a player.
     *
     * @param playerUuid The player's UUID
     * @param prefKey The preference key to delete
     * @return CompletableFuture that completes when the preference is deleted
     */
    CompletableFuture<Void> deletePreference(UUID playerUuid, String prefKey);

    /**
     * Deletes all preferences for a player.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture that completes when all preferences are deleted
     */
    CompletableFuture<Void> deleteAllPreferences(UUID playerUuid);

    /**
     * Checks if a preference exists for a player.
     *
     * @param playerUuid The player's UUID
     * @param prefKey The preference key
     * @return CompletableFuture containing true if the preference exists
     */
    CompletableFuture<Boolean> hasPreference(UUID playerUuid, String prefKey);
}
