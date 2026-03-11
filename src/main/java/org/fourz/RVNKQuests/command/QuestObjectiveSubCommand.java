package org.fourz.RVNKQuests.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.service.IObjectiveService;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin command for selective objective progress editing.
 * Exposes existing IObjectiveService methods (setProgress, markCompleted, resetProgress)
 * via console-friendly commands.
 *
 * <p>Usage:</p>
 * <ul>
 *   <li>/quest objective <quest_id> <objective_id> set <value> [player]</li>
 *   <li>/quest objective <quest_id> <objective_id> complete [player]</li>
 *   <li>/quest objective <quest_id> <objective_id> reset [player]</li>
 * </ul>
 *
 * <p>Console Support: MUST work from console with player name argument.</p>
 */
public class QuestObjectiveSubCommand extends BaseSubCommand {

    private static final List<String> OPERATIONS = List.of("set", "complete", "reset");

    public QuestObjectiveSubCommand(RVNKQuests plugin) {
        super(plugin, "objective", "Edit objective progress (admin)",
              "/quest objective <quest_id> <objective_id> <set|complete|reset> [value] [player]",
              "rvnkquests.command.objective", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args, 3)) {
            return true;
        }

        String questId = args[0].toLowerCase();
        String objectiveId = args[1].toLowerCase();
        String operation = args[2].toLowerCase();

        // Validate quest exists FIRST
        Quest quest = plugin.getQuestManager().getQuest(questId).orElse(null);
        if (quest == null) {
            sendErrorMessage(sender, "Unknown quest: " + questId);
            sendInfoMessage(sender, "Available quests: " + String.join(", ", plugin.getQuestManager().getQuestIds()));
            return true;
        }

        // Validate operation
        if (!OPERATIONS.contains(operation)) {
            sendErrorMessage(sender, "Unknown operation: " + operation);
            sendInfoMessage(sender, "Valid operations: set, complete, reset");
            return true;
        }

        // Parse value for 'set' operation
        int setValue = 0;
        int playerArgIndex = 3;
        if ("set".equals(operation)) {
            if (args.length < 4) {
                sendErrorMessage(sender, "Usage: /quest objective <quest_id> <objective_id> set <value> [player]");
                return true;
            }
            try {
                setValue = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sendErrorMessage(sender, "Invalid value: " + args[3] + " (must be a number)");
                return true;
            }
            playerArgIndex = 4;
        }

        // Determine target player
        Player targetPlayer;
        if (args.length > playerArgIndex) {
            targetPlayer = Bukkit.getPlayer(args[playerArgIndex]);
            if (targetPlayer == null) {
                sendErrorMessage(sender, "Player not found or not online: " + args[playerArgIndex]);
                return true;
            }
        } else if (sender instanceof Player) {
            targetPlayer = (Player) sender;
        } else {
            sendErrorMessage(sender, "Console must specify a player");
            return true;
        }

        UUID playerId = targetPlayer.getUniqueId();
        String playerName = targetPlayer.getName();
        IObjectiveService objService = plugin.getObjectiveService();

        if (objService == null) {
            sendErrorMessage(sender, "Objective service not available");
            return true;
        }

        logger.debug("Objective " + operation + ": quest=" + questId + " obj=" + objectiveId + " player=" + playerName);

        switch (operation) {
            case "set" -> {
                final int value = setValue;
                objService.setProgress(playerId, questId, objectiveId, value)
                    .thenAccept(progress -> Bukkit.getScheduler().runTask(plugin, () ->
                        sendSuccessMessage(sender, "Set " + objectiveId + " to " + value +
                            "/" + progress.targetCount() + " for " + playerName +
                            " (quest: " + questId + ")")
                    ))
                    .exceptionally(ex -> {
                        Bukkit.getScheduler().runTask(plugin, () -> sendErrorMessage(sender, getExMessage(ex)));
                        return null;
                    });
            }
            case "complete" -> {
                objService.markCompleted(playerId, questId, objectiveId)
                    .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () ->
                        sendSuccessMessage(sender, "Completed objective '" + objectiveId +
                            "' for " + playerName + " (quest: " + questId + ")")
                    ))
                    .exceptionally(ex -> {
                        Bukkit.getScheduler().runTask(plugin, () -> sendErrorMessage(sender, getExMessage(ex)));
                        return null;
                    });
            }
            case "reset" -> {
                objService.resetProgress(playerId, questId, objectiveId)
                    .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () ->
                        sendSuccessMessage(sender, "Reset objective '" + objectiveId +
                            "' for " + playerName + " (quest: " + questId + ")")
                    ))
                    .exceptionally(ex -> {
                        Bukkit.getScheduler().runTask(plugin, () -> sendErrorMessage(sender, getExMessage(ex)));
                        return null;
                    });
            }
        }

        return true;
    }

    private String getExMessage(Throwable ex) {
        return ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Tab complete quest IDs
            String partial = args[0].toLowerCase();
            return plugin.getQuestManager().getQuestIds().stream()
                    .filter(id -> id.startsWith(partial))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            // No way to tab-complete objective IDs without async lookup — return empty
            return List.of();
        } else if (args.length == 3) {
            // Tab complete operations
            String partial = args[2].toLowerCase();
            return OPERATIONS.stream()
                    .filter(op -> op.startsWith(partial))
                    .collect(Collectors.toList());
        } else if (args.length == 4 && "set".equalsIgnoreCase(args[2])) {
            // Suggest common values for 'set'
            return List.of("0", "1", "5", "10");
        } else {
            // Tab complete player names (last arg for all operations)
            int playerIdx = "set".equalsIgnoreCase(args[2]) ? 4 : 3;
            if (args.length == playerIdx + 1) {
                String partial = args[playerIdx].toLowerCase();
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList());
            }
        }
        return super.getTabCompletionOptions(sender, args);
    }
}
