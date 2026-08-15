package org.fourz.RVNKQuests.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.repository.IPreferenceRepository;
import org.fourz.RVNKQuests.integration.PreferencesServiceLookup;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Subcommand for managing player quest notification preferences.
 * Usage: /quest prefs
 *        /quest prefs toggle
 *        /quest prefs disable <type>
 *        /quest prefs enable <type>
 *        /quest prefs quiet <startHour> <endHour>
 *        /quest prefs quiet disable
 *        /quest prefs channel <type> <channel> <on|off>
 *
 * Writes go to PlayerPreferencesService when available, local repo otherwise.
 * Never writes to both simultaneously.
 */
public class QuestPrefsSubCommand extends BaseSubCommand {

    private static final String PLUGIN_ID = "rvnkquests";

    private final IPreferenceRepository prefsRepo;
    private final PreferencesServiceLookup prefsLookup;
    private final LogManager logger;

    public QuestPrefsSubCommand(RVNKQuests plugin, IPreferenceRepository prefsRepo) {
        super(
            plugin,
            null,  // parent will be set by command manager
            "prefs",
            "Manage your quest notification preferences",
            "/quest prefs [action]",
            "rvnkquests.prefs",
            true  // player-only
        );
        this.prefsRepo = prefsRepo;
        this.prefsLookup = new PreferencesServiceLookup(plugin);
        this.logger = LogManager.getInstance(plugin, "QuestPrefsCommand");
    }

    @Override
    public boolean executeSubCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "✖ This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        UUID playerId = player.getUniqueId();

        if (args.length == 0) {
            return showPreferences(player, playerId);
        }

