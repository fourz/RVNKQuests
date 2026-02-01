package org.fourz.rvnkquests.integration.dto;

import java.util.Objects;

/**
 * Data transfer object for lore entries from RVNKLore.
 *
 * <p>Lightweight DTO for cross-plugin communication without
 * requiring compile-time dependency on RVNKLore classes.</p>
 *
 * <p>This record is immutable and designed for safe transfer
 * between RVNKLore and RVNKQuests plugins via the ServiceRegistry.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // From RVNKLore LoreEntry
 * LoreEntryDTO dto = new LoreEntryDTO(
 *     entry.getId(),
 *     entry.getName(),
 *     entry.getDescription(),
 *     entry.getType().name()
 * );
 *
 * // Use in quest narrative
 * if (dto.type().equals("QUEST")) {
 *     sendQuestIntro(player, dto.description());
 * }
 * }</pre>
 *
 * @param id Lore entry ID (UUID string)
 * @param name Lore entry name (human-readable)
 * @param description Lore entry description/content (narrative text)
 * @param type Lore type (LANDMARK, CITY, PLAYER, ITEM, QUEST, etc.)
 *
 * @since 1.0
 * @see org.fourz.rvnklore.lore.LoreEntry
 * @see org.fourz.rvnklore.lore.LoreType
 */
public record LoreEntryDTO(
    String id,
    String name,
    String description,
    String type
) {
    /**
     * Compact constructor with validation.
     *
     * @throws NullPointerException if id, name, or type is null
     */
    public LoreEntryDTO {
        Objects.requireNonNull(id, "id required");
        Objects.requireNonNull(name, "name required");
        Objects.requireNonNull(type, "type required");
    }

    /**
     * Check if this lore entry is of a specific type.
     *
     * @param typeName The type name to check (case-insensitive)
     * @return true if this entry's type matches
     */
    public boolean isType(String typeName) {
        return type.equalsIgnoreCase(typeName);
    }

    /**
     * Check if this is a quest-related lore entry.
     *
     * @return true if type is QUEST
     */
    public boolean isQuestLore() {
        return isType("QUEST");
    }

    /**
     * Check if this is an NPC-related lore entry.
     *
     * @return true if type is PLAYER or FACTION
     */
    public boolean isNPCLore() {
        return isType("PLAYER") || isType("FACTION");
    }

    /**
     * Check if this is a location-related lore entry.
     *
     * @return true if type is LANDMARK, CITY, or PATH
     */
    public boolean isLocationLore() {
        return isType("LANDMARK") || isType("CITY") || isType("PATH");
    }

    /**
     * Get a formatted display string for this lore entry.
     *
     * @return Formatted string: "name (type)"
     */
    public String getDisplayName() {
        return name + " (" + type + ")";
    }

    @Override
    public String toString() {
        return "LoreEntryDTO{id=" + id + ", name='" + name + "', type=" + type + "}";
    }
}
