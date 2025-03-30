package org.fourz.RVNKQuests.command;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.util.Debug;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages command registration and execution for the RVNKQuests plugin.
 */
public class CommandManager {
    private final RVNKQuests plugin;
    private final Debug debug;
    private final Map<String, CommandExecutor> commands = new HashMap<>();

    public CommandManager(RVNKQuests plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "CommandManager", Level.FINE);
        try {
            registerCommands();
        } catch (Exception e) {
            debug.error("Failed to register commands", e);
        }
    }

    /**
     * Updates the debug level for this manager
     * @param level New log level
     */
    public void updateDebugLevel(Level level) {
        debug.setLogLevel(level);
        debug.debug("CommandManager log level updated to: " + level.getName());
    }

    /**
     * Registers all commands for the plugin
     */
    private void registerCommands() {
        QuestCommand questCommand = null;
        try {
            // Create main quest command
            questCommand = new QuestCommand(plugin);
            
            // Register core subcommands
            // Note: Some of these are already registered in QuestCommand's constructor
            try {
                questCommand.registerSubCommand("item", new QuestItemSubCommand(plugin));
                debug.debug("Registered item subcommand");
            } catch (Exception e) {
                debug.warning("Failed to register item subcommand: " + e.getMessage());
            }
            
            // These may be added later, we'll try them but skip if not available
            tryRegisterSubCommand(questCommand, "trigger", "QuestTriggerSubCommand");
            tryRegisterSubCommand(questCommand, "state", "QuestStateSubCommand");
            tryRegisterSubCommand(questCommand, "debug", "QuestDebugSubCommand");
            tryRegisterSubCommand(questCommand, "reload", "QuestReloadSubCommand");
            tryRegisterSubCommand(questCommand, "validate", "QuestValidateSubCommand");
            
            // Register the main quest command with the server
            registerCommand("quest", questCommand);
            
            debug.info("Commands registered successfully");
        } catch (Exception e) {
            debug.error("Error registering commands", e);
        }
    }
    
    /**
     * Attempts to register a subcommand, handling any errors gracefully
     */
    private void tryRegisterSubCommand(QuestCommand questCommand, String name, String className) {
        try {
            // Check if the subcommand is already registered
            // If so, we don't need to register it again
            if (questCommand.hasSubCommand(name)) {
                debug.debug("Subcommand already registered: " + name);
                return;
            }
            
            // Try to dynamically create the subcommand instance
            Class<?> subCommandClass = Class.forName("org.fourz.RVNKQuests.command." + className);
            SubCommand subCommand = (SubCommand) subCommandClass
                .getConstructor(RVNKQuests.class)
                .newInstance(plugin);
                
            // Register the subcommand
            questCommand.registerSubCommand(name, subCommand);
            debug.debug("Successfully registered subcommand: " + name);
        } catch (ClassNotFoundException e) {
            debug.debug("Subcommand class not available: " + name + " (" + className + ")");
        } catch (NoSuchMethodException e) {
            debug.warning("Constructor not found for subcommand: " + name);
        } catch (Exception e) {
            debug.warning("Failed to register subcommand: " + name + " - " + e.getMessage());
        }
    }

    /**
     * Registers a command with the server
     * 
     * @param commandName The name of the command
     * @param executor The executor for the command
     */
    private void registerCommand(String commandName, CommandExecutor executor) {
        debug.debug("Registering command: " + commandName);
        PluginCommand command = plugin.getCommand(commandName);
        
        if (command == null) {
            debug.warning("Failed to register command: " + commandName + " (not found in plugin.yml)");
            return;
        }
        
        command.setExecutor(executor);
        
        // If the executor also implements TabCompleter, register it
        if (executor instanceof TabCompleter) {
            command.setTabCompleter((TabCompleter) executor);
            debug.debug("Tab completer registered for command: " + commandName);
        }
        
        commands.put(commandName, executor);
        debug.debug("Command registered: " + commandName);
    }

    /**
     * Gets a registered command executor
     * 
     * @param commandName The name of the command
     * @return The command executor, or null if not found
     */
    public CommandExecutor getCommand(String commandName) {
        return commands.get(commandName);
    }
}