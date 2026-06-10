package org.fourz.RVNKQuests.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Subcommand for changing quest states.
 * Extends BaseSubCommand to provide standardized subcommand functionality.
 * Usage: /quest state <quest_id> <state> [player]
 */
public class QuestStateSubCommand extends BaseSubCommand {

    public QuestStateSubCommand(RVNKQuests plugin) {
        super(plugin, "state", "Changes the state of a quest",
              "/quest state <quest_id> <state> [player]", "rvnkquests.state", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args, 2)) {
            return true;
        }

        String questId = args[0].toLowerCase();
        String stateStr = args[1].toUpperCase();

        // Validate quest exists FIRST — so invalid IDs are reported even from console
        Quest quest = plugin.getQuestManager().getQuest(questId).orElse(null);
        if (quest == null) {
            sendErrorMessage(sender, "Unknown quest: " + questId);
            return true;
        }

        // Determine target player
        Player targetPlayer;
        if (args.length >= 3) {
            // Player specified as argument
            targetPlayer = Bukkit.getPlayer(args[2]);
            if (targetPlayer == null) {
                sendErrorMessage(sender, "Player not found: " + args[2]);
                return true;
            }
        } else if (sender instanceof Player) {
            // Use sender if they're a player
            targetPlayer = (Player) sender;
        } else {
            // Console must specify a player
            sendErrorMessage(sender, "Console must specify a player: /quest state <quest_id> <state> <player>");
            return true;
        }

        logger.debug("Attempting to change quest state: " + stateStr + " for " + questId + " (player: " + targetPlayer.getName() + ")");

        QuestState currentState = quest.getStateForPlayer(targetPlayer);
        QuestState newState;

        try {
            newState = QuestState.valueOf(stateStr);
        } catch (IllegalArgumentException e) {
            sendErrorMessage(sender, "Invalid quest state: " + stateStr);
            sendErrorMessage(sender, "Valid states: " + Arrays.toString(QuestState.values()));
            return true;
        }

        logger.debug("Changing quest " + questId + " state from " + currentState + " to " + newState + " for " + targetPlayer.getName());

        // Use per-player state advancement
        quest.advanceStateForPlayer(targetPlayer.getUniqueId(), newState)
            .thenRun(() -> {
                // Run on main thread for message sending
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sendSuccessMessage(sender, "Changed " + targetPlayer.getName() + "'s quest state for " + questId +
                            " from " + currentState + " to " + newState);
                });
            })
            .exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sendErrorMessage(sender, "Failed to update quest state: " + ex.getMessage());
                });
                return null;
            });

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
        } else if (args.length == 3) {
            String partial = args[2].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return super.getTabCompletionOptions(sender, args);
    }
}
