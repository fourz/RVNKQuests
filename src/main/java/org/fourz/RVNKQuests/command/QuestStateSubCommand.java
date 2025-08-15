package org.fourz.RVNKQuests.command;

import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Subcommand for changing quest states.
 * Extends BaseSubCommand to provide standardized subcommand functionality.
 * Usage: /quest state <quest_id> <state>
 */
public class QuestStateSubCommand extends BaseSubCommand {

    public QuestStateSubCommand(RVNKQuests plugin) {
        super(plugin, "state", "Changes the state of a quest", 
              "/quest state <quest_id> <state>", "rvnkquests.command.state", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args, 2)) {
            return true;
        }

        String questId = args[0].toLowerCase();
        String stateStr = args[1].toUpperCase();
        
        logger.debug("Attempting to change quest state: " + stateStr + " for " + questId);

        Quest quest = plugin.getQuestManager().getQuest(questId);
        if (quest == null) {
            sendErrorMessage(sender, "Unknown quest: " + questId);
            return true;
        }

        QuestState currentState = quest.getCurrentState();
        QuestState newState;
        
        try {
            newState = QuestState.valueOf(stateStr);
        } catch (IllegalArgumentException e) {
            sendErrorMessage(sender, "Invalid quest state: " + stateStr);
            sendErrorMessage(sender, "Valid states: " + Arrays.toString(QuestState.values()));
            return true;
        }

        logger.debug("Changing quest " + questId + " state from " + currentState + " to " + newState);
        quest.advanceState(newState);
        sendSuccessMessage(sender, "Changed quest state for " + questId + " from " + 
                currentState + " to " + newState);
        
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
            String partial = args[1].toUpperCase();
            return Arrays.stream(QuestState.values())
                    .map(QuestState::name)
                    .filter(state -> state.startsWith(partial))
                    .collect(Collectors.toList());
        }
        return super.getTabCompletionOptions(sender, args);
    }
}
