package org.fourz.RVNKQuests.data.repository;

import org.fourz.RVNKQuests.data.dto.QuestCategoryDTO;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for quest category persistence.
 *
 * <p>Provides async data access operations for quest categories with
 * CRUD operations and ordering support.</p>
 */
public interface ICategoryRepository {

    /**
     * Save a new category or update existing one.
     *
     * @param category The category to save
     * @return CompletableFuture with the saved category (with generated ID)
     */
    CompletableFuture<QuestCategoryDTO> save(QuestCategoryDTO category);

    /**
     * Find a category by ID.
     *
     * @param id The category ID
     * @return CompletableFuture with optional category
     */
    CompletableFuture<Optional<QuestCategoryDTO>> findById(int id);

    /**
     * Find a category by unique name.
     *
     * @param name The category name
     * @return CompletableFuture with optional category
     */
    CompletableFuture<Optional<QuestCategoryDTO>> findByName(String name);

    /**
     * Get all categories ordered by sort order.
     *
     * @return CompletableFuture with list of categories
     */
    CompletableFuture<List<QuestCategoryDTO>> findAll();

    /**
     * Get all categories ordered by name.
     *
     * @return CompletableFuture with list of categories
     */
    CompletableFuture<List<QuestCategoryDTO>> findAllOrderedByName();

    /**
     * Update an existing category.
     *
     * @param category The category with updated data
     * @return CompletableFuture with true if update succeeded
     */
    CompletableFuture<Boolean> update(QuestCategoryDTO category);

    /**
     * Delete a category by ID.
     *
     * @param id The category ID
     * @return CompletableFuture with true if deletion succeeded
     */
    CompletableFuture<Boolean> deleteById(int id);

    /**
     * Delete a category by name.
     *
     * @param name The category name
     * @return CompletableFuture with true if deletion succeeded
     */
    CompletableFuture<Boolean> deleteByName(String name);

    /**
     * Check if a category exists by name.
     *
     * @param name The category name
     * @return CompletableFuture with true if category exists
     */
    CompletableFuture<Boolean> existsByName(String name);

    /**
     * Count total categories.
     *
     * @return CompletableFuture with count of categories
     */
    CompletableFuture<Long> count();

    /**
     * Check if the repository is available.
     *
     * @return true if repository can perform operations
     */
    boolean isAvailable();
}