        String action = args[0].toLowerCase();
        switch (action) {
            case "toggle":
                return handleToggleMaster(player, playerId);
            case "enable":
                return handleEnable(player, playerId, args);
            case "disable":
                return handleDisable(player, playerId, args);
            case "quiet":
                return handleQuietHours(player, playerId, args);
            case "channel":
                return handleChannel(player, playerId, args);
            default:
                showUsage(player);
                return true;
        }
    }

    /**
     * Routes a preference write to exactly one storage system.
     * Uses PlayerPreferencesService when available, local repository as fallback.
     */
    private CompletableFuture<Void> routeWrite(
            Supplier<CompletableFuture<Void>> localWrite,
            Supplier<CompletableFuture<Void>> serviceWrite) {
        return prefsLookup.isAvailable() ? serviceWrite.get() : localWrite.get();
    }

    private boolean showPreferences(Player player, UUID playerId) {
        player.sendMessage(ChatColor.GOLD + "===== Your Quest Preferences =====");
        player.sendMessage(ChatColor.YELLOW + "Use /quest prefs <action> to modify:");
        player.sendMessage(ChatColor.GRAY + "  toggle - Toggle all notifications on/off");
        player.sendMessage(ChatColor.GRAY + "  enable <type> - Enable notification type");
        player.sendMessage(ChatColor.GRAY + "  disable <type> - Disable notification type");
        player.sendMessage(ChatColor.GRAY + "  quiet <hour1> <hour2> - Set quiet hours (24h format)");
        player.sendMessage(ChatColor.GRAY + "  quiet disable - Disable quiet hours");
        player.sendMessage(ChatColor.GRAY + "  channel <type> <channel> <on|off> - Toggle channel");
        player.sendMessage(ChatColor.YELLOW + "Notification Types:");
        player.sendMessage(ChatColor.GRAY + "  quest_start, quest_complete, quest_failed");
        player.sendMessage(ChatColor.GRAY + "  objective_progress, objective_complete");
        player.sendMessage(ChatColor.GRAY + "  quest_available, milestone, chain_progress");
        player.sendMessage(ChatColor.YELLOW + "Channels: TITLE, ACTION_BAR, CHAT, SOUND, BOSS_BAR");
        return true;
    }

    private boolean handleToggleMaster(Player player, UUID playerId) {
        CompletableFuture<Boolean> currentFuture = prefsLookup.isAvailable()
                ? prefsLookup.getService().isMasterEnabled(playerId, PLUGIN_ID)
                : prefsRepo.getPreference(playerId, "master_enabled")
                           .thenApply(v -> "true".equals(v));

        currentFuture
            .thenAccept(currentValue -> {
                boolean newValue = !currentValue;
                routeWrite(
                    () -> prefsRepo.savePreference(playerId, "master_enabled", String.valueOf(newValue))
                                   .thenApply(v -> null),
                    () -> prefsLookup.getService().setMasterEnabled(playerId, PLUGIN_ID, newValue)
                )
                .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    String status = newValue ? "enabled" : "disabled";
                    player.sendMessage(ChatColor.GREEN + "✓ All quest notifications " + status);
                }))
                .exceptionally(ex -> {
                    logger.error("Failed to save master preference for player " + playerId, ex);
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage(ChatColor.RED + "✖ Failed to save preference"));
                    return null;
                });
            })
            .exceptionally(ex -> {
                logger.error("Failed to get master preference for player " + playerId, ex);
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.sendMessage(ChatColor.RED + "✖ Failed to load current preference"));
                return null;
            });
        return true;
    }

    private boolean handleEnable(Player player, UUID playerId, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "✖ Usage: /quest prefs enable <type>");
            player.sendMessage(ChatColor.GRAY + "Types: quest_start, quest_complete, quest_failed, objective_progress, objective_complete, quest_available, milestone, chain_progress");
            return true;
        }

        String type = args[1].toLowerCase();
        routeWrite(
            () -> prefsRepo.savePreference(playerId, type + "_enabled", "true").thenApply(v -> null),
            () -> prefsLookup.getService().setNotificationEnabled(playerId, PLUGIN_ID, type, true)
        )
        .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () ->
            player.sendMessage(ChatColor.GREEN + "✓ Enabled notifications for " + type)))
        .exceptionally(ex -> {
            logger.error("Failed to enable notification type " + type + " for player " + playerId, ex);
            plugin.getServer().getScheduler().runTask(plugin, () ->
                player.sendMessage(ChatColor.RED + "✖ Failed to save preference"));
            return null;
        });
        return true;
    }

    private boolean handleDisable(Player player, UUID playerId, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "✖ Usage: /quest prefs disable <type>");
            player.sendMessage(ChatColor.GRAY + "Types: quest_start, quest_complete, quest_failed, objective_progress, objective_complete, quest_available, milestone, chain_progress");
            return true;
        }

        String type = args[1].toLowerCase();
        routeWrite(
            () -> prefsRepo.savePreference(playerId, type + "_enabled", "false").thenApply(v -> null),
            () -> prefsLookup.getService().setNotificationEnabled(playerId, PLUGIN_ID, type, false)
        )
        .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () ->
            player.sendMessage(ChatColor.GREEN + "✓ Disabled notifications for " + type)))
        .exceptionally(ex -> {
            logger.error("Failed to disable notification type " + type + " for player " + playerId, ex);
            plugin.getServer().getScheduler().runTask(plugin, () ->
                player.sendMessage(ChatColor.RED + "✖ Failed to save preference"));
            return null;
        });
        return true;
    }

    private boolean handleQuietHours(Player player, UUID playerId, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "✖ Usage: /quest prefs quiet <hour1> <hour2> or /quest prefs quiet disable");
            return true;
        }

        if ("disable".equalsIgnoreCase(args[1])) {
            routeWrite(
                () -> prefsRepo.savePreference(playerId, "quiet_hours_enabled", "false").thenApply(v -> null),
                () -> prefsLookup.getService().setQuietHours(playerId, PLUGIN_ID, -1, -1)
            )
            .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () ->
                player.sendMessage(ChatColor.GREEN + "✓ Quiet hours disabled")))
            .exceptionally(ex -> {
                logger.error("Failed to disable quiet hours for player " + playerId, ex);
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.sendMessage(ChatColor.RED + "✖ Failed to save preference"));
                return null;
            });
            return true;
        }

        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "✖ Usage: /quest prefs quiet <hour1> <hour2>");
            return true;
        }

        try {
            int hour1 = Integer.parseInt(args[1]);
            int hour2 = Integer.parseInt(args[2]);

            if (hour1 < 0 || hour1 > 23 || hour2 < 0 || hour2 > 23) {
                player.sendMessage(ChatColor.RED + "✖ Hours must be between 0-23");
                return true;
            }

            routeWrite(
                () -> prefsRepo.savePreference(playerId, "quiet_hours_enabled", "true")
                               .thenCompose(v -> prefsRepo.savePreference(playerId, "quiet_hours_start", String.valueOf(hour1)))
                               .thenCompose(v -> prefsRepo.savePreference(playerId, "quiet_hours_end", String.valueOf(hour2)))
                               .thenApply(v -> null),
                () -> prefsLookup.getService().setQuietHours(playerId, PLUGIN_ID, hour1, hour2)
            )
            .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () ->
                player.sendMessage(ChatColor.GREEN + "✓ Quiet hours set to " + hour1 + ":00 - " + hour2 + ":00")))
            .exceptionally(ex -> {
                logger.error("Failed to save quiet hours for player " + playerId, ex);
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.sendMessage(ChatColor.RED + "✖ Failed to save quiet hours"));
                return null;
            });
            return true;
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "✖ Hours must be numbers");
            return true;
        }
    }

    private boolean handleChannel(Player player, UUID playerId, String[] args) {
        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "✖ Usage: /quest prefs channel <type> <channel> <on|off>");
            player.sendMessage(ChatColor.GRAY + "Channels: TITLE, ACTION_BAR, CHAT, SOUND, BOSS_BAR");
            return true;
        }

        String type = args[1].toLowerCase();
        String channel = args[2].toUpperCase();
        String state = args[3].toLowerCase();

        if (!state.equals("on") && !state.equals("off")) {
            player.sendMessage(ChatColor.RED + "✖ State must be 'on' or 'off'");
            return true;
        }

        boolean enabled = state.equals("on");
        String prefKey = type + "_channel_" + channel.toLowerCase();

        routeWrite(
            () -> prefsRepo.savePreference(playerId, prefKey, String.valueOf(enabled)).thenApply(v -> null),
            () -> prefsLookup.getService().setChannelEnabled(playerId, PLUGIN_ID, type, channel, enabled)
        )
        .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            String status = enabled ? "enabled" : "disabled";
            player.sendMessage(ChatColor.GREEN + "✓ Channel " + channel + " " + status + " for " + type);
        }))
        .exceptionally(ex -> {
            logger.error("Failed to save channel preference for player " + playerId + " key " + prefKey, ex);
            plugin.getServer().getScheduler().runTask(plugin, () ->
                player.sendMessage(ChatColor.RED + "✖ Failed to save channel preference"));
            return null;
        });
        return true;
    }

    private void showUsage(Player player) {
        player.sendMessage(ChatColor.RED + "✖ Unknown preference action");
        player.sendMessage(ChatColor.YELLOW + "Usage: /quest prefs [toggle|enable|disable|quiet|channel]");
        player.sendMessage(ChatColor.GRAY + "Use /quest prefs for more information");
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("toggle");
            completions.add("enable");
            completions.add("disable");
            completions.add("quiet");
            completions.add("channel");
        } else if (args.length == 2) {
            if ("enable".equalsIgnoreCase(args[0]) || "disable".equalsIgnoreCase(args[0])) {
                completions.add("quest_start");
                completions.add("quest_complete");
                completions.add("quest_failed");
                completions.add("objective_progress");
                completions.add("objective_complete");
                completions.add("quest_available");
                completions.add("milestone");
                completions.add("chain_progress");
            } else if ("quiet".equalsIgnoreCase(args[0])) {
                completions.add("disable");
                completions.add("0");
                completions.add("22");
            } else if ("channel".equalsIgnoreCase(args[0])) {
                completions.add("quest_start");
                completions.add("quest_complete");
            }
        } else if (args.length == 3) {
            if ("channel".equalsIgnoreCase(args[0])) {
                completions.add("TITLE");
                completions.add("ACTION_BAR");
                completions.add("CHAT");
                completions.add("SOUND");
                completions.add("BOSS_BAR");
            } else if ("quiet".equalsIgnoreCase(args[0])) {
                completions.add("8");
            }
        } else if (args.length == 4) {
            if ("channel".equalsIgnoreCase(args[0])) {
                completions.add("on");
                completions.add("off");
            }
        }

        return completions;
    }

    /**
     * Worked examples for {@code /quest help prefs} (#1981).
     */
    @Override
    public java.util.List<String> getExamples() {
        return java.util.List.of(
                "/quest prefs",
                "  show your current notification preferences",
                "/quest prefs enable quest_complete",
                "/quest prefs disable objective_progress",
                "/quest prefs quiet 22 7",
                "  quiet hours, start and end",
                "/quest prefs quiet disable",
                "Types: quest_start quest_complete quest_failed objective_progress",
                "objective_complete quest_available milestone chain_progress");
    }
}
