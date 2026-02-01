package org.fourz.RVNKQuests.leaderboard;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable leaderboard entry representing a player's ranking in a specific leaderboard.
 * Implements Comparable for sorting by rank.
 *
 * @param playerUuid Player's unique identifier
 * @param playerName Player's current display name
 * @param leaderboardType Type of leaderboard this entry belongs to
 * @param value Numeric value used for ranking (e.g., quest count, completion time)
 * @param rank Player's rank position (1 = first place)
 * @param updatedAt Timestamp when this entry was last updated
 */
public record LeaderboardEntry(
    UUID playerUuid,
    String playerName,
    LeaderboardType leaderboardType,
    int value,
    int rank,
    Instant updatedAt
) implements Comparable<LeaderboardEntry> {

    /**
     * Compare entries by rank (ascending order).
     * Lower rank number = higher position.
     *
     * @param other Entry to compare to
     * @return Comparison result
     */
    @Override
    public int compareTo(LeaderboardEntry other) {
        return Integer.compare(this.rank, other.rank);
    }

    /**
     * Get a formatted display string for this entry.
     * Format: "#1 PlayerName - 42 quests"
     *
     * @return Formatted display string
     */
    public String getDisplayString() {
        return String.format("#%d %s - %d", rank, playerName, value);
    }

    /**
     * Check if this entry is a top 3 position (podium).
     *
     * @return True if rank is 1, 2, or 3
     */
    public boolean isPodium() {
        return rank >= 1 && rank <= 3;
    }

    /**
     * Get the rank color for display purposes.
     * Gold for 1st, Silver for 2nd, Bronze for 3rd, White for others.
     *
     * @return Minecraft color code
     */
    public String getRankColor() {
        return switch (rank) {
            case 1 -> "&6"; // Gold
            case 2 -> "&7"; // Silver (light gray)
            case 3 -> "&c"; // Bronze (red)
            default -> "&f"; // White
        };
    }
}
