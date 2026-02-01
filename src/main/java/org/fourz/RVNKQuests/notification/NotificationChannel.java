package org.fourz.RVNKQuests.notification;

/**
 * Defines the available notification channels for quest events.
 * Each channel has different visibility and persistence characteristics.
 */
public enum NotificationChannel {
    /**
     * Title and subtitle displayed on screen.
     * Best for major events: quest start, quest complete.
     */
    TITLE,

    /**
     * Action bar text at bottom of screen.
     * Best for progress updates: objective progress, kills remaining.
     */
    ACTION_BAR,

    /**
     * Boss bar progress indicator at top of screen.
     * Best for ongoing tracking: active quest progress percentage.
     */
    BOSS_BAR,

    /**
     * Chat message in player's chat window.
     * Best for detailed info: quest available, quest failed.
     */
    CHAT,

    /**
     * Sound effect only.
     * Best for feedback: completion sounds, milestone sounds.
     */
    SOUND
}
