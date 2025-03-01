package org.fourz.RVNKQuests.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.util.Debug;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Subcommand for changing quest states
 * Usage: /quest state <quest_id> <state>
 */
public class QuestStateSubCommand implements SubCommand {
    private final RVNKQuests plugin;
    private final Debug debug;

    public QuestStateSubCommand(RVNKQuests plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "QuestStateCommand", Level.FINE);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /quest state <quest_id> <state>");
            return true;
        }

        String questId = args[0].toLowerCase();
        String stateStr = args[1].toUpperCase();
        
        debug.debug("Attempting to change quest state: " + questId + " to " + stateStr);

        Quest quest = plugin.getQuestManager().getQuest(questId);
        if (quest == null) {
            sender.sendMessage(ChatColor.RED + "Unknown quest: " + questId);
            return true;
        }

        QuestState currentState = quest.getCurrentState();
        QuestState newState;
        
        try {
            newState = QuestState.valueOf(stateStr);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Invalid quest state: " + stateStr);
            sender.sendMessage(ChatColor.RED + "Valid states: " + 
                    Arrays.toString(QuestState.values()));
            return true;
        }

        debug.debug("Changing quest " + questId + " state from " + currentState + " to " + newState);
        quest.advanceState(newState);
        sender.sendMessage(ChatColor.GREEN + "Changed quest state for " + questId + " from " + 
                currentState + " to " + newState);
        
        return true;
    }

    @Override
    public String getDescription() {
        return "Changes the state of a quest";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnkquests.command.state") || sender.isOp();
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
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
        return new ArrayList<>();
    }
}
