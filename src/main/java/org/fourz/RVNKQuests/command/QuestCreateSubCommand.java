package org.fourz.RVNKQuests.command;

import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.IQuestRepository;
import org.fourz.RVNKQuests.data.dto.QuestDTO;

import java.util.Collections;
import java.util.List;

/**
 * /quest create <id> <name> — create an empty quest definition.
 */
public class QuestCreateSubCommand extends BaseSubCommand {

    public QuestCreateSubCommand(RVNKQuests plugin) {
        super(plugin, "create", "Create a new quest definition",
              "/quest create <id> <name...>", "rvnkquests.admin.create", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args, 2)) return true;

        String questId = args[0].toLowerCase();
        String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

        IQuestRepository repo = plugin.getQuestRepository();
        if (repo == null) {
            sendErrorMessage(sender, "Quest repository not available.");
            return true;
        }

        repo.exists(questId).thenAccept(exists -> {
            if (exists) {
                sendErrorMessage(sender, "Quest '" + questId + "' already exists.");
                return;
            }

            QuestDTO quest = QuestDTO.create(questId, name, "");
            repo.save(quest).thenAccept(success -> {
                if (success) {
                    sendSuccessMessage(sender, "Created quest: " + questId + " (" + name + ")");
                    logger.info("Quest created: " + questId + " by " + sender.getName());
                } else {
                    sendErrorMessage(sender, "Failed to save quest definition.");
                }
            });
        });

        return true;
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("<quest_id>");
        }
        if (args.length == 2) {
            return List.of("<display_name>");
        }
        return Collections.emptyList();
    }
}
