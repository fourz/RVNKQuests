package org.fourz.RVNKQuests.data.dto;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Data Transfer Object for quest prerequisites.
 * Immutable and thread-safe for cross-boundary data transfer.
 *
 * <p>Represents a requirement that must be met before a quest
 * can be started. Supports multiple prerequisite types including
 * quest completion, level requirements, permissions, and custom conditions.</p>
 *
 * @since 1.0
 */
public record QuestPrerequisite(
    String prerequisiteId,
    PrerequisiteType type,
    String targetId,
    int requiredValue,
    String description,
    boolean optional,
    Map<String, String> metadata
) {
    /**
     * Types of quest prerequisites.
     */
    public enum PrerequisiteType {
        /**
         * Requires completion of another quest.
         * targetId: Quest ID that must be completed
         */
        QUEST_COMPLETE,
        
        /**
         * Requires completion of a quest chain.
         * targetId: Chain ID that must be completed
         */
        CHAIN_COMPLETE,
        
        /**
         * Requires a minimum player level.
         * requiredValue: Minimum XP level
         */
        PLAYER_LEVEL,
        
        /**
         * Requires a permission node.
         * targetId: Permission node required
         */
        PERMISSION,
        
        /**
         * Requires an item in inventory.
         * targetId: Item type/ID, requiredValue: amount
         */
        ITEM_REQUIRED,
        
        /**
         * Requires completion of N quests from a category.
         * targetId: Category name, requiredValue: count
         */
        CATEGORY_COUNT,
        
        /**
         * Requires a specific reputation level.
         * targetId: Faction/reputation ID, requiredValue: level
         */
        REPUTATION,
        
        /**
         * Requires a specific time range.
         * Uses metadata for time configuration.
         */
        TIME_RANGE,
        
        /**
         * Requires player to be in specific world.
         * targetId: World name
         */
        WORLD,
        
        /**
         * Custom prerequisite with plugin-defined logic.
         * targetId: Custom evaluator ID
         */
        CUSTOM
    }
    
    /**
     * Compact constructor with validation and defensive copies.
     */
    public QuestPrerequisite {
        Objects.requireNonNull(prerequisiteId, "prerequisiteId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        
        // Ensure non-negative required value
        if (requiredValue < 0) {
            requiredValue = 0;
        }
        
        // Defensive copy
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
    
    // ==================== Factory Methods ====================
    
    /**
     * Creates a quest completion prerequisite.
     *
     * @param id The prerequisite ID
     * @param questId The quest that must be completed
     * @return A new QuestPrerequisite
     */
    public static QuestPrerequisite questComplete(String id, String questId) {
        return new QuestPrerequisite(
            id, PrerequisiteType.QUEST_COMPLETE, questId, 1,
            "Complete quest: " + questId, false, Map.of()
        );
    }
    
    /**
     * Creates a chain completion prerequisite.
     *
     * @param id The prerequisite ID
     * @param chainId The chain that must be completed
     * @return A new QuestPrerequisite
     */
    public static QuestPrerequisite chainComplete(String id, String chainId) {
        return new QuestPrerequisite(
            id, PrerequisiteType.CHAIN_COMPLETE, chainId, 1,
            "Complete chain: " + chainId, false, Map.of()
        );
    }
    
    /**
     * Creates a player level prerequisite.
     *
     * @param id The prerequisite ID
     * @param minLevel The minimum required level
     * @return A new QuestPrerequisite
     */
    public static QuestPrerequisite playerLevel(String id, int minLevel) {
        return new QuestPrerequisite(
            id, PrerequisiteType.PLAYER_LEVEL, null, minLevel,
            "Reach level " + minLevel, false, Map.of()
        );
    }
    
    /**
     * Creates a permission prerequisite.
     *
     * @param id The prerequisite ID
     * @param permission The required permission node
     * @return A new QuestPrerequisite
     */
    public static QuestPrerequisite permission(String id, String permission) {
        return new QuestPrerequisite(
            id, PrerequisiteType.PERMISSION, permission, 1,
            "Requires permission: " + permission, false, Map.of()
        );
    }
    
    /**
     * Creates an item requirement prerequisite.
     *
     * @param id The prerequisite ID
     * @param itemId The item type/ID
     * @param amount The required amount
     * @return A new QuestPrerequisite
     */
    public static QuestPrerequisite itemRequired(String id, String itemId, int amount) {
        return new QuestPrerequisite(
            id, PrerequisiteType.ITEM_REQUIRED, itemId, amount,
            "Requires " + amount + "x " + itemId, false, Map.of()
        );
    }
    
    /**
     * Creates a world prerequisite.
     *
     * @param id The prerequisite ID
     * @param worldName The required world name
     * @return A new QuestPrerequisite
     */
    public static QuestPrerequisite world(String id, String worldName) {
        return new QuestPrerequisite(
            id, PrerequisiteType.WORLD, worldName, 1,
            "Must be in " + worldName, false, Map.of()
        );
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Check if this is a quest-related prerequisite.
     *
     * @return true if this requires quest/chain completion
     */
    public boolean isQuestBased() {
        return type == PrerequisiteType.QUEST_COMPLETE || 
               type == PrerequisiteType.CHAIN_COMPLETE ||
               type == PrerequisiteType.CATEGORY_COUNT;
    }
    
    /**
     * Get a metadata value as String.
     *
     * @param key The metadata key
     * @return Optional containing the value if present
     */
    public Optional<String> getMetadata(String key) {
        return Optional.ofNullable(metadata.get(key));
    }
    
    /**
     * Get a metadata value as int.
     *
     * @param key The metadata key
     * @param defaultValue Default if missing or invalid
     * @return The int value or default
     */
    public int getMetadataInt(String key, int defaultValue) {
        String value = metadata.get(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Creates a copy marked as optional.
     *
     * @return A new QuestPrerequisite with optional=true
     */
    public QuestPrerequisite asOptional() {
        return new QuestPrerequisite(prerequisiteId, type, targetId, 
                                     requiredValue, description, true, metadata);
    }
    
    /**
     * Creates a copy with updated description.
     *
     * @param newDescription The new description
     * @return A new QuestPrerequisite
     */
    public QuestPrerequisite withDescription(String newDescription) {
        return new QuestPrerequisite(prerequisiteId, type, targetId,
                                     requiredValue, newDescription, optional, metadata);
    }
    
    // ==================== Builder ====================
    
    /**
     * Builder for constructing QuestPrerequisite with fluent API.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder class for QuestPrerequisite.
     */
    public static class Builder {
        private String prerequisiteId;
        private PrerequisiteType type;
        private String targetId;
        private int requiredValue = 1;
        private String description;
        private boolean optional = false;
        private Map<String, String> metadata = Map.of();
        
        public Builder prerequisiteId(String prerequisiteId) {
            this.prerequisiteId = prerequisiteId;
            return this;
        }
        
        public Builder type(PrerequisiteType type) {
            this.type = type;
            return this;
        }
        
        public Builder targetId(String targetId) {
            this.targetId = targetId;
            return this;
        }
        
        public Builder requiredValue(int requiredValue) {
            this.requiredValue = requiredValue;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder optional(boolean optional) {
            this.optional = optional;
            return this;
        }
        
        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public QuestPrerequisite build() {
            return new QuestPrerequisite(
                prerequisiteId, type, targetId, requiredValue,
                description, optional, metadata
            );
        }
    }
}
