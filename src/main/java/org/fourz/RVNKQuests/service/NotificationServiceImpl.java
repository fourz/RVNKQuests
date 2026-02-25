package org.fourz.RVNKQuests.service;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.notification.NotificationChannel;
import org.fourz.RVNKQuests.notification.NotificationType;
import org.fourz.RVNKQuests.integration.PreferencesServiceLookup;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.rvnkcore.api.service.PlayerPreferencesService;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of INotificationService for quest notifications.
 *
 * <p>Handles multiple notification channels including titles, action bars,
 * boss bars, chat messages, and sound effects with player preference support.</p>
 *
 * <p>Respects PlayerPreferencesService from RVNKCore (Phase 3 integration) for
 * persistent, centralized notification preferences management.</p>
 */
public class NotificationServiceImpl implements INotificationService {

    private final RVNKQuests plugin;
    private final LogManager logger;
    private final PreferencesServiceLookup prefsLookup;

    // Player boss bars for quest tracking
    private final Map<UUID, BossBar> playerBossBars = new ConcurrentHashMap<>();

    // Cooldown tracking: player UUID -> notification type -> last sent timestamp
    private final Map<UUID, Map<NotificationType, Long>> cooldownTracker = new ConcurrentHashMap<>();

    // Default cooldowns per notification type (milliseconds)
    private final Map<NotificationType, Long> cooldownSettings = new EnumMap<>(NotificationType.class);

    // Player preferences (in-memory, fallback when PlayerPreferencesService unavailable)
    private final Map<UUID, Set<NotificationChannel>> playerDisabledChannels = new ConcurrentHashMap<>();

    // Chat message prefixes
    private static final String PREFIX = ChatColor.GOLD + "[Quest] " + ChatColor.RESET;
    private static final String SUCCESS_PREFIX = ChatColor.GREEN + "[Quest] " + ChatColor.RESET;
    private static final String ERROR_PREFIX = ChatColor.RED + "[Quest] " + ChatColor.RESET;

    public NotificationServiceImpl(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.prefsLookup = new PreferencesServiceLookup(plugin);
        initializeDefaultCooldowns();
        logger.info("NotificationService initialized");
    }

    private void initializeDefaultCooldowns() {
        // Set default cooldowns to prevent spam
        cooldownSettings.put(NotificationType.QUEST_START, 0L); // No cooldown
        cooldownSettings.put(NotificationType.QUEST_COMPLETE, 0L);
        cooldownSettings.put(NotificationType.QUEST_FAILED, 0L);
        cooldownSettings.put(NotificationType.OBJECTIVE_PROGRESS, 500L); // 500ms cooldown
        cooldownSettings.put(NotificationType.OBJECTIVE_COMPLETE, 0L);
        cooldownSettings.put(NotificationType.QUEST_AVAILABLE, 5000L); // 5s cooldown
        cooldownSettings.put(NotificationType.MILESTONE, 0L);
        cooldownSettings.put(NotificationType.CHAIN_PROGRESS, 1000L); // 1s cooldown
    }

    // ==================== Quest Event Notifications ====================

    @Override
    public void notifyQuestStart(Player player, String questName, String questDescription) {
        UUID playerId = player.getUniqueId();

        // Check PlayerPreferencesService first
        if (!shouldNotifyPlayerViaPreferences(playerId, "quest_start")) {
            logger.debug("Quest start notification suppressed for " + player.getName() +
                    " (notifications disabled in preferences)");
            return;
        }

        if (!canSendNotification(playerId, NotificationType.QUEST_START)) {
            return;
        }

        String title = ChatColor.GOLD + "Quest Started";
        String subtitle = ChatColor.YELLOW + questName;

        sendNotification(player, NotificationType.QUEST_START, title, subtitle);

        if (questDescription != null && !questDescription.isEmpty()) {
            sendToChannel(player, NotificationChannel.CHAT,
                SUCCESS_PREFIX + "Started: " + ChatColor.WHITE + questName);
            sendToChannel(player, NotificationChannel.CHAT,
                ChatColor.GRAY + questDescription);
        }

        markCooldown(player.getUniqueId(), NotificationType.QUEST_START);
        logger.debug("Sent quest start notification to " + player.getName() + ": " + questName);
    }

