package org.fourz.RVNKQuests.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.dto.JournalEntryDTO;
import org.fourz.RVNKQuests.data.dto.JournalEntryDTO.JournalAction;
import org.fourz.RVNKQuests.data.repository.IJournalRepository;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Subcommand for viewing player quest journal.
 * Extends BaseSubCommand to provide standardized subcommand functionality.
 * Usage: /quest journal [player]
 *
 * <p>Supports console execution by specifying target player as argument.</p>
 * <p>Displays quest history with completion dates, statistics, and color-coded states.</p>
 *
 * @author Forge-1 (forge-build-specialist)
 */
public class QuestJournalSubCommand extends BaseSubCommand {

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    public QuestJournalSubCommand(RVNKQuests plugin) {
        super(plugin, "journal", "View quest journal history",
              "/quest journal [player]", "rvnkquests.command.journal", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        // Determine target player
        Player targetPlayer;
        boolean viewingOther = false;

        if (args.length >= 1) {
            // Player specified as argument
            targetPlayer = Bukkit.getPlayer(args[0]);
            if (targetPlayer == null) {
                sendErrorMessage(sender, "Player not found: " + args[0]);
                return true;
            }
            viewingOther = true;
        } else if (sender instanceof Player) {
            // Use sender if they're a player
            targetPlayer = (Player) sender;
        } else {
            // Console must specify a player
            sendErrorMessage(sender, "Console must specify a player: /quest journal <player>");
            return true;
        }

        // Check permission for viewing other player's journal
        if (viewingOther && !sender.hasPermission("rvnkquests.journal.other")) {
            sendErrorMessage(sender, "You don't have permission to view other players' journals");
            return true;
        }

        logger.debug("Fetching quest journal for " + targetPlayer.getName() +
                    " (requested by: " + sender.getName() + ")");

        // Get journal repository (NOTE: Implementation needs to be added to plugin)
        IJournalRepository journalRepository = getJournalRepository();

        if (journalRepository == null || !journalRepository.isAvailable()) {
            sendErrorMessage(sender, "Journal system is not available");
            logger.warning("Journal repository not available or not initialized");
            return true;
        }

        // Fetch journal entries asynchronously
        UUID playerUuid = targetPlayer.getUniqueId();
        journalRepository.findByPlayer(playerUuid)
            .thenAccept(entries -> {
                // Run display on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    displayJournal(sender, targetPlayer, entries);
                });
            })
            .exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sendErrorMessage(sender, "Failed to retrieve quest journal: " + ex.getMessage());
                    logger.error("Error fetching journal entries for " + targetPlayer.getName(), ex);
                });
                return null;
            });

        return true;
    }

    /**
     * Display the journal with history and statistics.
     *
     * @param sender The command sender
     * @param targetPlayer The player whose journal is being viewed
     * @param entries List of journal entries
     */
    private void displayJournal(CommandSender sender, Player targetPlayer, List<JournalEntryDTO> entries) {
        // Header
        sendMessage(sender, "&7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sendMessage(sender, "&6Quest Journal: &f" + targetPlayer.getName());
        sendMessage(sender, "&7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (entries.isEmpty()) {
            sendMessage(sender, "");
            sendMessage(sender, "&7No quest history found.");
            sendMessage(sender, "&7Start your first quest with &e/quest start <quest_id>");
            sendMessage(sender, "&7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return;
        }

        // Calculate statistics
        Map<JournalAction, Long> actionCounts = entries.stream()
            .collect(Collectors.groupingBy(JournalEntryDTO::action, Collectors.counting()));

        long started = actionCounts.getOrDefault(JournalAction.STARTED, 0L);
        long completed = actionCounts.getOrDefault(JournalAction.COMPLETED, 0L);
        long abandoned = actionCounts.getOrDefault(JournalAction.ABANDONED, 0L);
        long failed = actionCounts.getOrDefault(JournalAction.FAILED, 0L);

        // Display statistics
        sendMessage(sender, "");
        sendMessage(sender, "&6Statistics:");
        sendMessage(sender, "  &7Quests Started: &f" + started);
        sendMessage(sender, "  &aQuests Completed: &f" + completed);

        if (abandoned > 0) {
            sendMessage(sender, "  &6Quests Abandoned: &f" + abandoned);
        }

        if (failed > 0) {
            sendMessage(sender, "  &cQuests Failed: &f" + failed);
        }

        // Calculate completion rate
        if (started > 0) {
            double completionRate = (completed * 100.0) / started;
            String rateColor = completionRate >= 75 ? "&a" : completionRate >= 50 ? "&e" : "&c";
            sendMessage(sender, "  &7Completion Rate: " + rateColor + String.format("%.1f%%", completionRate));
        }

        // Display recent history (last 10 entries)
        sendMessage(sender, "");
        sendMessage(sender, "&6Recent History:");

        List<JournalEntryDTO> recentEntries = entries.stream()
            .sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
            .limit(10)
            .toList();

        for (JournalEntryDTO entry : recentEntries) {
            displayJournalEntry(sender, entry);
        }

        // Footer
        sendMessage(sender, "&7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (entries.size() > 10) {
            sendMessage(sender, "&7Showing 10 of " + entries.size() + " total entries");
        }
    }

    /**
     * Display a single journal entry line.
     *
     * @param sender The command sender
     * @param entry The journal entry to display
     */
    private void displayJournalEntry(CommandSender sender, JournalEntryDTO entry) {
        String actionIcon = getActionIcon(entry.action());
        String actionColor = getActionColor(entry.action());
        String questName = formatQuestName(entry.questId());
        String timestamp = DATE_FORMATTER.format(entry.timestamp());

        StringBuilder line = new StringBuilder();
        line.append("  ").append(actionIcon).append(" ");
        line.append(actionColor).append(getActionName(entry.action())).append(" &7");
        line.append(questName).append(" &8(").append(timestamp).append(")");

        if (entry.hasDetails()) {
            line.append(" &f- &7").append(entry.details());
        }

        sendMessage(sender, line.toString());
    }

    /**
     * Get icon for journal action.
     */
    private String getActionIcon(JournalAction action) {
        return switch (action) {
            case STARTED -> "&b▶";
            case COMPLETED -> "&a✓";
            case ABANDONED -> "&6⊘";
            case FAILED -> "&c✗";
            case OBJECTIVE_COMPLETE -> "&e●";
            case PATH_CHOSEN -> "&d⚑";
            case REWARD_CLAIMED -> "&6★";
        };
    }

    /**
     * Get color code for journal action.
     */
    private String getActionColor(JournalAction action) {
        return switch (action) {
            case STARTED -> "&b";
            case COMPLETED -> "&a";
            case ABANDONED -> "&6";
            case FAILED -> "&c";
            case OBJECTIVE_COMPLETE -> "&e";
            case PATH_CHOSEN -> "&d";
            case REWARD_CLAIMED -> "&6";
        };
    }

    /**
     * Get display name for journal action.
     */
    private String getActionName(JournalAction action) {
        return switch (action) {
            case STARTED -> "Started";
            case COMPLETED -> "Completed";
            case ABANDONED -> "Abandoned";
            case FAILED -> "Failed";
            case OBJECTIVE_COMPLETE -> "Objective Complete";
            case PATH_CHOSEN -> "Path Chosen";
            case REWARD_CLAIMED -> "Reward Claimed";
        };
    }

    /**
     * Format quest ID to be more human-readable.
     * Converts "ancient_guardian_quest" to "Ancient Guardian Quest"
     */
    private String formatQuestName(String questId) {
        if (questId == null || questId.isEmpty()) {
            return "Unknown Quest";
        }

        // Replace underscores with spaces and capitalize words
        String[] words = questId.split("_");
        StringBuilder formatted = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                formatted.append(" ");
            }
            String word = words[i];
            if (!word.isEmpty()) {
                formatted.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    formatted.append(word.substring(1).toLowerCase());
                }
            }
        }

        return formatted.toString();
    }

    /**
     * Get the journal repository from the plugin.
     *
     * @return IJournalRepository instance or null if not available
     */
    private IJournalRepository getJournalRepository() {
        // TODO: This needs to be added to RVNKQuests plugin main class
        // For now, return null to indicate the repository is not yet available
        //
        // Expected implementation in RVNKQuests.java:
        //   private IJournalRepository journalRepository;
        //
        //   In onEnable():
        //     journalRepository = new JournalRepositoryImpl(databaseManager);
        //
        //   Public getter:
        //     public IJournalRepository getJournalRepository() {
        //         return journalRepository;
        //     }

        logger.warning("Journal repository not implemented yet - returning null");
        logger.warning("To enable journal functionality, create JournalRepositoryImpl and add getter to RVNKQuests");

        return null;
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Tab complete player names (only if sender has permission to view others)
            if (sender.hasPermission("rvnkquests.journal.other")) {
                String partial = args[0].toLowerCase();
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList());
            }
        }
        return super.getTabCompletionOptions(sender, args);
    }
}
