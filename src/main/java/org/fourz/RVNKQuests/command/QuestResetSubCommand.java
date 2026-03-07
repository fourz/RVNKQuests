package org.fourz.RVNKQuests.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.service.IQuestService;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin command to reset a player's quest progress to NOT_STARTED state.
 * Clears all progress, objectives, and pending rewards.
 *
 * <p>Usage: /quest reset <quest_id> [player]</p>
 * <ul>
 *   <li>If player specified: Reset quest for that player (admin/console)</li>
 *   <li>If no player: Error (requires player context)</li>
 * </ul>
 *
 * <p>Console Support: MUST work from console with player name argument
 * for automated testing and administration.</p>
 *
 * <p>Use Cases:</p>
 * <ul>
 *   <li>Fix bugged quest states</li>
 *   <li>Reset quest for testing</li>
 *   <li>Player request after abandonment</li>
 *   <li>Quest chain debugging</li>
 * </ul>
 */
public class QuestResetSubCommand extends BaseSubCommand {

    public QuestResetSubCommand(RVNKQuests plugin) {
        super(plugin, "reset", "Reset a player's quest progress (admin)",
              "/quest reset <quest_id> <player>", "rvnkquests.admin.reset", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args, 1)) {
            sendMessage(sender, "&c▶ Usage: &e/quest reset <quest_id> <player>");
            sendMessage(sender, "&7   Resets quest to NOT_STARTED state");
            sendMessage(sender, "&7   Clears ALL progress, objectives, and rewards");
            return true;
        }

        String questId = args[0].toLowerCase();

        // Determine target player - MUST be specified for admin commands
        Player targetPlayer;
        if (args.length >= 2) {
            targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                sendErrorMessage(sender, "Player not found or not online: " + args[1]);
                return true;
            }
        } else if (sender instanceof Player) {
            // Allow player to reset their own quest
            targetPlayer = (Player) sender;
        } else {
            // Console must specify a player
            sendErrorMessage(sender, "Console must specify a player: /quest reset <quest_id> <player>");
            return true;
        }

        // Validate quest exists
        IQuestService questService = plugin.getQuestManager();
        Quest quest = questService.getQuest(questId).orElse(null);
        if (quest == null) {
            sendErrorMessage(sender, "Unknown quest: " + questId);
            sendInfoMessage(sender, "Available quests: " + String.join(", ", plugin.getQuestManager().getQuestIds()));
            return true;
        }

        UUID playerId = targetPlayer.getUniqueId();
        String playerName = targetPlayer.getName();
        String adminName = sender.getName();

        logger.debug("Admin '" + adminName + "' resetting quest '" + questId + "' for player: " + playerName);

        // Get current state for logging
        questService.getPlayerQuestState(playerId, questId)
            .thenCompose(currentState -> {
                // Check if quest is already NOT_STARTED
                if (currentState == QuestState.NOT_STARTED) {
                    throw new IllegalStateException(playerName + " has not started quest '" + questId + "' - nothing to reset");
                }

                // Reset the quest
                logger.info("ADMIN RESET: " + adminName + " resetting quest '" + questId +
                           "' for " + playerName + " (from state: " + currentState + ")");
                return questService.resetQuest(playerId, questId);
            })
            .thenAccept(success -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (success) {
                        sendSuccessMessage(sender, "Reset quest '" + quest.getName() + "' for " + playerName);
                        sendMessage(sender, "&7   Quest state set to NOT_STARTED");
                        sendMessage(sender, "&7   All progress cleared");

                        // Notify target player if different from sender
                        if (!sender.equals(targetPlayer)) {
                            sendInfoMessage(targetPlayer, "Your quest '" + quest.getName() + "' has been reset by an admin");
                        }

                        logger.info("Quest '" + questId + "' reset for player: " + playerName + " (by: " + adminName + ")");
                    } else {
                        sendErrorMessage(sender, "Failed to reset quest '" + questId + "' for " + playerName);
                        logger.warning("Quest reset failed for " + playerName + " on quest " + questId);
                    }
                });
            })
            .exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    sendErrorMessage(sender, message);
                });
                return null;
            });

        return true;
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