    @Override
    public void notifyQuestComplete(Player player, String questName) {
        UUID playerId = player.getUniqueId();

        // Check PlayerPreferencesService first
        if (!shouldNotifyPlayerViaPreferences(playerId, "quest_complete")) {
            logger.debug("Quest complete notification suppressed for " + player.getName() +
                    " (notifications disabled in preferences)");
            return;
        }

        if (!canSendNotification(playerId, NotificationType.QUEST_COMPLETE)) {
            return;
        }

        String title = ChatColor.GREEN + "Quest Complete!";
        String subtitle = ChatColor.GOLD + questName;

        sendNotification(player, NotificationType.QUEST_COMPLETE, title, subtitle);

        sendToChannel(player, NotificationChannel.CHAT,
            SUCCESS_PREFIX + "Completed: " + ChatColor.WHITE + questName + ChatColor.GREEN + " \u2713");

        // Hide quest progress bar on completion
        hideQuestProgressBar(player);

        markCooldown(player.getUniqueId(), NotificationType.QUEST_COMPLETE);
        logger.debug("Sent quest complete notification to " + player.getName() + ": " + questName);
    }

    @Override
    public void notifyQuestFailed(Player player, String questName, String reason) {
        UUID playerId = player.getUniqueId();

        // Check PlayerPreferencesService first
        if (!shouldNotifyPlayerViaPreferences(playerId, "quest_failed")) {
            logger.debug("Quest failed notification suppressed for " + player.getName() +
                    " (notifications disabled in preferences)");
            return;
        }

        if (!canSendNotification(playerId, NotificationType.QUEST_FAILED)) {
            return;
        }

        String title = ChatColor.RED + "Quest Failed";
        String subtitle = ChatColor.GRAY + questName;

        sendNotification(player, NotificationType.QUEST_FAILED, title, subtitle);

        String message = ERROR_PREFIX + "Failed: " + ChatColor.WHITE + questName;
        if (reason != null && !reason.isEmpty()) {
            message += ChatColor.GRAY + " - " + reason;
        }
        sendToChannel(player, NotificationChannel.CHAT, message);

        // Hide quest progress bar on failure
        hideQuestProgressBar(player);

        markCooldown(player.getUniqueId(), NotificationType.QUEST_FAILED);
        logger.debug("Sent quest failed notification to " + player.getName() + ": " + questName);
    }

    // ==================== Objective Notifications ====================

    @Override
    public void notifyObjectiveProgress(Player player, String objectiveName, int current, int target) {
        UUID playerId = player.getUniqueId();

        // Check PlayerPreferencesService first
        if (!shouldNotifyPlayerViaPreferences(playerId, "objective_progress")) {
            return;
        }

        if (!canSendNotification(playerId, NotificationType.OBJECTIVE_PROGRESS)) {
            return;
        }

        String message = ChatColor.YELLOW + objectiveName + ": " +
                        ChatColor.WHITE + current + "/" + target;

        sendToChannel(player, NotificationChannel.ACTION_BAR, message);
        markCooldown(player.getUniqueId(), NotificationType.OBJECTIVE_PROGRESS);
    }

    @Override
    public void notifyObjectiveComplete(Player player, String objectiveName) {
        UUID playerId = player.getUniqueId();

        // Check PlayerPreferencesService first
        if (!shouldNotifyPlayerViaPreferences(playerId, "objective_complete")) {
            return;
        }

        if (!canSendNotification(playerId, NotificationType.OBJECTIVE_COMPLETE)) {
            return;
        }

        String message = ChatColor.GREEN + "\u2713 " + objectiveName + " Complete!";
        sendToChannel(player, NotificationChannel.ACTION_BAR, message);

        // Play completion sound
        NotificationType type = NotificationType.OBJECTIVE_COMPLETE;
        if (type.hasSound() && isChannelEnabled(player.getUniqueId(), NotificationChannel.SOUND)) {
            player.playSound(player.getLocation(), type.getDefaultSound(), 1.0f, 1.0f);
        }

        markCooldown(player.getUniqueId(), NotificationType.OBJECTIVE_COMPLETE);
        logger.debug("Sent objective complete notification to " + player.getName() + ": " + objectiveName);
    }

    // ==================== General Notifications ====================

    @Override
    public void notifyQuestAvailable(Player player, String questName) {
        UUID playerId = player.getUniqueId();

        // Check PlayerPreferencesService first
        if (!shouldNotifyPlayerViaPreferences(playerId, "quest_available")) {
            return;
        }

        if (!canSendNotification(playerId, NotificationType.QUEST_AVAILABLE)) {
            return;
        }

        sendToChannel(player, NotificationChannel.CHAT,
            PREFIX + "New quest available: " + ChatColor.GOLD + questName);

        NotificationType type = NotificationType.QUEST_AVAILABLE;
        if (type.hasSound() && isChannelEnabled(player.getUniqueId(), NotificationChannel.SOUND)) {
            player.playSound(player.getLocation(), type.getDefaultSound(), 0.7f, 1.2f);
        }

        markCooldown(player.getUniqueId(), NotificationType.QUEST_AVAILABLE);
    }

