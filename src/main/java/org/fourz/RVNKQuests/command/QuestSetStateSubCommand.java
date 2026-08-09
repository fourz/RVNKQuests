package org.fourz.RVNKQuests.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Debug command to set arbitrary quest states for testing.
 * WARNING: Does NOT validate state transitions - may cause inconsistent state.
 *
 * <p>Usage: /quest setstate <quest_id> <state> [player]</p>
 * <ul>
 *   <li>If player specified: Set state for that player (admin/console)</li>
 *   <li>If no player: Error (requires player context)</li>
 * </ul>
 *
 * <p>Valid States: NOT_STARTED, TRIGGER_FOUND, QUEST_ACTIVE, OBJECTIVE_FOUND, COMPLETED, ABANDONED</p>
 *
 * <p>Console Support: MUST work from console with player name argument
 * for automated testing.</p>
 *
 * <p>Use Cases:</p>
 * <ul>
 *   <li>Testing state machine edge cases</li>
 *   <li>Reproducing reported bugs</li>
 *   <li>Testing state-specific UI/commands</li>
 *   <li>Integration testing with quest chains</li>
 * </ul>
 *
 * <p>Safety: This command bypasses normal state transitions and may cause
 * inconsistent data. Use /quest reset for safe state restoration.</p>
 */
public class QuestSetStateSubCommand extends BaseSubCommand {

    public QuestSetStateSubCommand(RVNKQuests plugin) {
        super(plugin, "setstate", "Set quest state directly (debug)",
              "/quest debug setstate <quest_id> <state> <player>", "rvnkquests.admin.debug", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args, 2)) {
            sendMessage(sender, "&c▶ Usage: &e/quest debug setstate <quest_id> <state> <player>");
            sendMessage(sender, "&7   Valid states: NOT_STARTED, TRIGGER_FOUND, QUEST_ACTIVE,");
            sendMessage(sender, "&7                 OBJECTIVE_FOUND, COMPLETED, ABANDONED");
            sendMessage(sender, "&e⚠ Debug command - may cause inconsistent state");
            return true;
        }

        String questId = args[0].toLowerCase();
        String stateArg = args[1].toUpperCase();

        // Parse and validate state
        QuestState targetState;
        try {
            targetState = QuestState.valueOf(stateArg);
        } catch (IllegalArgumentException e) {
            sendErrorMessage(sender, "Invalid state: " + stateArg);
            sendInfoMessage(sender, "Valid states: " +
                    Arrays.stream(QuestState.values())
                            .map(Enum::name)
                            .collect(Collectors.joining(", ")));
            return true;
        }

        // Determine target player - MUST be specified for debug commands
        Player targetPlayer;
        if (args.length >= 3) {
            targetPlayer = Bukkit.getPlayer(args[2]);
            if (targetPlayer == null) {
                sendErrorMessage(sender, "Player not found or not online: " + args[2]);
                return true;
            }
        } else if (sender instanceof Player) {
            targetPlayer = (Player) sender;
        } else {
            // Console must specify a player
            sendErrorMessage(sender, "Console must specify a player: /quest debug setstate <quest_id> <state> <player>");
            return true;
        }

        // Validate quest exists
        Quest quest = plugin.getQuestManager().getQuest(questId).orElse(null);
        if (quest == null) {
            sendErrorMessage(sender, "Unknown quest: " + questId);
            sendInfoMessage(sender, "Available quests: " + String.join(", ", plugin.getQuestManager().getQuestIds()));
            return true;
        }

        UUID playerId = targetPlayer.getUniqueId();
        String playerName = targetPlayer.getName();
        String adminName = sender.getName();

        logger.debug("Admin '" + adminName + "' setting quest '" + questId + "' state to " +
                    targetState + " for player: " + playerName);

        // Display warning about debug command
        sendMessage(sender, "&e⚠ Debug command - bypassing normal state transitions");

        // Read previous state from DB (avoids stateCache race after reset+trigger sequences),
        // then advance — all chained so the previous state is accurate in the success message.
        quest.getStateForPlayer(playerId)
            .thenCompose(previousState -> {
                logger.info("ADMIN DEBUG STATE CHANGE: " + adminName + " setting quest '" + questId +
                           "' for " + playerName + " from " + previousState + " to " + targetState);
                return quest.setStateForPlayer(playerId, targetState)
                    .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                        sendSuccessMessage(sender, "Set quest '" + quest.getName() + "' state for " + playerName);
                        sendMessage(sender, "&7   Previous state: &e" + previousState);
                        sendMessage(sender, "&7   New state: &a" + targetState);
                        // #1884: this used to claim "no rewards triggered" unconditionally, which is
                        // false for COMPLETED. AbstractQuest.performAdvance fires every completion
                        // side-effect — onComplete() rewards, notifications, broadcast,
                        // QuestCompleteEvent — "regardless of how COMPLETED is reached (trigger
                        // component, admin command, or direct complete() call)". That is deliberate,
                        // so trigger-driven completions still pay out; the message was simply lying.
                        //
                        // It is not cosmetic. On Event this handed a player a SECOND Ravenforge Shard
                        // and fired a duplicate server-wide [Chapter I] broadcast for a quest he had
                        // already completed two minutes earlier — item duplication and a false public
                        // announcement, from a command an operator reasonably believed was inert.
                        if (targetState == QuestState.COMPLETED) {
                            sendMessage(sender, "&e   ⚠ COMPLETED fires rewards, notifications and the");
                            sendMessage(sender, "&e     completion broadcast - same as finishing it normally.");
                        } else {
                            sendMessage(sender, "&7   &oJournal entry recorded (no rewards triggered)");
                        }

                        if (!sender.equals(targetPlayer)) {
                            sendInfoMessage(targetPlayer, "Quest '" + quest.getName() + "' state changed to " + targetState + " (debug)");
                        }

                        logger.info("Quest '" + questId + "' state set to " + targetState +
                                   " for player: " + playerName + " (by: " + adminName + ")");
                    }));
            })
            .exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    sendErrorMessage(sender, "Failed to set state: " + message);
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
            // Tab complete quest states
            String partial = args[1].toUpperCase();
            return Arrays.stream(QuestState.values())
                    .map(Enum::name)
                    .filter(name -> name.startsWith(partial))
                    .collect(Collectors.toList());
        } else if (args.length == 3) {
            // Tab complete player names
            String partial = args[2].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return super.getTabCompletionOptions(sender, args);
    }
}
