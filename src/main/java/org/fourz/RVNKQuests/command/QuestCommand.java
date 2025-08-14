package org.fourz.RVNKQuests.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.util.log.LogManager;
import org.fourz.RVNKQuests.util.log.FZLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main command handler for the /quest command.
 * Dispatches to appropriate subcommands based on arguments.
 */
public class QuestCommand implements CommandExecutor, TabCompleter {
    private final RVNKQuests plugin;
    private final FZLogger logger;
    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public QuestCommand(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        registerCoreCommands();
    }

    /**
     * Registers the core subcommands that are always available
     */
    private void registerCoreCommands() {
        logger.debug("Registering core subcommands");
        
        // Create and register each subcommand directly
        registerSubCommand("item", new QuestItemSubCommand(plugin));
        registerSubCommand("state", new QuestStateSubCommand(plugin));
        registerSubCommand("reload", new QuestReloadSubCommand(plugin));
        registerSubCommand("trigger", new QuestTriggerSubCommand(plugin));
        registerSubCommand("debug", new QuestDebugSubCommand(plugin));
        registerSubCommand("mobs", new QuestMobsSubCommand(plugin));
        registerSubCommand("config", new QuestConfigSubCommand(plugin));
        registerSubCommand("validate", new QuestValidateSubCommand(plugin));
        //registerSubCommand("help", new QuestHelpSubCommand(plugin));
        
        logger.debug("Core subcommands registered");
    }

    /**
     * Registers a subcommand if it doesn't already exist
     * 
     * @param name The name of the subcommand (case insensitive)
     * @param subCommand The subcommand implementation
     * @return true if the command was newly registered, false if it already existed
     */
    public boolean registerSubCommand(String name, SubCommand subCommand) {
        if (name == null || name.isEmpty() || subCommand == null) {
            logger.warning("Invalid subcommand registration attempt");
            return false;
        }
        
        String lowerName = name.toLowerCase();
        if (subCommands.containsKey(lowerName)) {
            logger.debug("Subcommand already registered: " + name + "");
            return false;
        }
        
        subCommands.put(lowerName, subCommand);
        logger.debug("Registered subcommand: " + name + "");
        return true;
    }
    
    /**
     * Checks if a subcommand is already registered
     * 
     * @param name The name of the subcommand (case insensitive)
     * @return true if the subcommand exists
     */
    public boolean hasSubCommand(String name) {
        return name != null && subCommands.containsKey(name.toLowerCase());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);

        if (subCommand == null) {
            sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCommandName);
            showHelp(sender);
            return true;
        }

        if (!subCommand.hasPermission(sender)) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        // Remove the subcommand name from args
        String[] subCommandArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subCommandArgs, 0, args.length - 1);

        logger.debug("Executing subcommand: " + subCommandName + "");
        return subCommand.execute(sender, subCommandArgs);
    }

    /**
     * Shows help information to the sender
     * 
     * @param sender Command sender to show help to
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== RVNKQuests Commands =====");
        
        for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
            if (entry.getValue().hasPermission(sender)) {
                sender.sendMessage(ChatColor.YELLOW + "/quest " + entry.getKey() + 
                                   ChatColor.WHITE + " - " + entry.getValue().getDescription());
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Complete subcommand names
            String partial = args[0].toLowerCase();
            for (String subCommand : subCommands.keySet()) {
                if (subCommands.get(subCommand).hasPermission(sender) && 
                    subCommand.startsWith(partial)) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length > 1) {
            // Pass to subcommand for completion
            String subCommandName = args[0].toLowerCase();
            SubCommand subCommand = subCommands.get(subCommandName);
            
            if (subCommand != null && subCommand.hasPermission(sender)) {
                String[] subCommandArgs = new String[args.length - 1];
                System.arraycopy(args, 1, subCommandArgs, 0, args.length - 1);
                
                List<String> subCommandCompletions = subCommand.getTabCompletions(sender, subCommandArgs);
                if (subCommandCompletions != null) {
                    completions.addAll(subCommandCompletions);
                }
            }
        }

        return completions;
    }
}
