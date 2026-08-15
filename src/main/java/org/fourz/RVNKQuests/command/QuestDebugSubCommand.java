package org.fourz.RVNKQuests.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestManager;
import org.fourz.RVNKQuests.quest.QuestState;

import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

/**
 * Debug subcommand for RVNKQuests.
 * Provides diagnostic commands for troubleshooting quest system issues.
 *
 * Usage:
 *   /quest debug diagnostics - Show system health status
 *   /quest debug list - List all registered quests with status
 *   /quest debug player [name] - Show player quest progress
 *   /quest debug loglevel [level] - View or change log level
 */
public class QuestDebugSubCommand extends BaseSubCommand {

    private static final List<String> SUB_COMMANDS = Arrays.asList(
        "diagnostics", "list", "player", "loglevel", "seed", "setstate", "setup", "preflight"
    );

    private SeedSubCommand seedSubCommand;
    private QuestSetStateSubCommand setStateSubCommand;
    private QuestPreflightSubCommand preflightSubCommand;

    private static final List<String> LOG_LEVELS = Arrays.asList("DEBUG", "INFO", "WARN", "OFF");

    public QuestDebugSubCommand(RVNKQuests plugin) {
        super(plugin, "debug", "Debug and diagnostics commands",
              "/quest debug <subcommand>", "rvnkquests.admin", false);
        this.seedSubCommand = new SeedSubCommand(plugin);
        this.setStateSubCommand = new QuestSetStateSubCommand(plugin);
        this.preflightSubCommand = new QuestPreflightSubCommand(plugin);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String[] subArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];

