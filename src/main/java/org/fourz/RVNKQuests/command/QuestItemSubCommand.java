package org.fourz.RVNKQuests.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.reward.QuestItem;
import org.fourz.RVNKQuests.util.Debug;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Subcommand for giving quest items to players
 * Usage: /quest item <item_name>
 */
public class QuestItemSubCommand implements SubCommand {
    private final RVNKQuests plugin;
    private final Debug debug;
    private static final List<String> QUEST_ITEM_IDS = Arrays.asList(
            "grotsnout_journal", "grotsnouts_last_stand"
            // Add more item IDs here as they are created
    );

    public QuestItemSubCommand(RVNKQuests plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "QuestItemCommand", Level.FINE);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /quest item <item_name>");
            return true;
        }

        String itemName = args[0].toLowerCase();
        debug.debug("Player " + player.getName() + " requested quest item: " + itemName);

        ItemStack item = QuestItem.getQuestItem(itemName);
        if (item == null) {
            player.sendMessage(ChatColor.RED + "Unknown quest item: " + itemName);
            return true;
        }

        debug.debug("Giving item " + itemName + " to player " + player.getName());
        player.getInventory().addItem(item);
        player.sendMessage(ChatColor.GREEN + "You received the quest item: " + itemName);
        return true;
    }

    @Override
    public String getDescription() {
        return "Gives you a quest item by name";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnkquests.command.item") || sender.isOp();
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return QUEST_ITEM_IDS.stream()
                    .filter(id -> id.startsWith(partial))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
