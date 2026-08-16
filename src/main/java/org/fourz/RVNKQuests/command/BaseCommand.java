package org.fourz.RVNKQuests.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;

/**
 * Abstract base class for all RVNKQuests top-level commands.
 * Provides common functionality for command execution, permission checking,
 * subcommand management, and help text generation.
 *
 * <p>Implements both {@link ICommand} (execution / metadata) and
 * {@link ICommandRouter} (subcommand registry) so that it satisfies the
 * combined {@link RVNKCommand} contract while keeping those two concerns
 * cleanly separated at the interface level.</p>
 *
 * <p>This follows the RVNK ecosystem command framework pattern with:
 * <ul>
 *   <li>Integrated {@link CommandExecutor} and {@link TabCompleter} interfaces</li>
 *   <li>Permission checking with configurable permissions</li>
 *   <li>Subcommand registration and delegation</li>
 *   <li>Standardised help and error messaging</li>
 * </ul>
 */
public abstract class BaseCommand implements ICommand, ICommandRouter, CommandExecutor, TabCompleter {

    protected final RVNKQuests plugin;
    protected final String name;
    protected final String description;
    protected final String usage;
    protected final String permission;
    protected final LogManager logger;
    protected final Map<String, SubCommand> subCommands;

    /**
     * Constructor for BaseCommand.
     *
     * @param plugin      The RVNKQuests plugin instance
     * @param name        The command name
     * @param description The command description
     * @param usage       The command usage string
     * @param permission  The permission required to use the command (can be null)
     */
    public BaseCommand(RVNKQuests plugin, String name, String description, String usage, String permission) {
        this.plugin = plugin;
        this.name = name;
        this.description = description;
        this.usage = usage;
        this.permission = permission;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.subCommands = new HashMap<>();
    }

    /**
     * Constructor for BaseCommand with default permission.
     *
     * @param plugin      The RVNKQuests plugin instance
     * @param name        The command name
     * @param description The command description
     * @param usage       The command usage string
     */
    public BaseCommand(RVNKQuests plugin, String name, String description, String usage) {
        this(plugin, name, description, usage, "rvnkquests." + name.toLowerCase());
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getUsage() {
        return usage;
    }

    @Override
    public String getPermission() {
        return permission;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        if (permission == null || permission.isEmpty()) {
            return true;
        }
        return sender.hasPermission(permission);
    }

    // ==================== ICommandRouter ====================

    @Override
    public void registerSubCommand(String name, SubCommand subCommand) {
        subCommands.put(name.toLowerCase(), subCommand);
        logger.debug("Registered subcommand: " + this.name + " -> " + name);
    }

    @Override
    public SubCommand getSubCommand(String name) {
        return subCommands.get(name.toLowerCase());
    }

    // ==================== Internal helpers ====================

    /**
     * Get all registered subcommand names.
     *
     * @return Set of subcommand names
     */
    protected Set<String> getSubCommandNames() {
        return subCommands.keySet();
    }

    /**
     * Get matching subcommands for tab completion.
     *
     * @param sender  The command sender
     * @param partial The partial subcommand name
     * @return List of matching subcommand names
     */
    protected List<String> getMatchingSubCommands(CommandSender sender, String partial) {
        List<String> matches = new ArrayList<>();
        String lowerPartial = partial.toLowerCase();

        for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
            String subCommandName = entry.getKey();
            SubCommand subCommand = entry.getValue();

            if (subCommandName.startsWith(lowerPartial) && subCommand.hasPermission(sender)) {
                // Check if subcommand is player-only and sender is not a player
                if (subCommand.isPlayerOnly() && !(sender instanceof Player)) {
                    continue;
                }
                matches.add(subCommandName);
            }
        }

        return matches;
    }

    // ==================== Bukkit hooks ====================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        logger.debug("Command executed: " + label + " with " + args.length + " arguments");

        // Check permission
        if (!hasPermission(sender)) {
            sendNoPermissionMessage(sender);
            return true;
        }

