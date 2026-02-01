package org.fourz.RVNKQuests.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.leaderboard.LeaderboardEntry;
import org.fourz.RVNKQuests.leaderboard.LeaderboardRepository;
import org.fourz.RVNKQuests.leaderboard.LeaderboardType;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Subcommand for displaying quest leaderboards.
 * Extends BaseSubCommand to provide standardized subcommand functionality.
 * Usage: /quest leaderboard [type]
 *
 * <p>Supports console execution - no player context needed.</p>
 * <p>Displays top 10 players for the specified leaderboard type.</p>
 */
public class QuestLeaderboardSubCommand extends BaseSubCommand {

    private final LeaderboardRepository leaderboardRepository;

    public QuestLeaderboardSubCommand(RVNKQuests plugin) {
        super(plugin, "leaderboard", "View quest leaderboards",
              "/quest leaderboard [type]", "rvnkquests.command.leaderboard", false);
        this.leaderboardRepository = new LeaderboardRepository(plugin, plugin.getDatabaseManager());
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        // Determine leaderboard type (default to MOST_COMPLETED)
        LeaderboardType type = LeaderboardType.MOST_COMPLETED;

        if (args.length >= 1) {
            LeaderboardType parsed = LeaderboardType.fromString(args[0]);
            if (parsed == null) {
                sendErrorMessage(sender, "Unknown leaderboard type: " + args[0]);
                sendMessage(sender, "&7Available types: " + getAvailableTypes());
                return true;
            }
            type = parsed;
        }

        logger.debug("Displaying leaderboard: " + type.name());

        final LeaderboardType finalType = type;

        // Fetch top 10 entries asynchronously
        leaderboardRepository.getTopEntries(type, 10)
            .thenAccept(entries -> {
                // Run display on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    displayLeaderboard(sender, finalType, entries);
                });
            })
            .exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sendErrorMessage(sender, "Failed to retrieve leaderboard: " + ex.getMessage());
                    logger.error("Error fetching leaderboard", ex);
                });
                return null;
            });

        return true;
    }

    /**
     * Display the leaderboard to the sender.
     */
    private void displayLeaderboard(CommandSender sender, LeaderboardType type, List<LeaderboardEntry> entries) {
        // Header
        sendMessage(sender, "&7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sendMessage(sender, "&6Quest Leaderboard: &e" + type.getDisplayName());
        sendMessage(sender, "&7" + type.getDescription());
        sendMessage(sender, "&7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (entries.isEmpty()) {
            sendMessage(sender, "&7No entries yet. Complete quests to get on the board!");
        } else {
            sendMessage(sender, "");

            // Display entries
            for (LeaderboardEntry entry : entries) {
                String rankColor = entry.getRankColor();
                String rankIcon = getRankIcon(entry.rank());
                String valueLabel = getValueLabel(type);

                sendMessage(sender,
                    rankColor + rankIcon + " #" + entry.rank() +
                    " &f" + entry.playerName() +
                    " &7- " + entry.value() + " " + valueLabel);
            }

            sendMessage(sender, "");
            sendMessage(sender, "&7View other types: &e/quest leaderboard <type>");
            sendMessage(sender, "&7Types: " + getAvailableTypes());
        }

        sendMessage(sender, "&7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * Get the rank icon for display.
     */
    private String getRankIcon(int rank) {
        return switch (rank) {
            case 1 -> "👑"; // Crown for 1st
            case 2 -> "🥈"; // Silver medal
            case 3 -> "🥉"; // Bronze medal
            default -> "  "; // Two spaces for alignment
        };
    }

    /**
     * Get the value label based on leaderboard type.
     */
    private String getValueLabel(LeaderboardType type) {
        return switch (type) {
            case MOST_COMPLETED -> "quests";
            case FASTEST_COMPLETION -> "avg seconds";
            case DAILY_CHAMPION -> "today";
            case WEEKLY_CHAMPION -> "this week";
            case STREAK_MASTER -> "streak";
        };
    }

    /**
     * Get a formatted string of available leaderboard types.
     */
    private String getAvailableTypes() {
        return Arrays.stream(LeaderboardType.values())
                .map(type -> type.name().toLowerCase())
                .collect(Collectors.joining(", "));
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Tab complete leaderboard types
            String partial = args[0].toLowerCase();
            return Arrays.stream(LeaderboardType.values())
                    .map(type -> type.name().toLowerCase())
                    .filter(name -> name.startsWith(partial))
                    .collect(Collectors.toList());
        }
        return super.getTabCompletionOptions(sender, args);
    }
}
