package org.fourz.RVNKQuests.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.service.IQuestProgressService;
import org.fourz.RVNKQuests.service.IQuestService;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Subcommand for abandoning an active quest.
 * Supports console execution with player name argument for admin overrides.
 *
 * <p>Usage: /quest abandon <quest_id> [player]</p>
 * <ul>
 *   <li>If player specified: Abandon quest for that player (admin/console)</li>
 *   <li>If no player: Abandon quest for sender (player-only context)</li>
 * </ul>
 *
 * <p>Validation:</p>
 * <ul>
 *   <li>Quest must be active for the player</li>
 *   <li>Cannot abandon if quest is locked/required (future enhancement)</li>
 *   <li>Clears all progress for that quest</li>
 *   <li>Updates quest state to ABANDONED</li>
 * </ul>
 *
 * <p>Console Support: MUST work from console with player name argument
 * for automated testing and remote administration.</p>
 */
public class QuestAbandonSubCommand extends BaseSubCommand {

    public QuestAbandonSubCommand(RVNKQuests plugin) {
        super(plugin, "abandon", "Abandon an active quest",
              "/quest abandon <quest_id> [player]", "rvnkquests.command.abandon", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args, 1)) {
            return true;
        }

        String questId = args[0].toLowerCase();

        // Validate quest exists FIRST — so invalid IDs are reported even from console
        IQuestService questService = plugin.getQuestManager();
        Quest quest = questService.getQuest(questId).orElse(null);
        if (quest == null) {
            sendErrorMessage(sender, "Unknown quest: " + questId);
            List<String> questIds = questService.getAllQuests().stream()
                .map(Quest::getId)
                .collect(Collectors.toList());
            sendInfoMessage(sender, "Available quests: " + String.join(", ", questIds));
            return true;
        }

        // Determine target player
        Player targetPlayer;
        if (args.length >= 2) {
            // Player specified as argument (admin/console mode)
            targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                sendErrorMessage(sender, "Player not found or not online: " + args[1]);
                return true;
            }
        } else if (sender instanceof Player) {
            // Use sender if they're a player
            targetPlayer = (Player) sender;
        } else {
            // Console must specify a player
            sendErrorMessage(sender, "Console must specify a player: /quest abandon <quest_id> <player>");
            return true;
        }

        UUID playerId = targetPlayer.getUniqueId();
        String playerName = targetPlayer.getName();

        logger.debug("Attempting to abandon quest '" + questId + "' for player: " + playerName);

        IQuestProgressService progressService = plugin.getQuestProgressService();

        // Check if quest is active for the player
        progressService.getQuestState(playerId, questId)
            .thenCompose(currentState -> {
                // Validate quest is active
                if (!isQuestActive(currentState)) {
                    throw new IllegalStateException(
                        "Cannot abandon quest '" + questId + "' - quest is not active (current state: " + currentState + ")"
                    );
                }

                // Update quest state to ABANDONED
                return questService.abandonQuest(playerId, questId)
                    .thenCompose(success -> {
                        if (!success) {
                            throw new IllegalStateException("Failed to abandon quest - service returned false");
                        }

                        // Clear all objective progress
                        return progressService.getAllObjectives(playerId, questId)
                            .thenCompose(objectives -> {
                                // Reset quest progress completely
                                return progressService.resetQuestProgress(playerId, questId);
                            })
                            .thenApply(resetSuccess -> {
                                if (!resetSuccess) {
                                    logger.warning("Failed to clear objective progress for abandoned quest: " + questId);
                                }
                                return success;
                            });
                    });
            })
            .thenAccept(success -> {
                // Run on main thread for message sending
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sendSuccessMessage(sender, "Abandoned quest '" + quest.getName() + "' for " + playerName);

                    // Notify target player if different from sender
                    if (!sender.equals(targetPlayer)) {
                        sendInfoMessage(targetPlayer, "Quest abandoned: " + quest.getName());
                    }

                    logger.debug("Quest '" + questId + "' abandoned for player: " + playerName +
                               " (by: " + sender.getName() + ")");
                });
            })
            .exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    sendErrorMessage(sender, message);
                    logger.debug("Failed to abandon quest '" + questId + "' for player: " + playerName + " - " + message);
                });
                return null;
            });

        return true;
    }

    /**
     * Determines if a quest state represents an active quest that can be abandoned.
     *
     * @param state The current quest state
     * @return true if the quest is in an active state
     */
    private boolean isQuestActive(QuestState state) {
        return state == QuestState.QUEST_ACTIVE ||
               state == QuestState.TRIGGER_FOUND ||
               state == QuestState.OBJECTIVE_FOUND;
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Tab complete quest IDs (show all quests, user may be trying any ID)
            String partial = args[0].toLowerCase();
            IQuestService questService = plugin.getQuestManager();
            return questService.getAllQuests().stream()
                    .map(Quest::getId)
                    .filter(id -> id.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            // Tab complete player names
            String partial = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return super.getTabCompletionOptions(sender, args);
    }
}
