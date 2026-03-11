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
 * Admin command to force-complete a quest for a player.
 * Bypasses all objective requirements and grants rewards immediately.
 *
 * <p>Usage: /quest complete <quest_id> [player]</p>
 * <ul>
 *   <li>If player specified: Complete quest for that player (admin/console)</li>
 *   <li>If no player: Error (requires player context)</li>
 * </ul>
 *
 * <p>Console Support: MUST work from console with player name argument
 * for automated testing and CI/CD.</p>
 *
 * <p>Use Cases:</p>
 * <ul>
 *   <li>Testing quest chains (skip prerequisites)</li>
 *   <li>Debugging reward systems</li>
 *   <li>Compensating players for bugs</li>
 *   <li>CI/CD automated testing</li>
 * </ul>
 */
public class QuestCompleteSubCommand extends BaseSubCommand {

    public QuestCompleteSubCommand(RVNKQuests plugin) {
        super(plugin, "complete", "Force complete a quest (admin)",
              "/quest complete <quest_id> <player>", "rvnkquests.admin.complete", false);
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
            sendInfoMessage(sender, "Available quests: " + String.join(", ", plugin.getQuestManager().getQuestIds()));
            return true;
        }

        // Determine target player - MUST be specified for admin commands
        Player targetPlayer;
        if (args.length >= 2) {
            targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                sendErrorMessage(sender, "Player not found or not online: " + args[1]);
                return true;
            }
        } else if (sender instanceof Player) {
            // Allow player to complete their own quest (with permission)
            targetPlayer = (Player) sender;
        } else {
            // Console must specify a player
            sendErrorMessage(sender, "Console must specify a player: /quest complete <quest_id> <player>");
            return true;
        }

        UUID playerId = targetPlayer.getUniqueId();
        String playerName = targetPlayer.getName();
        String adminName = sender.getName();

        logger.debug("Admin '" + adminName + "' force-completing quest '" + questId + "' for player: " + playerName);

        // Get current state for validation
        questService.getPlayerQuestState(playerId, questId)
            .thenCompose(currentState -> {
                // Check if quest is already completed
                if (currentState == QuestState.COMPLETED) {
                    throw new IllegalStateException(playerName + " has already completed quest '" + questId + "'");
                }

                // Force complete the quest
                logger.info("ADMIN FORCE COMPLETE: " + adminName + " completing quest '" + questId +
                           "' for " + playerName + " (from state: " + currentState + ")");
                return questService.completeQuest(playerId, questId);
            })
            .thenAccept(success -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (success) {
                        sendSuccessMessage(sender, "Force completed quest '" + quest.getName() + "' for " + playerName);
                        sendMessage(sender, "&7   All objectives bypassed");
                        sendMessage(sender, "&7   Rewards granted");

                        // Notify target player if different from sender
                        if (!sender.equals(targetPlayer)) {
                            sendSuccessMessage(targetPlayer, "Quest '" + quest.getName() + "' completed by admin!");
                        }

                        logger.info("Quest '" + questId + "' force-completed for player: " + playerName + " (by: " + adminName + ")");
                    } else {
                        sendErrorMessage(sender, "Failed to complete quest '" + questId + "' for " + playerName);
                        logger.warning("Quest force-complete failed for " + playerName + " on quest " + questId);
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