        return execute(sender, args);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // If no arguments or help is requested, show help. With a verb argument it serves that
        // verb's usage and worked examples (#1981) — the examples ship in the jar, so they cannot
        // drift from the build the way a second copy in docs/plugins/commands/ does.
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            if (args.length >= 2) {
                sendVerbHelp(sender, args[1].toLowerCase());
            } else {
                sendHelp(sender);
            }
            return true;
        }

        // Check for subcommands
        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = getSubCommand(subCommandName);

        if (subCommand != null) {
            // Check if subcommand is player-only
            if (subCommand.isPlayerOnly() && !(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
                return true;
            }

            // Execute subcommand with remaining arguments
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            return subCommand.execute(sender, subArgs);
        }

        // If no subcommand found, try to execute the base command
        return executeCommand(sender, args);
    }

    /**
     * Execute the base command logic. Subclasses should override this method
     * to provide command-specific functionality.
     *
     * @param sender The command sender
     * @param args   Command arguments
     * @return true if the command was handled successfully
     */
    protected boolean executeCommand(CommandSender sender, String[] args) {
        sendUnknownSubCommandMessage(sender, args[0]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return tabComplete(sender, args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            // Return matching subcommands
            return getMatchingSubCommands(sender, args[0]);
        } else if (args.length > 1) {
            // Delegate to subcommand tab completion
            SubCommand subCommand = getSubCommand(args[0]);
            if (subCommand != null && subCommand.hasPermission(sender)) {
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                return subCommand.getTabCompletions(sender, subArgs);
            }
        }

        return Collections.emptyList();
    }

    @Override
    public void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== " + getName() + " Command ===");
        sender.sendMessage(ChatColor.YELLOW + "Description: " + ChatColor.WHITE + getDescription());
        sender.sendMessage(ChatColor.YELLOW + "Usage: " + ChatColor.WHITE + getUsage());

        if (!subCommands.isEmpty()) {
            // Sorted, and derived from the registry rather than a hand-kept table — a table drifts
            // silently as verbs are added. RVNKWorlds' curated list had lost six verbs before it
            // was caught, so this stays generated even though it costs the grouping.
            List<String> names = new ArrayList<>(subCommands.keySet());
            Collections.sort(names);

            int visible = 0;
            boolean anyExamples = false;
            List<String> lines = new ArrayList<>();
            for (String name : names) {
                SubCommand subCommand = subCommands.get(name);
                if (subCommand == null || !subCommand.hasPermission(sender)) {
                    continue;
                }
                visible++;
                boolean hasExamples = !subCommand.getExamples().isEmpty();
                anyExamples |= hasExamples;
                lines.add(ChatColor.GRAY + "  " + name
                        + (hasExamples ? ChatColor.AQUA + "*" : " ")
                        + ChatColor.WHITE + " - " + subCommand.getDescription());
            }

            sender.sendMessage(ChatColor.YELLOW + "Subcommands (" + visible + "):");
            for (String line : lines) {
                sender.sendMessage(line);
            }
            if (anyExamples) {
                sender.sendMessage(ChatColor.AQUA + "*" + ChatColor.GRAY
                        + " has worked examples — " + ChatColor.WHITE
                        + "/" + getName() + " help <subcommand>");
            }
            sender.sendMessage(ChatColor.GRAY + "Every subcommand also accepts "
                    + ChatColor.WHITE + "/" + getName() + " help <subcommand>"
                    + ChatColor.GRAY + " for its usage.");
        }
    }

    /**
     * {@code /<command> help <verb>} &mdash; one subcommand's usage and worked examples.
     *
     * <p>Usage always prints, because every subcommand has one. Examples print only when the
     * subcommand overrides {@link SubCommand#getExamples()}; a verb whose grammar is a single
     * argument does not need them and says so rather than showing an empty section.</p>
     */
    protected void sendVerbHelp(CommandSender sender, String verb) {
        SubCommand subCommand = getSubCommand(verb);
        if (subCommand == null) {
            sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + verb);
            sender.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/" + getName() + " help"
                    + ChatColor.GRAY + " for the list.");
            return;
        }
        if (!subCommand.hasPermission(sender)) {
            sendNoPermissionMessage(sender);
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== " + getName() + " " + verb + " ===");
        sender.sendMessage(ChatColor.WHITE + subCommand.getDescription());
        sender.sendMessage(ChatColor.YELLOW + "Usage: " + ChatColor.WHITE + subCommand.getUsage());
        if (subCommand.isPlayerOnly()) {
            sender.sendMessage(ChatColor.GRAY + "Players only — not available from console.");
        }
        sender.sendMessage(ChatColor.GRAY + "Permission: " + subCommand.getPermission());

        List<String> examples = subCommand.getExamples();
        if (examples.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY
                    + "No further examples — the usage line above is the whole grammar.");
            return;
        }
        sender.sendMessage(ChatColor.YELLOW + "Examples:");
        for (String example : examples) {
            if (example.startsWith("  ")) {
                sender.sendMessage(ChatColor.DARK_GRAY + "     " + example.trim());
            } else {
                sender.sendMessage(ChatColor.WHITE + "  " + example);
            }
        }
    }

    // ==================== Messaging helpers ====================

    /**
     * Send a message when the sender doesn't have permission.
     *
     * @param sender The command sender
     */
    protected void sendNoPermissionMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
        logger.warning("Permission denied for " + sender.getName() + " attempting to use command: " + getName());
    }

    /**
     * Send a message when an unknown subcommand is used.
     *
     * @param sender         The command sender
     * @param subCommandName The unknown subcommand name
     */
    protected void sendUnknownSubCommandMessage(CommandSender sender, String subCommandName) {
        sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCommandName);
        sender.sendMessage(ChatColor.GRAY + "Use '/" + getName() + " help' for available commands.");
    }

    /**
     * Validate that the sender is a player.
     *
     * @param sender The command sender
     * @return true if the sender is a player
     */
    protected boolean validatePlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return false;
        }
        return true;
    }

    /**
     * Validate the number of arguments.
     *
     * @param sender  The command sender
     * @param args    The arguments
     * @param minArgs Minimum number of arguments required
     * @param usage   Usage string to display if validation fails
     * @return true if validation passes
     */
    protected boolean validateArgs(CommandSender sender, String[] args, int minArgs, String usage) {
        if (args.length < minArgs) {
            sender.sendMessage(ChatColor.RED + "Usage: " + usage);
            return false;
        }
        return true;
    }
}