    @Override
    public void notifyMilestone(Player player, String milestoneName) {
        UUID playerId = player.getUniqueId();

        // Check PlayerPreferencesService first
        if (!shouldNotifyPlayerViaPreferences(playerId, "milestone")) {
            return;
        }

        if (!canSendNotification(playerId, NotificationType.MILESTONE)) {
            return;
        }

        String title = ChatColor.AQUA + "\u2605 Milestone \u2605";
        String subtitle = ChatColor.WHITE + milestoneName;

        sendNotification(player, NotificationType.MILESTONE, title, subtitle);
        markCooldown(player.getUniqueId(), NotificationType.MILESTONE);
    }

    @Override
    public void notifyChainProgress(Player player, String chainName, int currentQuest, int totalQuests) {
        UUID playerId = player.getUniqueId();

        // Check PlayerPreferencesService first
        if (!shouldNotifyPlayerViaPreferences(playerId, "chain_progress")) {
            return;
        }

        if (!canSendNotification(playerId, NotificationType.CHAIN_PROGRESS)) {
            return;
        }

        sendToChannel(player, NotificationChannel.CHAT,
            PREFIX + "Chain Progress: " + ChatColor.GOLD + chainName +
            ChatColor.WHITE + " [" + currentQuest + "/" + totalQuests + "]");

        NotificationType type = NotificationType.CHAIN_PROGRESS;
        if (type.hasSound() && isChannelEnabled(player.getUniqueId(), NotificationChannel.SOUND)) {
            player.playSound(player.getLocation(), type.getDefaultSound(), 0.5f, 1.5f);
        }

        markCooldown(player.getUniqueId(), NotificationType.CHAIN_PROGRESS);
    }

    // ==================== Custom Notifications ====================

    @SuppressWarnings("deprecation")
    @Override
    public void sendNotification(Player player, NotificationType type, String title, String subtitle) {
        UUID playerId = player.getUniqueId();
        NotificationChannel channel = type.getDefaultChannel();

        if (!isChannelEnabled(playerId, channel)) {
            return;
        }

        switch (channel) {
            case TITLE:
                player.sendTitle(
                    title != null ? title : "",
                    subtitle != null ? subtitle : "",
                    type.getFadeIn(),
                    type.getStay(),
                    type.getFadeOut()
                );
                break;
            case ACTION_BAR:
                player.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(title != null ? title : "")
                );
                break;
            case CHAT:
                player.sendMessage(title != null ? title : "");
                break;
            default:
                break;
        }

        // Play sound if configured
        if (type.hasSound() && isChannelEnabled(playerId, NotificationChannel.SOUND)) {
            player.playSound(player.getLocation(), type.getDefaultSound(), 1.0f, 1.0f);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void sendToChannel(Player player, NotificationChannel channel, String message) {
        if (!isChannelEnabled(player.getUniqueId(), channel)) {
            return;
        }

        switch (channel) {
            case TITLE:
                player.sendTitle(message, "", 10, 40, 10);
                break;
            case ACTION_BAR:
                player.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(message)
                );
                break;
            case CHAT:
                player.sendMessage(message);
                break;
            case SOUND:
                // No-op for sound-only channel
                break;
            case BOSS_BAR:
                // Use showQuestProgressBar for boss bar
                break;
        }
    }

    // ==================== Boss Bar Management ====================

