package org.fourz.RVNKQuests.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.dto.JournalEntryDTO;
import org.fourz.RVNKQuests.data.dto.JournalEntryDTO.JournalAction;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.service.IJournalService;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Subcommand for viewing and managing player quest journal.
 * Usage: /quest journal [player]
 *        /quest journal view <quest_id> [player]
 *        /quest journal remove <quest_id> [player]
 *        /quest journal assign <quest_id> <player>
 *        /quest journal unassign <quest_id> <player>
 */
public class QuestJournalSubCommand extends BaseSubCommand {

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    public QuestJournalSubCommand(RVNKQuests plugin) {
        super(plugin, "journal", "View and manage quest journal",
              "/quest journal [view|remove] [quest_id] [player]", "rvnkquests.journal", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        // Parse subaction: /quest journal [view|remove] [quest_id] [player]
        String subaction = "list"; // default
        int argOffset = 0;

        if (args.length >= 1) {
            String first = args[0].toLowerCase();
            if (first.equals("view") || first.equals("remove") ||
                first.equals("assign") || first.equals("unassign")) {
                subaction = first;
                argOffset = 1;
            }
        }

        // Staff assign/unassign have different arg parsing: <quest_id> <player> (player required)
        if (subaction.equals("assign") || subaction.equals("unassign")) {
            return handleStaffCommand(sender, subaction, args, argOffset);
        }

        // For view/remove, quest_id is required
        String questId = null;
        if (!subaction.equals("list")) {
            if (args.length < argOffset + 1) {
                sendMessage(sender, "&c\u25b6 Usage: /quest journal " + subaction + " <quest_id> [player]");
                return true;
            }
            questId = args[argOffset];
            argOffset++;
        }

        // Determine target player
        Player targetPlayer;
        boolean viewingOther = false;

        if (args.length > argOffset) {
            targetPlayer = Bukkit.getPlayer(args[argOffset]);
            if (targetPlayer == null) {
                sendErrorMessage(sender, "Player not found: " + args[argOffset]);
                return true;
            }
            viewingOther = true;
        } else if (sender instanceof Player) {
            targetPlayer = (Player) sender;
        } else {
            sendErrorMessage(sender, "Console must specify a player: /quest journal [view|remove] [quest_id] <player>");
            return true;
        }

        if (viewingOther && !sender.hasPermission("rvnkquests.journal.other")) {
            sendErrorMessage(sender, "You don't have permission to view other players' journals");
            return true;
        }

        IJournalService journalService = plugin.getJournalService();
        if (journalService == null || !journalService.isAvailable()) {
            sendErrorMessage(sender, "Journal system is not available");
            return true;
        }

        UUID playerUuid = targetPlayer.getUniqueId();
        String playerName = targetPlayer.getName();

        switch (subaction) {
            case "list" -> handleList(sender, playerUuid, playerName, journalService);
            case "view" -> handleView(sender, playerUuid, playerName, questId, journalService);
            case "remove" -> handleRemove(sender, playerUuid, playerName, questId, journalService);
        }

        return true;
    }

    private void handleList(CommandSender sender, UUID playerUuid, String playerName, IJournalService journalService) {
        journalService.getPlayerJournal(playerUuid)
            .thenAccept(entries -> Bukkit.getScheduler().runTask(plugin, () -> displayJournal(sender, playerName, entries)))
            .exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin, () ->
                    sendErrorMessage(sender, "Failed to retrieve journal: " + ex.getMessage()));
                return null;
            });
    }

    private void handleView(CommandSender sender, UUID playerUuid, String playerName, String questId, IJournalService journalService) {
        journalService.getQuestJournal(playerUuid, questId)
            .thenAccept(entries -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (entries.isEmpty()) {
                    sendMessage(sender, "&eNo journal entries for quest " + questId);
                    return;
                }
                sendMessage(sender, "&7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                sendMessage(sender, "&6Journal: &f" + formatQuestName(questId) + " &7(" + playerName + ")");
                sendMessage(sender, "&7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                for (JournalEntryDTO entry : entries) {
                    displayJournalEntry(sender, entry);
                }
                sendMessage(sender, "&7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            }))
            .exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin, () ->
                    sendErrorMessage(sender, "Failed to retrieve journal: " + ex.getMessage()));
                return null;
            });
    }

    private void handleRemove(CommandSender sender, UUID playerUuid, String playerName, String questId, IJournalService journalService) {
        if (!sender.hasPermission("rvnkquests.journal.remove")) {
            sendErrorMessage(sender, "You don't have permission to remove journal entries");
            return;
        }

        journalService.clearQuestJournal(playerUuid, questId)
            .thenAccept(deleted -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (deleted > 0) {
                    sendSuccessMessage(sender, "Removed " + deleted + " journal entries for " + questId + " (" + playerName + ")");
                } else {
                    sendMessage(sender, "&eNo journal entries found for quest " + questId);
                }
            }))
            .exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin, () ->
                    sendErrorMessage(sender, "Failed to remove entries: " + ex.getMessage()));
                return null;
            });
    }

    private boolean handleStaffCommand(CommandSender sender, String action, String[] args, int argOffset) {
        if (!sender.hasPermission("rvnkquests.journal.staff")) {
            sendErrorMessage(sender, "You don't have permission to " + action + " quests");
            return true;
        }

        if (args.length < argOffset + 2) {
            sendMessage(sender, "&c▶ Usage: /quest journal " + action + " <quest_id> <player>");
            return true;
        }

        String questId = args[argOffset];
        String playerArg = args[argOffset + 1];

        // Support offline players so console can assign/unassign without requiring target to be online
        Player onlinePlayer = Bukkit.getPlayer(playerArg);
        UUID playerUuid;
        String playerName;
        if (onlinePlayer != null) {
            playerUuid = onlinePlayer.getUniqueId();
            playerName = onlinePlayer.getName();
        } else {
            @SuppressWarnings("deprecation")
            OfflinePlayer offline = Bukkit.getOfflinePlayer(playerArg);
            if (!offline.hasPlayedBefore()) {
                sendErrorMessage(sender, "Player not found: " + playerArg);
                return true;
            }
            playerUuid = offline.getUniqueId();
            playerName = offline.getName() != null ? offline.getName() : playerArg;
        }

        // Validate quest exists
        if (plugin.getQuestManager().getQuest(questId).isEmpty()) {
            sendErrorMessage(sender, "Quest not found: " + questId);
            return true;
        }

        IJournalService journalService = plugin.getJournalService();
        if (journalService == null || !journalService.isAvailable()) {
            sendErrorMessage(sender, "Journal system is not available");
            return true;
        }

        if (action.equals("assign")) {
            handleAssign(sender, playerUuid, playerName, questId, journalService);
        } else {
            handleUnassign(sender, playerUuid, playerName, questId, journalService);
        }
        return true;
    }

    private void handleAssign(CommandSender sender, UUID playerUuid, String playerName, String questId, IJournalService journalService) {
        plugin.getQuestManager().getPlayerQuestState(playerUuid, questId)
            .thenCompose(state -> {
                if (state != QuestState.NOT_STARTED) {
                    throw new IllegalStateException("Quest is already " + state.name().toLowerCase().replace('_', ' ') + " for " + playerName);
                }
                return plugin.getQuestManager().startQuest(playerUuid, questId)
                    .thenCompose(started -> journalService.recordAction(
                        playerUuid, questId, JournalAction.STARTED, "Assigned by " + sender.getName()));
            })
            .thenAccept(entry -> Bukkit.getScheduler().runTask(plugin, () ->
                sendSuccessMessage(sender, "Assigned quest " + formatQuestName(questId) + " to " + playerName)))
            .exceptionally(ex -> {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                Bukkit.getScheduler().runTask(plugin, () ->
                    sendErrorMessage(sender, "Failed to assign quest: " + cause.getMessage()));
                return null;
            });
    }

    private void handleUnassign(CommandSender sender, UUID playerUuid, String playerName, String questId, IJournalService journalService) {
        // Clear journal entries only — quest progress is preserved so history is not destroyed.
        // Progress remains in the DB; the quest simply no longer appears in the player's journal view.
        journalService.clearQuestJournal(playerUuid, questId)
            .thenAccept(deleted -> Bukkit.getScheduler().runTask(plugin, () ->
                sendSuccessMessage(sender, "Unassigned quest " + formatQuestName(questId)
                    + " from " + playerName + " (" + deleted + " journal entries hidden, progress preserved)")))
            .exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin, () ->
                    sendErrorMessage(sender, "Failed to unassign quest: " + ex.getMessage()));
                return null;
            });
    }

    private void displayJournal(CommandSender sender, String playerName, List<JournalEntryDTO> entries) {
        sendMessage(sender, "&7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sendMessage(sender, "&6Quest Journal: &f" + playerName);
        sendMessage(sender, "&7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (entries.isEmpty()) {
            sendMessage(sender, "");
            sendMessage(sender, "&7No quest history found.");
            sendMessage(sender, "&7Start your first quest with &e/quest start <quest_id>");
            sendMessage(sender, "&7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return;
        }

        // Statistics
        Map<JournalAction, Long> actionCounts = entries.stream()
            .collect(Collectors.groupingBy(JournalEntryDTO::action, Collectors.counting()));

        long started = actionCounts.getOrDefault(JournalAction.STARTED, 0L);
        long completed = actionCounts.getOrDefault(JournalAction.COMPLETED, 0L);
        long abandoned = actionCounts.getOrDefault(JournalAction.ABANDONED, 0L);

        sendMessage(sender, "");
        sendMessage(sender, "&6Statistics:");
        sendMessage(sender, "  &7Started: &f" + started + "  &aCompleted: &f" + completed +
            (abandoned > 0 ? "  &6Abandoned: &f" + abandoned : ""));

        if (started > 0) {
            double rate = (completed * 100.0) / started;
            String color = rate >= 75 ? "&a" : rate >= 50 ? "&e" : "&c";
            sendMessage(sender, "  &7Completion Rate: " + color + String.format("%.0f%%", rate));
        }

        // Recent entries
        sendMessage(sender, "");
        sendMessage(sender, "&6Recent History:");

        entries.stream()
            .sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
            .limit(10)
            .forEach(entry -> displayJournalEntry(sender, entry));

        sendMessage(sender, "&7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (entries.size() > 10) {
            sendMessage(sender, "&7Showing 10 of " + entries.size() + " entries. Use /quest journal view <quest_id> for details.");
        }
    }

    private void displayJournalEntry(CommandSender sender, JournalEntryDTO entry) {
        String icon = getActionIcon(entry.action());
        String color = getActionColor(entry.action());
        String name = formatQuestName(entry.questId());
        String time = DATE_FORMATTER.format(entry.timestamp());

        StringBuilder line = new StringBuilder();
        line.append("  ").append(icon).append(" ");
        line.append(color).append(getActionName(entry.action())).append(" &7");
        line.append(name).append(" &8(").append(time).append(")");

        if (entry.hasDetails()) {
            line.append(" &f- &7").append(entry.details());
        }

        sendMessage(sender, line.toString());
    }

    private String getActionIcon(JournalAction action) {
        return switch (action) {
            case STARTED -> "&b\u25b6";
            case COMPLETED -> "&a\u2713";
            case ABANDONED -> "&6\u2298";
            case FAILED -> "&c\u2717";
            case OBJECTIVE_COMPLETE -> "&e\u25cf";
            case PATH_CHOSEN -> "&d\u2691";
            case REWARD_CLAIMED -> "&6\u2605";
        };
    }

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

    private String getActionName(JournalAction action) {
        return switch (action) {
            case STARTED -> "Started";
            case COMPLETED -> "Completed";
            case ABANDONED -> "Abandoned";
            case FAILED -> "Failed";
            case OBJECTIVE_COMPLETE -> "Objective";
            case PATH_CHOSEN -> "Path Chosen";
            case REWARD_CLAIMED -> "Reward";
        };
    }

    private String formatQuestName(String questId) {
        if (questId == null || questId.isEmpty()) return "Unknown Quest";
        String[] words = questId.split("_");
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) formatted.append(" ");
            String word = words[i];
            if (!word.isEmpty()) {
                formatted.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) formatted.append(word.substring(1).toLowerCase());
            }
        }
        return formatted.toString();
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> options = new java.util.ArrayList<>(List.of("view", "remove"));
            if (sender.hasPermission("rvnkquests.journal.staff")) {
                options.add("assign");
                options.add("unassign");
            }
            // Also suggest player names for the default list action
            if (sender.hasPermission("rvnkquests.journal.other")) {
                Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .forEach(options::add);
            }
            return options.stream()
                .filter(o -> o.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
        }
        String sub = args[0].toLowerCase();
        boolean isQuestIdArg = args.length == 2 && (sub.equals("view") || sub.equals("remove") ||
            sub.equals("assign") || sub.equals("unassign"));
        if (isQuestIdArg) {
            return plugin.getQuestManager().getQuestIds().stream()
                .filter(id -> id.startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }
        boolean isPlayerArg = args.length == 3 && (sub.equals("view") || sub.equals("remove") ||
            sub.equals("assign") || sub.equals("unassign"));
        if (isPlayerArg) {
            if (sender.hasPermission("rvnkquests.journal.other") || sender.hasPermission("rvnkquests.journal.staff")) {
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }

    /**
     * Worked examples for {@code /quest help journal} (#1981).
     */
    @Override
    public java.util.List<String> getExamples() {
        return java.util.List.of(
                "/quest journal",
                "/quest journal view tfah_ch1_journey",
                "/quest journal view tfah_ch1_journey Shad0melt",
                "  console must name the player",
                "/quest journal remove tfah_ch1_journey Shad0melt",
                "Actions recorded: STARTED COMPLETED ABANDONED OBJECTIVE_COMPLETE FAILED",
                "PATH_CHOSEN REWARD_CLAIMED");
    }
}
