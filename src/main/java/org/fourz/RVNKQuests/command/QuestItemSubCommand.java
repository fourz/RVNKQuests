package org.fourz.RVNKQuests.command;

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
 * Usage: /quest item <item_name>
 */
public class QuestItemSubCommand extends BaseSubCommand {
    private static final List<String> QUEST_ITEM_IDS = Arrays.asList(
            "grotsnout_journal", "grotsnouts_last_stand"
            // Add more item IDs here as they are created
    );

    public QuestItemSubCommand(RVNKQuests plugin) {
        super(plugin, "item", "Gives you a quest item by name", 
              "/quest item <item_name>", "rvnkquests.command.item", true);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        Player player = (Player) sender; // Safe cast due to playerOnly = true

        if (!validateArgs(sender, args, 1)) {
            return true;
        }

        String itemName = args[0].toLowerCase();
        logger.debug("Player " + player.getName() + " requested quest item: " + itemName);

        ItemStack item = QuestItem.getQuestItem(itemName);
        if (item == null) {
            sendErrorMessage(sender, "Unknown quest item: " + itemName);
            return true;
        }

        logger.debug("Giving item " + itemName + " to player " + player.getName());
        player.getInventory().addItem(item);
        sendSuccessMessage(sender, "You received the quest item: " + itemName);
        return true;
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return QUEST_ITEM_IDS.stream()
                    .filter(id -> id.startsWith(partial))
                    .collect(Collectors.toList());
        }
        return super.getTabCompletionOptions(sender, args);
    }
}
