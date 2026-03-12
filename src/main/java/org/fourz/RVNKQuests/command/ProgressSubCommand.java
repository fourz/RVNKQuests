package org.fourz.RVNKQuests.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.dto.QuestObjectiveProgressDTO;
import org.fourz.RVNKQuests.data.dto.QuestProgressDTO;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.service.IQuestProgressService;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Subcommand for checking quest progress.
 * Extends BaseSubCommand to provide standardized subcommand functionality.
 * Usage: /quest progress <quest_id> [player]
 *
 * <p>Supports console execution by specifying target player as second argument.</p>
 * <p>Displays quest progress including objectives, completion percentage, and next steps.</p>
 */
public class ProgressSubCommand extends BaseSubCommand {

    public ProgressSubCommand(RVNKQuests plugin) {
        super(plugin, "progress", "Check quest progress",
              "/quest progress <quest_id> [player]", "rvnkquests.command.progress", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args, 1)) {
            return true;
        }

        String questId = args[0].toLowerCase();

        // Validate quest exists FIRST — so invalid IDs are reported even from console
        Quest quest = plugin.getQuestManager().getQuest(questId).orElse(null);
        if (quest == null) {
            sendErrorMessage(sender, "Unknown quest: " + questId);
            return true;
        }

        // Determine target player
        Player targetPlayer;
        if (args.length >= 2) {
            // Player specified as argument
            targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                sendErrorMessage(sender, "Player not found: " + args[1]);
                return true;
            }
        } else if (sender instanceof Player) {
            // Use sender if they're a player
            targetPlayer = (Player) sender;
        } else {
            // Console must specify a player
            sendErrorMessage(sender, "Console must specify a player: /quest progress <quest_id> <player>");
            return true;
        }

        logger.debug("Checking quest progress for " + questId + " (player: " + targetPlayer.getName() + ")");

        // Get progress service
        IQuestProgressService progressService = plugin.getQuestProgressService();
        if (progressService == null) {
            sendErrorMessage(sender, "Quest progress service not available");
            logger.error("QuestProgressService is null");
            return true;
        }

        // Fetch and display progress asynchronously
        progressService.getProgress(targetPlayer.getUniqueId(), questId)
            .thenCompose(progressOpt -> {
                if (progressOpt.isEmpty()) {
                    // No progress means quest not started
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        displayNotStarted(sender, quest, targetPlayer);
                    });
                    // FIX: Return CompletableFuture instead of null to prevent NPE
                    return CompletableFuture.completedFuture(null);
                }

                QuestProgressDTO progress = progressOpt.get();

                // Fetch all objectives for this quest
                return progressService.getAllObjectives(targetPlayer.getUniqueId(), questId)
                    .thenAccept(objectives -> {
                        // Run display on main thread
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            displayProgress(sender, quest, targetPlayer, progress, objectives);
                        });
                    });
            })
            .exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sendErrorMessage(sender, "Failed to retrieve quest progress: " + ex.getMessage());
                    logger.error("Error fetching quest progress", ex);
                });
                return null;
            });

        return true;
    }

    /**
     * Display progress when quest has not been started.
     */
    private void displayNotStarted(CommandSender sender, Quest quest, Player targetPlayer) {
        sendMessage(sender, "&7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sendMessage(sender, "&6Quest Progress: &e" + quest.getName() + " &7(" + quest.getId() + ")");
        sendMessage(sender, "&7Player: &f" + targetPlayer.getName());
        sendMessage(sender, "&7Status: &cNOT_STARTED");
        sendMessage(sender, "&7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (quest.getStartTrigger() != null) {
            sendMessage(sender, "&7Start by: &e" + quest.getStartTrigger());
        }
    }

    /**
     * Display quest progress with objectives.
     */
    private void displayProgress(CommandSender sender, Quest quest, Player targetPlayer,
                                 QuestProgressDTO progress, List<QuestObjectiveProgressDTO> objectives) {
        // Calculate overall completion percentage
        double totalProgress = 0.0;
        if (!objectives.isEmpty()) {
            totalProgress = objectives.stream()
                .mapToDouble(QuestObjectiveProgressDTO::getCompletionPercentage)
                .average()
                .orElse(0.0);
        }
        int percentage = (int) (totalProgress * 100);

        // Determine status display
        String statusColor = getStatusColor(progress.state());
        String statusText = progress.state().name();
        if (progress.state() != QuestState.NOT_STARTED && progress.state() != QuestState.COMPLETED) {
            statusText += " (" + percentage + "% complete)";
        }

        // Header
        sendMessage(sender, "&7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sendMessage(sender, "&6Quest Progress: &e" + quest.getName() + " &7(" + quest.getId() + ")");
        sendMessage(sender, "&7Player: &f" + targetPlayer.getName());
        sendMessage(sender, "&7Status: " + statusColor + statusText);

        // Objectives section
        if (!objectives.isEmpty()) {
            sendMessage(sender, "");
            sendMessage(sender, "&6Objectives:");

            // Find next incomplete objective
            QuestObjectiveProgressDTO nextObjective = null;

            for (QuestObjectiveProgressDTO objective : objectives) {
                String icon = objective.completed() ? "&a✓" : "&7○";
                String progressText = objective.progressCount() + "/" + objective.targetCount();
                String objectiveName = formatObjectiveName(objective.objectiveId());

                sendMessage(sender, "  " + icon + " &f" + objectiveName + " &7(" + progressText + ")");

                // Track first incomplete objective
                if (!objective.completed() && nextObjective == null) {
                    nextObjective = objective;
                }
            }

            // Show next step hint
            if (nextObjective != null && progress.state() != QuestState.COMPLETED) {
                sendMessage(sender, "");
                int remaining = nextObjective.getRemainingCount();
                String objectiveName = formatObjectiveName(nextObjective.objectiveId());
                sendMessage(sender, "&eNext: &f" + objectiveName + " &7(need " + remaining + " more)");
            }
        } else if (progress.state() != QuestState.NOT_STARTED && progress.state() != QuestState.COMPLETED) {
            sendMessage(sender, "");
            sendMessage(sender, "&7No objectives tracked for this quest.");
        }

        // Footer
        sendMessage(sender, "&7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * Get color code for quest state.
     */
    private String getStatusColor(QuestState state) {
        return switch (state) {
            case NOT_STARTED -> "&7";
            case QUEST_ACTIVE -> "&b";
            case COMPLETED -> "&a";
            default -> "&e";
        };
    }

    /**
     * Format objective ID to be more human-readable.
     * Converts "kill_zombies" to "Kill zombies"
     */
    private String formatObjectiveName(String objectiveId) {
        if (objectiveId == null || objectiveId.isEmpty()) {
            return "Unknown objective";
        }

        // Replace underscores with spaces and capitalize first letter
        String formatted = objectiveId.replace('_', ' ');
        return formatted.substring(0, 1).toUpperCase() + formatted.substring(1);
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
