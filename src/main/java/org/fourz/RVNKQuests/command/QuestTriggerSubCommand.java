package org.fourz.RVNKQuests.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.trigger.TriggerCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Subcommand for triggering quests through commands.
 * Extends BaseSubCommand to provide standardized subcommand functionality.
 * Usage: /quest trigger <quest_id> [location]
 * Where location is: here (default) or around (random within 40 blocks)
 */
public class QuestTriggerSubCommand extends BaseSubCommand {
    private final TriggerCommand triggerCommand;

    public QuestTriggerSubCommand(RVNKQuests plugin) {
        super(plugin, "trigger", "Manually triggers a quest at your location", 
              "/quest trigger <quest_id> [location]", "rvnkquests.command.trigger", true);
        this.triggerCommand = new TriggerCommand(plugin);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        Player player = (Player) sender; // Safe cast due to playerOnly = true

        if (!validateArgs(sender, args, 1)) {
            sendInfoMessage(sender, "Where location is: 'here' (default) or 'around' (random within 40 blocks)");
            return true;
        }

        String questId = args[0].toLowerCase();
        String location = args.length > 1 ? args[1].toLowerCase() : "here";

        logger.debug("Player " + player.getName() + " attempting to trigger quest: " + questId + " location: " + location);

        // Validate location parameter
        if (!location.equals("here") && !location.equals("around")) {
            sendErrorMessage(sender, "Invalid location. Use 'here' or 'around'.");
            return true;
        }

        // Try to trigger the quest
        boolean success = triggerCommand.triggerQuest(player, questId, location);

        if (!success) {
            sendErrorMessage(sender, "Could not trigger quest: " + questId);
        }

        return true;
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
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
        return super.getTabCompletionOptions(sender, args);
    }
}