        switch (subCommand) {
            case "diagnostics":
            case "diag":
                return executeDiagnostics(sender);
            case "list":
            case "ls":
                return executeList(sender);
            case "player":
            case "p":
                return executePlayer(sender, subArgs);
            case "loglevel":
            case "level":
                return handleLogLevel(sender, subArgs);
            case "seed":
                return seedSubCommand.execute(sender, subArgs);
            case "setstate":
                return setStateSubCommand.execute(sender, subArgs);
            case "setup":
                return executeSetup(sender);
            case "preflight":
            case "pf":
                return preflightSubCommand.execute(sender, subArgs);
            default:
                sendErrorMessage(sender, "Unknown debug command: " + subCommand);
                showUsage(sender);
                return true;
        }
    }

    private void showUsage(CommandSender sender) {
        sendMessage(sender, "&6=== RVNKQuests Debug Commands ===");
        sendMessage(sender, "&7/quest debug diagnostics &8- Show system health status");
        sendMessage(sender, "&7/quest debug list &8- List all registered quests");
        sendMessage(sender, "&7/quest debug player [name] &8- Show player quest progress");
        sendMessage(sender, "&7/quest debug loglevel [level] &8- View or change log level");
        sendMessage(sender, "&7/quest debug seed <action> &8- Seed/cleanup test data");
        sendMessage(sender, "&7/quest debug setstate <quest> <state> [player] &8- Set quest state (bypasses validation)");
        sendMessage(sender, "&7/quest debug setup &8- Bootstrap LuckPerms permission defaults");
        sendMessage(sender, "&7/quest debug preflight <quest> [--no-load] [--force] &8- Check worlds, blocks, states, rewards");
    }

    /**
     * impl-15: Show diagnostic information about the quest system
     */
    private boolean executeDiagnostics(CommandSender sender) {
        sendMessage(sender, "&6=== RVNKQuests Diagnostics ===");

        // Plugin status
        sendMessage(sender, "&7Plugin Version: &f" + plugin.getDescription().getVersion());

        // Quest Manager status
        QuestManager qm = plugin.getQuestManager();
        if (qm != null) {
            List<Quest> quests = qm.getAllQuests();
            List<String> questIds = qm.getQuestIds();

            sendMessage(sender, "&7Quest System:");
            sendMessage(sender, "&7  Registered Quests: &f" + quests.size());
            sendMessage(sender, "&7  Quest IDs: &f" + String.join(", ", questIds));

            // Count quests by state — use per-player state when sender is a player
            int activeCount = 0;
            int notStartedCount = 0;
            int completedCount = 0;
            boolean hasPlayerContext = sender instanceof Player;
            Player playerSender = hasPlayerContext ? (Player) sender : null;
            for (Quest quest : quests) {
                QuestState state = hasPlayerContext
                    ? quest.getStateForPlayer(playerSender)
                    : quest.getCurrentState();
                if (state == QuestState.QUEST_ACTIVE || state == QuestState.OBJECTIVE_FOUND ||
                    state == QuestState.TRIGGER_FOUND) {
                    activeCount++;
                } else if (state == QuestState.COMPLETED) {
                    completedCount++;
                } else {
                    notStartedCount++;
                }
            }
            String contextLabel = hasPlayerContext ? " (for " + playerSender.getName() + ")" : " (global)";
            sendMessage(sender, "&7  Active: &a" + activeCount + "&7, Not Started: &e" + notStartedCount +
                       "&7, Completed: &b" + completedCount + "&7" + contextLabel);
        } else {
            sendMessage(sender, "&7Quest Manager: &cNOT INITIALIZED");
        }

        // Config Manager status
        if (plugin.getConfigManager() != null) {
            String logLevel = plugin.getConfig().getString("general.logLevel", "INFO");
            sendMessage(sender, "&7Config Status: &aLoaded");
            sendMessage(sender, "&7  Log Level: &f" + logLevel);
        } else {
            sendMessage(sender, "&7Config Manager: &cNOT INITIALIZED");
        }

        // Lore Database status
        if (plugin.hasLoreDatabase()) {
            sendMessage(sender, "&7Lore Database: &aENABLED");
        } else {
            sendMessage(sender, "&7Lore Database: &7disabled");
        }

        // Server info
        sendMessage(sender, "&7Online Players: &f" + Bukkit.getOnlinePlayers().size());

        // Memory usage
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        sendMessage(sender, "&7Memory: &f" + usedMemory + "MB / " + maxMemory + "MB");

        // RVNKCore integration check
        boolean rvnkCorePresent = Bukkit.getPluginManager().getPlugin("RVNKCore") != null;
        sendMessage(sender, "&7RVNKCore: " + (rvnkCorePresent ? "&aConnected" : "&cNot Found"));

        return true;
    }

    /**
     * impl-16: List all registered quests with their status
     */
    private boolean executeList(CommandSender sender) {
        QuestManager qm = plugin.getQuestManager();
        if (qm == null) {
            sendErrorMessage(sender, "Quest Manager not initialized");
            return true;
        }

        List<Quest> quests = qm.getAllQuests();

        sendMessage(sender, "&6=== Registered Quests (" + quests.size() + ") ===");

        if (quests.isEmpty()) {
            sendMessage(sender, "&7No quests registered.");
            return true;
        }

        boolean hasPlayerContext = sender instanceof Player;
        Player listPlayer = hasPlayerContext ? (Player) sender : null;
        if (hasPlayerContext) {
            sendMessage(sender, "&7(Showing state for: &f" + listPlayer.getName() + "&7)");
        }

        for (Quest quest : quests) {
            String id = quest.getId();
            String name = quest.getName();
            QuestState state = hasPlayerContext
                ? quest.getStateForPlayer(listPlayer)
                : quest.getCurrentState();
            String trigger = quest.getStartTrigger();

            // Color-code based on state
            String stateColor;
            switch (state) {
                case QUEST_ACTIVE:
                case OBJECTIVE_FOUND:
                    stateColor = "&a";
                    break;
                case COMPLETED:
                    stateColor = "&b";
                    break;
                case NOT_STARTED:
                    stateColor = "&7";
                    break;
                case TRIGGER_FOUND:
                default:
                    stateColor = "&e";
            }

            sendMessage(sender, stateColor + "● &f" + id + " &7- &f" + name);
            sendMessage(sender, "  &7State: " + stateColor + state.name() +
                       " &7| Trigger: &f" + (trigger != null ? trigger : "N/A"));
        }

        return true;
    }

    /**
     * impl-17: Show player quest progress
     */
    private boolean executePlayer(CommandSender sender, String[] args) {
        Player target;

        if (args.length > 0) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sendErrorMessage(sender, "Player not found: " + args[0]);
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sendErrorMessage(sender, "Usage: /quest debug player <name>");
            return true;
        }

        QuestManager qm = plugin.getQuestManager();
        if (qm == null) {
            sendErrorMessage(sender, "Quest Manager not initialized");
            return true;
        }

        List<Quest> quests = qm.getAllQuests();

        sendMessage(sender, "&6=== Quest Progress: " + target.getName() + " ===");
        sendMessage(sender, "&7UUID: &f" + target.getUniqueId());
        sendMessage(sender, "&7World: &f" + target.getWorld().getName());

        if (quests.isEmpty()) {
            sendMessage(sender, "&7No quests registered.");
            return true;
        }

        int completedCount = 0;
        int inProgressCount = 0;

        sendMessage(sender, "&7");
        sendMessage(sender, "&7Quest Status:");

        for (Quest quest : quests) {
            boolean completed = quest.isCompleted(target);
            QuestState state = quest.getStateForPlayer(target);

            if (completed) {
                completedCount++;
                sendMessage(sender, "&a✓ &f" + quest.getName() + " &7- &aCompleted");
            } else if (state == QuestState.QUEST_ACTIVE || state == QuestState.OBJECTIVE_FOUND ||
                       state == QuestState.TRIGGER_FOUND) {
                inProgressCount++;
                sendMessage(sender, "&e◐ &f" + quest.getName() + " &7- &eIn Progress (" + state + ")");
            } else {
                sendMessage(sender, "&7○ &f" + quest.getName() + " &7- &7Not Started");
            }
        }

        sendMessage(sender, "&7");
        sendMessage(sender, "&7Summary: &a" + completedCount + " completed&7, &e" +
                   inProgressCount + " in progress&7, &f" +
                   (quests.size() - completedCount - inProgressCount) + " available");

        return true;
    }

    /**
     * View or change the log level at runtime.
     * Usage: /quest debug loglevel [DEBUG|INFO|WARN|OFF]
     */
    private boolean handleLogLevel(CommandSender sender, String[] args) {
        if (args.length == 0) {
            String currentLevel = plugin.getConfig().getString("general.logLevel", "INFO");
            sendInfoMessage(sender, "Current log level: " + currentLevel);
            sendInfoMessage(sender, "Usage: /quest debug loglevel <DEBUG|INFO|WARN|OFF>");
            return true;
        }

        String levelStr = args[0].toUpperCase();
        Level level = LogManager.parseLevel(levelStr);

        LogManager.setPluginLogLevel(plugin, level);

        plugin.getConfig().set("general.logLevel", levelStr);
        plugin.saveConfig();

        sendSuccessMessage(sender, "Log level set to: " + levelStr);
        sendInfoMessage(sender, "(Saved to config.yml)");

        return true;
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String cmd : SUB_COMMANDS) {
                if (cmd.startsWith(partial)) {
                    completions.add(cmd);
                }
            }
        } else if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            String partial = args[1].toLowerCase();

            if (subCmd.equals("player") || subCmd.equals("p")) {
                // Complete with online player names
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(partial)) {
                        completions.add(player.getName());
                    }
                }
            } else if (subCmd.equals("loglevel") || subCmd.equals("level")) {
                // Complete with log levels
                String upperPartial = args[1].toUpperCase();
                for (String level : LOG_LEVELS) {
                    if (level.startsWith(upperPartial)) {
                        completions.add(level);
                    }
                }
            } else if (subCmd.equals("seed")) {
                // Delegate to seed subcommand
                return seedSubCommand.getTabCompletions(sender,
                    Arrays.copyOfRange(args, 1, args.length));
            } else if (subCmd.equals("setstate")) {
                // Delegate to setstate subcommand
                return setStateSubCommand.getTabCompletions(sender,
                    Arrays.copyOfRange(args, 1, args.length));
            }
        }

        return completions;
    }

    private boolean executeSetup(CommandSender sender) {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            sendErrorMessage(sender, "LuckPerms is not installed — cannot apply permission defaults.");
            sendMessage(sender, "&7Install LuckPerms and run this command again.");
            return true;
        }

        sendMessage(sender, "&6=== RVNKQuests Permission Setup ===");
        sendMessage(sender, "&7Applying LuckPerms defaults...");

        String[][] assignments = {
            {"admin",     "rvnkquests.admin",            "true"},
            {"moderator", "rvnkquests.admin.reset",      "true"},
            {"moderator", "rvnkquests.admin.complete",   "true"},
            {"default",   "rvnkquests.list",             "true"},
            {"default",   "rvnkquests.start",            "true"},
            {"default",   "rvnkquests.abandon",          "true"},
            {"default",   "rvnkquests.progress",         "true"},
            {"default",   "rvnkquests.journal",          "true"},
            {"default",   "rvnkquests.leaderboard",      "true"},
            {"default",   "rvnkquests.chain",            "true"},
        };

        org.bukkit.command.ConsoleCommandSender console = Bukkit.getConsoleSender();
        int ok = 0;
        int fail = 0;

        for (String[] row : assignments) {
            String cmd = "lp group " + row[0] + " permission set " + row[1] + " " + row[2];
            try {
                Bukkit.dispatchCommand(console, cmd);
                sendMessage(sender, "&a✓ &7" + row[0] + " &8← &f" + row[1]);
                ok++;
            } catch (Exception e) {
                sendErrorMessage(sender, "Failed: " + cmd + " (" + e.getMessage() + ")");
                fail++;
            }
        }

        sendMessage(sender, "&7Done. &f" + ok + " applied" + (fail > 0 ? "&c, " + fail + " failed" : "") + ".");
        sendMessage(sender, "&8Run &7lp editor&8 to review or adjust group assignments.");
        return true;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnkquests.admin") || sender.isOp();
    }


    /**
     * Worked examples for {@code /quest help debug} (#1981).
     */
    @Override
    public java.util.List<String> getExamples() {
        return java.util.List.of(
                "/quest debug diagnostics",
                "/quest debug list",
                "/quest debug player Shad0melt",
                "/quest debug loglevel DEBUG",
                "  persists to config.yml - set it back to WARNING when you are done",
                "/quest debug preflight tfah_ch1_journey",
                "  reports blockers before anyone plays it; 0 blockers means startable",
                "/quest debug preflight tfah_ch1_journey --no-load --force",
                "/quest debug setstate tfah_ch1_journey QUEST_ACTIVE Shad0melt",
                "/quest debug seed minimal",
                "  minimal 10 / standard 100 / stress 1000 / cleanup / status",
                "DEBUG logging plus load is an availability risk - it once queued ~150k lines",
                "and blocked shutdown for 11 minutes.");
    }
}
