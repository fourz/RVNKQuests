package org.fourz.RVNKQuests.service;

import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.notification.NotificationChannel;
import org.fourz.RVNKQuests.notification.NotificationType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for quest notification management.
 *
 * <p>Provides high-level operations for sending quest-related notifications
 * through multiple channels with player preference support.</p>
 *
 * <p>Pattern compliance:</p>
 * <ul>
 *   <li>Interface uses "I" prefix per RVNK coding standards</li>
 *   <li>All preference operations return CompletableFuture for async execution</li>
 *   <li>Notification sending is synchronous for immediate feedback</li>
 * </ul>
 */
public interface INotificationService {

    // ==================== Quest Event Notifications ====================

    /**
     * Sends a quest start notification to the player.
     *
     * @param player The player to notify
     * @param questName The quest name to display
     * @param questDescription Optional quest description
     */
    void notifyQuestStart(Player player, String questName, String questDescription);

    /**
     * Sends a quest completion notification to the player.
     *
     * @param player The player to notify
     * @param questName The quest name
     */
    void notifyQuestComplete(Player player, String questName);

    /**
     * Sends a quest failure notification to the player.
     *
     * @param player The player to notify
     * @param questName The quest name
     * @param reason Optional failure reason
     */
    void notifyQuestFailed(Player player, String questName, String reason);

    // ==================== Objective Notifications ====================

    /**
     * Sends an objective progress notification to the player.
     *
     * @param player The player to notify
     * @param objectiveName The objective name
     * @param current Current progress value
     * @param target Target value for completion
     */
    void notifyObjectiveProgress(Player player, String objectiveName, int current, int target);

    /**
     * Sends an objective completion notification to the player.
     *
     * @param player The player to notify
     * @param objectiveName The objective name
     */
    void notifyObjectiveComplete(Player player, String objectiveName);

    // ==================== General Notifications ====================

    /**
     * Sends a quest available notification to the player.
     *
     * @param player The player to notify
     * @param questName The available quest name
     */
    void notifyQuestAvailable(Player player, String questName);

    /**
     * Sends a milestone notification to the player.
     *
     * @param player The player to notify
     * @param milestoneName The milestone reached
     */
    void notifyMilestone(Player player, String milestoneName);

    /**
     * Sends a chain progress notification to the player.
     *
     * @param player The player to notify
     * @param chainName The quest chain name
     * @param currentQuest Current quest in chain
     * @param totalQuests Total quests in chain
     */
    void notifyChainProgress(Player player, String chainName, int currentQuest, int totalQuests);

    // ==================== Custom Notifications ====================

    /**
     * Sends a custom notification with specified type and message.
     *
     * @param player The player to notify
     * @param type The notification type
     * @param title Primary message/title
     * @param subtitle Secondary message (optional)
     */
    void sendNotification(Player player, NotificationType type, String title, String subtitle);

    /**
     * Sends a notification to a specific channel.
     *
     * @param player The player to notify
     * @param channel The notification channel
     * @param message The message to send
     */
    void sendToChannel(Player player, NotificationChannel channel, String message);

    // ==================== Boss Bar Management ====================

    /**
     * Shows or updates a boss bar for active quest tracking.
     *
     * @param player The player to show the bar to
     * @param questName The quest name
     * @param progress Progress percentage (0.0 to 1.0)
     */
    void showQuestProgressBar(Player player, String questName, double progress);

    /**
     * Hides the quest progress boss bar for a player.
     *
     * @param player The player to hide the bar from
     */
    void hideQuestProgressBar(Player player);

    // ==================== Player Preferences ====================

    /**
     * Gets the enabled notification channels for a player.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture with set of enabled channels
     */
    CompletableFuture<Set<NotificationChannel>> getEnabledChannels(UUID playerUuid);

    /**
     * Sets whether a notification channel is enabled for a player.
     *
     * @param playerUuid The player's UUID
     * @param channel The notification channel
     * @param enabled Whether to enable or disable
     * @return CompletableFuture that completes when preference is saved
     */
    CompletableFuture<Void> setChannelEnabled(UUID playerUuid, NotificationChannel channel, boolean enabled);

    /**
     * Checks if a specific notification type is enabled for a player.
     *
     * @param playerUuid The player's UUID
     * @param type The notification type
     * @return true if the notification type is enabled
     */
    boolean isNotificationEnabled(UUID playerUuid, NotificationType type);

    /**
     * Resets all notification preferences to defaults for a player.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture that completes when reset is done
     */
    CompletableFuture<Void> resetPreferences(UUID playerUuid);

    // ==================== Cooldown Management ====================

    /**
     * Checks if a notification is on cooldown for a player.
     *
     * @param playerUuid The player's UUID
     * @param type The notification type
     * @return true if notification is on cooldown
     */
    boolean isOnCooldown(UUID playerUuid, NotificationType type);

    /**
     * Sets the cooldown duration for a notification type.
     *
     * @param type The notification type
     * @param milliseconds Cooldown duration in milliseconds
     */
    void setCooldown(NotificationType type, long milliseconds);

    /**
     * Gets the default cooldown for a notification type.
     *
     * @param type The notification type
     * @return Cooldown duration in milliseconds
     */
    long getCooldown(NotificationType type);

    // ==================== Service Status ====================

    /**
     * Checks if the notification service is available.
     *
     * @return true if service can send notifications
     */
    boolean isAvailable();

    /**
     * Cleans up resources (call on plugin disable).
     */
    void shutdown();
}
