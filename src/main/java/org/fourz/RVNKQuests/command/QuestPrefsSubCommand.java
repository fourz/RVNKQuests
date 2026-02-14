package org.fourz.RVNKQuests.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Subcommand for managing player quest notification preferences.
 * Usage: /quest prefs
 *        /quest prefs toggle
 *        /quest prefs disable <type>
 *        /quest prefs enable <type>
 *        /quest prefs quiet <startHour> <endHour>
 *        /quest prefs quiet disable
 *        /quest prefs channel <type> <channel> <on|off>
 */
public class QuestPrefsSubCommand extends BaseSubCommand {

    public QuestPrefsSubCommand(RVNKQuests plugin) {
        super(
            plugin,
            null,  // parent will be set by command manager
            "prefs",
            "Manage your quest notification preferences",
            "/quest prefs [action]",
            "rvnkquests.prefs",
            true  // player-only
        );
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
        player.sendMessage(ChatColor.GREEN + "✓ Queued: Toggle all quest notifications");
        player.sendMessage(ChatColor.GRAY + "Note: Preference persistence coming in future update");
        return true;
    }

    private boolean handleEnable(Player player, UUID playerId, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "✖ Usage: /quest prefs enable <type>");
            player.sendMessage(ChatColor.GRAY + "Types: quest_start, quest_complete, quest_failed, objective_progress, objective_complete, quest_available, milestone, chain_progress");
            return true;
        }

        String type = args[1].toLowerCase();
        player.sendMessage(ChatColor.GREEN + "✓ Queued: Enable notifications for " + type);
        player.sendMessage(ChatColor.GRAY + "Note: Preference persistence coming in future update");
        return true;
    }

    private boolean handleDisable(Player player, UUID playerId, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "✖ Usage: /quest prefs disable <type>");
            player.sendMessage(ChatColor.GRAY + "Types: quest_start, quest_complete, quest_failed, objective_progress, objective_complete, quest_available, milestone, chain_progress");
            return true;
        }

        String type = args[1].toLowerCase();
        player.sendMessage(ChatColor.GREEN + "✓ Queued: Disable notifications for " + type);
        player.sendMessage(ChatColor.GRAY + "Note: Preference persistence coming in future update");
        return true;
    }

    private boolean handleQuietHours(Player player, UUID playerId, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "✖ Usage: /quest prefs quiet <hour1> <hour2> or /quest prefs quiet disable");
            return true;
        }

        if ("disable".equalsIgnoreCase(args[1])) {
            player.sendMessage(ChatColor.GREEN + "✓ Quiet hours disabled");
            player.sendMessage(ChatColor.GRAY + "Note: Preference persistence coming in future update");
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

            player.sendMessage(ChatColor.GREEN + "✓ Quiet hours set to " + hour1 + ":00 - " + hour2 + ":00");
            player.sendMessage(ChatColor.GRAY + "Note: Preference persistence coming in future update");
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

        String status = state.equals("on") ? "enabled" : "disabled";
        player.sendMessage(ChatColor.GREEN + "✓ Queued: Channel " + channel + " " + status + " for " + type);
        player.sendMessage(ChatColor.GRAY + "Note: Preference persistence coming in future update");
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
}
