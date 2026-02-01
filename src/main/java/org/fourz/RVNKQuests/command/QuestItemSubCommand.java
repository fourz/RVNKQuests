package org.fourz.RVNKQuests.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.reward.QuestItem;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Subcommand for giving quest items to players.
 * Extends BaseSubCommand to provide standardized subcommand functionality.
 * Usage: /quest item <item_name> [player]
 *
 * <p>Supports console execution by specifying target player as second argument.</p>
 */
public class QuestItemSubCommand extends BaseSubCommand {
    private static final List<String> QUEST_ITEM_IDS = Arrays.asList(
            "grotsnout_journal", "grotsnouts_last_stand"
            // Add more item IDs here as they are created
    );

    public QuestItemSubCommand(RVNKQuests plugin) {
        super(plugin, "item", "Gives a quest item to a player",
              "/quest item <item_name> [player]", "rvnkquests.command.item", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args, 1)) {
            return true;
        }

        String itemName = args[0].toLowerCase();

        // Determine target player
        Player targetPlayer;
        if (args.length >= 2) {
            // Player specified as argument
            targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                sendErrorMessage(sender, "Player not found: " + args[1]);
                return true;
            }
        } else if (sender instanceof Player) {
            // Use sender if they're a player
            targetPlayer = (Player) sender;
        } else {
            // Console must specify a player
            sendErrorMessage(sender, "Console must specify a player: /quest item <item_name> <player>");
            return true;
        }

        logger.debug("Requested quest item: " + itemName + " for player: " + targetPlayer.getName());

        ItemStack item = QuestItem.getQuestItem(itemName);
        if (item == null) {
            sendErrorMessage(sender, "Unknown quest item: " + itemName);
            return true;
        }

        logger.debug("Giving item " + itemName + " to player " + targetPlayer.getName());
        targetPlayer.getInventory().addItem(item);

        if (sender == targetPlayer) {
            sendSuccessMessage(sender, "You received the quest item: " + itemName);
        } else {
            sendSuccessMessage(sender, "Gave quest item '" + itemName + "' to " + targetPlayer.getName());
        }
        return true;
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return QUEST_ITEM_IDS.stream()
                    .filter(id -> id.startsWith(partial))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            String partial = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return super.getTabCompletionOptions(sender, args);
    }
}
