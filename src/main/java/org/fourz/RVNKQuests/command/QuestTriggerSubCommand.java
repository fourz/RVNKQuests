package org.fourz.RVNKQuests.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.trigger.TriggerCommand;
import org.fourz.RVNKQuests.util.Debug;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Subcommand for triggering quests through commands
 * Usage: /quest trigger <quest_id> [location]
 * Where location is: here (default) or around (random within 40 blocks)
 */
public class QuestTriggerSubCommand implements SubCommand {
    private final RVNKQuests plugin;
    private final Debug debug;
    private final TriggerCommand triggerCommand;

    public QuestTriggerSubCommand(RVNKQuests plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "QuestTriggerSubCommand", Level.FINE);
        this.triggerCommand = new TriggerCommand(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /quest trigger <quest_id> [location]");
            player.sendMessage(ChatColor.GRAY + "Where location is: 'here' (default) or 'around' (random within 40 blocks)");
            return true;
        }

        String questId = args[0].toLowerCase();
        String location = args.length > 1 ? args[1].toLowerCase() : "here";

        debug.debug("Player " + player.getName() + " attempting to trigger quest: " + questId + " at " + location);

        // Validate location parameter
        if (!location.equals("here") && !location.equals("around")) {
            player.sendMessage(ChatColor.RED + "Invalid location. Use 'here' or 'around'.");
            return true;
        }

        // Try to trigger the quest
        boolean success = triggerCommand.triggerQuest(player, questId, location);

        if (!success) {
            player.sendMessage(ChatColor.RED + "Could not trigger quest: " + questId);
        }

        return true;
    }

    @Override
    public String getDescription() {
        return "Manually triggers a quest at your location";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnkquests.command.trigger") || sender.isOp();
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return plugin.getQuestManager().getQuestIds().stream()
                    .filter(id -> id.startsWith(partial))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            String partial = args[1].toLowerCase();
            List<String> locations = new ArrayList<>();
            locations.add("here");
            locations.add("around");
            return locations.stream()
                    .filter(loc -> loc.startsWith(partial))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