    @Override
    public void showQuestProgressBar(Player player, String questName, double progress) {
        UUID playerId = player.getUniqueId();

        if (!isChannelEnabled(playerId, NotificationChannel.BOSS_BAR)) {
            return;
        }

        BossBar bar = playerBossBars.computeIfAbsent(playerId, id -> {
            BossBar newBar = Bukkit.createBossBar(
                ChatColor.GOLD + questName,
                BarColor.YELLOW,
                BarStyle.SEGMENTED_10
            );
            newBar.addPlayer(player);
            return newBar;
        });

        // Update progress and title
        bar.setTitle(ChatColor.GOLD + questName + ChatColor.WHITE + " - " +
                    ChatColor.GREEN + String.format("%.0f%%", progress * 100));
        bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));

        // Color changes based on progress
        if (progress >= 0.75) {
            bar.setColor(BarColor.GREEN);
        } else if (progress >= 0.5) {
            bar.setColor(BarColor.YELLOW);
        } else if (progress >= 0.25) {
            bar.setColor(BarColor.WHITE);
        } else {
            bar.setColor(BarColor.RED);
        }

        bar.setVisible(true);
    }

    @Override
    public void hideQuestProgressBar(Player player) {
        UUID playerId = player.getUniqueId();
        BossBar bar = playerBossBars.remove(playerId);
        if (bar != null) {
            bar.removePlayer(player);
            bar.setVisible(false);
        }
    }

    // ==================== Player Preferences ====================

    @Override
    public CompletableFuture<Set<NotificationChannel>> getEnabledChannels(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Set<NotificationChannel> disabled = playerDisabledChannels.getOrDefault(
                playerUuid, Collections.emptySet());
            Set<NotificationChannel> enabled = EnumSet.allOf(NotificationChannel.class);
            enabled.removeAll(disabled);
            return enabled;
        });
    }

    @Override
    public CompletableFuture<Void> setChannelEnabled(UUID playerUuid, NotificationChannel channel, boolean enabled) {
        return CompletableFuture.runAsync(() -> {
            Set<NotificationChannel> disabled = playerDisabledChannels.computeIfAbsent(
                playerUuid, id -> ConcurrentHashMap.newKeySet());

            if (enabled) {
                disabled.remove(channel);
            } else {
                disabled.add(channel);
            }

            logger.debug("Player " + playerUuid + " set channel " + channel + " to " + enabled);
        });
    }

    @Override
    public boolean isNotificationEnabled(UUID playerUuid, NotificationType type) {
        return isChannelEnabled(playerUuid, type.getDefaultChannel());
    }

    @Override
    public CompletableFuture<Void> resetPreferences(UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            playerDisabledChannels.remove(playerUuid);
            logger.debug("Reset notification preferences for player " + playerUuid);
        });
    }

    private boolean isChannelEnabled(UUID playerUuid, NotificationChannel channel) {
        Set<NotificationChannel> disabled = playerDisabledChannels.get(playerUuid);
        return disabled == null || !disabled.contains(channel);
    }

    // ==================== Cooldown Management ====================

    @Override
    public boolean isOnCooldown(UUID playerUuid, NotificationType type) {
        Map<NotificationType, Long> playerCooldowns = cooldownTracker.get(playerUuid);
        if (playerCooldowns == null) {
            return false;
        }

        Long lastSent = playerCooldowns.get(type);
        if (lastSent == null) {
            return false;
        }

        long cooldown = cooldownSettings.getOrDefault(type, 0L);
        return System.currentTimeMillis() - lastSent < cooldown;
    }

    @Override
    public void setCooldown(NotificationType type, long milliseconds) {
        cooldownSettings.put(type, milliseconds);
    }

    @Override
    public long getCooldown(NotificationType type) {
        return cooldownSettings.getOrDefault(type, 0L);
    }

    /**
     * Checks if a player has notifications enabled via PlayerPreferencesService.
     * Returns true if service unavailable (graceful fallback).
     * This is a synchronous check with short timeout.
     *
     * @param playerUuid Player UUID
     * @param notificationType Notification type (e.g., "quest_start")
     * @return true if player should receive notifications
     */
    private boolean shouldNotifyPlayerViaPreferences(UUID playerUuid, String notificationType) {
        if (!prefsLookup.isAvailable()) {
            return true; // Service unavailable - allow notification
        }

        try {
            PlayerPreferencesService prefs = prefsLookup.getService();
            // Check master toggle with 500ms timeout
            Boolean canNotify = prefs.isNotificationEnabled(playerUuid, "rvnkquests", notificationType)
                    .get(500, java.util.concurrent.TimeUnit.MILLISECONDS);
            return canNotify != null && canNotify;
        } catch (Exception e) {
            logger.debug("Error checking notification preferences: " + e.getMessage() + ", allowing notification");
            return true; // On error, allow notification (fail open)
        }
    }

    private boolean canSendNotification(UUID playerUuid, NotificationType type) {
        return !isOnCooldown(playerUuid, type) && isNotificationEnabled(playerUuid, type);
    }

    private void markCooldown(UUID playerUuid, NotificationType type) {
        cooldownTracker.computeIfAbsent(playerUuid, id -> new ConcurrentHashMap<>())
                      .put(type, System.currentTimeMillis());
    }

    // ==================== Service Status ====================

    @Override
    public boolean isAvailable() {
        return plugin.isEnabled();
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down NotificationService");

        // Remove all boss bars
        for (Map.Entry<UUID, BossBar> entry : playerBossBars.entrySet()) {
            BossBar bar = entry.getValue();
            bar.removeAll();
            bar.setVisible(false);
        }
        playerBossBars.clear();

        // Clear cooldown tracking
        cooldownTracker.clear();

        logger.info("NotificationService shutdown complete");
    }
}
