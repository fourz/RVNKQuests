package org.fourz.RVNKQuests.data.repository;

import org.fourz.RVNKQuests.data.dto.QuestTagDTO;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for quest tag persistence.
 *
 * <p>Provides async data access operations for quest tags with
 * CRUD operations and assignment management.</p>
 */
public interface ITagRepository {

    /**
     * Save a new tag or update existing one.
     *
     * @param tag The tag to save
     * @return CompletableFuture with the saved tag (with generated ID)
     */
    CompletableFuture<QuestTagDTO> save(QuestTagDTO tag);

    /**
     * Find a tag by ID.
     *
     * @param id The tag ID
     * @return CompletableFuture with optional tag
     */
    CompletableFuture<Optional<QuestTagDTO>> findById(int id);

    /**
     * Find a tag by unique name.
     *
     * @param name The tag name
     * @return CompletableFuture with optional tag
     */
    CompletableFuture<Optional<QuestTagDTO>> findByName(String name);

    /**
     * Get all tags.
     *
     * @return CompletableFuture with list of tags
     */
    CompletableFuture<List<QuestTagDTO>> findAll();

    /**
     * Get tags assigned to a specific quest.
     *
     * @param questId The quest identifier
     * @return CompletableFuture with list of tags
     */
    CompletableFuture<List<QuestTagDTO>> findByQuestId(String questId);

    /**
     * Update an existing tag.
     *
     * @param tag The tag with updated data
     * @return CompletableFuture with true if update succeeded
     */
    CompletableFuture<Boolean> update(QuestTagDTO tag);

    /**
     * Delete a tag by ID.
     *
     * @param id The tag ID
     * @return CompletableFuture with true if deletion succeeded
     */
    CompletableFuture<Boolean> deleteById(int id);

    /**
     * Delete a tag by name.
     *
     * @param name The tag name
     * @return CompletableFuture with true if deletion succeeded
     */
    CompletableFuture<Boolean> deleteByName(String name);

    /**
     * Assign a tag to a quest.
     *
     * @param questId The quest identifier
     * @param tagId The tag ID
     * @return CompletableFuture with true if assignment succeeded
     */
    CompletableFuture<Boolean> assignTagToQuest(String questId, int tagId);

    /**
     * Remove a tag from a quest.
     *
     * @param questId The quest identifier
     * @param tagId The tag ID
     * @return CompletableFuture with true if removal succeeded
     */
    CompletableFuture<Boolean> removeTagFromQuest(String questId, int tagId);

    /**
     * Remove all tags from a quest.
     *
     * @param questId The quest identifier
     * @return CompletableFuture with number of tags removed
     */
    CompletableFuture<Integer> removeAllTagsFromQuest(String questId);

    /**
     * Get all quests with a specific tag.
     *
     * @param tagId The tag ID
     * @return CompletableFuture with list of quest IDs
     */
    CompletableFuture<List<String>> findQuestsByTagId(int tagId);

    /**
     * Check if a tag is assigned to a quest.
     *
     * @param questId The quest identifier
     * @param tagId The tag ID
     * @return CompletableFuture with true if assigned
     */
    CompletableFuture<Boolean> isTagAssignedToQuest(String questId, int tagId);

    /**
     * Check if a tag exists by name.
     *
     * @param name The tag name
     * @return CompletableFuture with true if tag exists
     */
    CompletableFuture<Boolean> existsByName(String name);

    /**
     * Count total tags.
     *
     * @return CompletableFuture with count of tags
     */
    CompletableFuture<Long> count();

    /**
     * Check if the repository is available.
     *
     * @return true if repository can perform operations
     */
    boolean isAvailable();
}
