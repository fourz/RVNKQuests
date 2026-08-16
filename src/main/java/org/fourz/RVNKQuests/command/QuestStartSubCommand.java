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
 * Subcommand for starting a quest for a player.
 * Supports console execution with player name argument for automated testing.
 * 
 * <p>Usage: /quest start <quest_id> [player]</p>
 * <ul>
 *   <li>If player specified: Start quest for that player (admin/console)</li>
 *   <li>If no player: Start quest for sender (player-only context)</li>
 * </ul>
 * 
 * <p>Console Support: MUST work from console with player name argument
 * for automated testing and remote administration.</p>
 */
public class QuestStartSubCommand extends BaseSubCommand {

    public QuestStartSubCommand(RVNKQuests plugin) {
        super(plugin, "start", "Start a quest for a player",
              "/quest start <quest_id> [player]", "rvnkquests.start", false);
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
            sendErrorMessage(sender, "Console must specify a player: /quest start <quest_id> <player>");
            return true;
        }

        UUID playerId = targetPlayer.getUniqueId();
        String playerName = targetPlayer.getName();

        logger.debug("Attempting to start quest '" + questId + "' for player: " + playerName);

        // Check if player can start the quest
        questService.canStartQuest(playerId, questId)
            .thenCompose(canStart -> {
                if (!canStart) {
                    // Get current state to provide informative error
                    return questService.getPlayerQuestState(playerId, questId)
                        .thenApply(state -> {
                            throw new IllegalStateException(getCannotStartReason(state, questId, playerName));
                        });
                }
                
                // Start the quest
                return questService.startQuest(playerId, questId)
                    .thenApply(success -> {
                        if (!success) {
                            throw new IllegalStateException("Quest start failed for unknown reason");
                        }
                        return success;
                    });
            })
            .thenAccept(success -> {
                // Run on main thread for message sending
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sendSuccessMessage(sender, "Started quest '" + quest.getName() + "' for " + playerName);
                    
                    // Notify target player if different from sender
                    if (!sender.equals(targetPlayer)) {
                        sendSuccessMessage(targetPlayer, "Quest started: " + quest.getName());
                    }
                    
                    logger.debug("Quest '" + questId + "' started for player: " + playerName + " (by: " + sender.getName() + ")");
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

    /**
     * Generates a human-readable reason why a quest cannot be started.
     */
    private String getCannotStartReason(QuestState state, String questId, String playerName) {
        return switch (state) {
            case QUEST_ACTIVE, TRIGGER_FOUND, OBJECTIVE_FOUND -> 
                playerName + " already has quest '" + questId + "' in progress (state: " + state + ")";
            case COMPLETED -> 
                playerName + " has already completed quest '" + questId + "'";
            default -> 
                playerName + " cannot start quest '" + questId + "' (current state: " + state + ")";
        };
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

    /**
     * Worked examples for {@code /quest help start} (#1981).
     */
    @Override
    public java.util.List<String> getExamples() {
        return java.util.List.of(
                "/quest start tfah_ch1_journey",
                "  as a player, starts it for yourself",
                "/quest start tfah_ch1_journey Shad0melt",
                "  console MUST name the player - it has no self to start for",
                "Starting does not skip the trigger. A quest whose trigger has not fired",
                "still needs its TRIGGER_FOUND step, so check /quest state after.");
    }
}
