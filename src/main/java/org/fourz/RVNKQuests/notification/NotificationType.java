package org.fourz.RVNKQuests.notification;

import org.bukkit.Sound;

/**
 * Defines the types of quest notifications and their default settings.
 */
public enum NotificationType {
    /**
     * Notification when a quest is started.
     */
    QUEST_START(NotificationChannel.TITLE, Sound.ENTITY_PLAYER_LEVELUP, 10, 40, 10),

    /**
     * Notification when a quest is completed.
     */
    QUEST_COMPLETE(NotificationChannel.TITLE, Sound.UI_TOAST_CHALLENGE_COMPLETE, 10, 60, 20),

    /**
     * Notification when a quest fails.
     */
    QUEST_FAILED(NotificationChannel.TITLE, Sound.ENTITY_VILLAGER_NO, 10, 40, 10),

    /**
     * Notification when a quest objective progresses.
     */
    OBJECTIVE_PROGRESS(NotificationChannel.ACTION_BAR, null, 0, 40, 0),

    /**
     * Notification when an objective is completed.
     */
    OBJECTIVE_COMPLETE(NotificationChannel.ACTION_BAR, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0, 60, 0),

    /**
     * Notification when a quest becomes available.
     */
    QUEST_AVAILABLE(NotificationChannel.CHAT, Sound.BLOCK_NOTE_BLOCK_CHIME, 0, 0, 0),

    /**
     * Notification when a quest milestone is reached.
     */
    MILESTONE(NotificationChannel.TITLE, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 5, 30, 5),

    /**
     * Notification for quest chain progress.
     */
    CHAIN_PROGRESS(NotificationChannel.CHAT, Sound.BLOCK_NOTE_BLOCK_PLING, 0, 0, 0);

    private final NotificationChannel defaultChannel;
    private final Sound defaultSound;
    private final int fadeIn;
    private final int stay;
    private final int fadeOut;

    NotificationType(NotificationChannel defaultChannel, Sound defaultSound,
                     int fadeIn, int stay, int fadeOut) {
        this.defaultChannel = defaultChannel;
        this.defaultSound = defaultSound;
        this.fadeIn = fadeIn;
        this.stay = stay;
        this.fadeOut = fadeOut;
    }

    public NotificationChannel getDefaultChannel() {
        return defaultChannel;
    }

    public Sound getDefaultSound() {
        return defaultSound;
    }

    public int getFadeIn() {
        return fadeIn;
    }

    public int getStay() {
        return stay;
    }

    public int getFadeOut() {
        return fadeOut;
    }

    public boolean hasSound() {
        return defaultSound != null;
    }
}
