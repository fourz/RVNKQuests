package org.fourz.RVNKQuests.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.ui.QuestMenuManager;
import org.fourz.RVNKQuests.ui.QuestMenuManager.QuestFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Subcommand to open the quest GUI menu.
 * Usage: /quest menu [filter]
 *
 * <p>Opens a GUI-based quest browsing interface with:</p>
 * <ul>
 *   <li>Color-coded quest items by state</li>
 *   <li>Filter buttons (all, active, available, completed)</li>
 *   <li>Quest detail views with objectives</li>
 *   <li>Accept/abandon/claim actions</li>
 * </ul>
 *
 * <p>Console Support: Requires player context, console must specify target player.</p>
 */
public class QuestMenuSubCommand extends BaseSubCommand {

    public QuestMenuSubCommand(RVNKQuests plugin, RVNKCommand parent) {
        super(plugin, parent, "menu", "Open quest menu GUI",
              "/quest menu [filter] [player]", "rvnkquests.menu", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        // Determine target player
        Player targetPlayer;
        QuestFilter filter = QuestFilter.ALL;

        if (args.length >= 2) {
            // Console or player specifying another player
            targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                sendErrorMessage(sender, "Player not found: " + args[1]);
                return true;
            }

            // Check permission for opening menu for others
            if (!sender.hasPermission("rvnkquests.menu.other")) {
                sendErrorMessage(sender, "You don't have permission to open menu for other players");
                return true;
            }

            // Parse filter if provided
            if (args.length >= 1) {
                filter = parseFilter(args[0]);
            }

        } else if (args.length >= 1) {
            // Filter specified, use sender as player
            if (!(sender instanceof Player)) {
                sendErrorMessage(sender, "Console must specify a player: /quest menu <filter> <player>");
                return true;
            }

            targetPlayer = (Player) sender;
            filter = parseFilter(args[0]);

        } else {
            // No arguments, use sender
            if (!(sender instanceof Player)) {
                sendErrorMessage(sender, "Console must specify a player: /quest menu <filter> <player>");
                return true;
            }

            targetPlayer = (Player) sender;
        }

        // Open menu
        logger.debug("Opening quest menu for " + targetPlayer.getName() +
                   " (filter: " + filter + ", requested by: " + sender.getName() + ")");

        QuestMenuManager menu = new QuestMenuManager(plugin, targetPlayer, filter);
        menu.openMenu();

        // Notify sender if different from target
        if (sender != targetPlayer) {
            sendSuccessMessage(sender, "Opened quest menu for " + targetPlayer.getName());
        }

        return true;
    }

    /**
     * Parses filter argument to QuestFilter enum.
     *
     * @param filterArg The filter argument
     * @return The corresponding QuestFilter
     */
    private QuestFilter parseFilter(String filterArg) {
        return switch (filterArg.toLowerCase()) {
            case "active", "a" -> QuestFilter.ACTIVE;
            case "available", "av" -> QuestFilter.AVAILABLE;
            case "completed", "c" -> QuestFilter.COMPLETED;
            default -> QuestFilter.ALL;
        };
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Tab complete filter options
            completions.add("all");
            completions.add("active");
            completions.add("available");
            completions.add("completed");

            // Filter based on partial input
            String partial = args[0].toLowerCase();
            completions.removeIf(option -> !option.startsWith(partial));

        } else if (args.length == 2 && sender.hasPermission("rvnkquests.menu.other")) {
            // Tab complete player names
            String partial = args[1].toLowerCase();
            Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(partial))
                .forEach(completions::add);
        }

        return completions;
    }
}
