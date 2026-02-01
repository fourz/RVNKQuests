package org.fourz.RVNKQuests.leaderboard;

/**
 * Types of leaderboards available for quest tracking.
 * Each leaderboard type tracks different player achievements and has its own update schedule.
 */
public enum LeaderboardType {
    MOST_COMPLETED(
        "Most Quests Completed",
        "Players ranked by total number of completed quests",
        300 // 5 minutes
    ),
    FASTEST_COMPLETION(
        "Fastest Completion",
        "Players ranked by average quest completion time",
        300
    ),
    DAILY_CHAMPION(
        "Daily Champion",
        "Players ranked by quests completed today",
        60 // 1 minute for active daily tracking
    ),
    WEEKLY_CHAMPION(
        "Weekly Champion",
        "Players ranked by quests completed this week",
        300
    ),
    STREAK_MASTER(
        "Streak Master",
        "Players ranked by current consecutive completion streak",
        300
    );

    private final String displayName;
    private final String description;
    private final int updateIntervalSeconds;

    LeaderboardType(String displayName, String description, int updateIntervalSeconds) {
        this.displayName = displayName;
        this.description = description;
        this.updateIntervalSeconds = updateIntervalSeconds;
    }

    /**
     * Get the human-readable display name for this leaderboard type.
     *
     * @return Display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the description of what this leaderboard tracks.
     *
     * @return Description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get the update interval in seconds for this leaderboard type.
     * Daily leaderboards update more frequently (1 minute) while others update every 5 minutes.
     *
     * @return Update interval in seconds
     */
    public int getUpdateIntervalSeconds() {
        return updateIntervalSeconds;
    }

    /**
     * Parse leaderboard type from string, case-insensitive.
     *
     * @param name String name to parse
     * @return LeaderboardType or null if not found
     */
    public static LeaderboardType fromString(String name) {
        if (name == null) {
            return null;
        }
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
