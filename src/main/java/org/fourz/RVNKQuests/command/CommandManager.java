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
        try {
            // Create and register the main quest command only
            QuestCommand questCommand = new QuestCommand(plugin);
            
            // Add validate command if it exists (not in core)
            tryRegisterOptionalCommand(questCommand, "validate", "QuestValidateSubCommand");
            
            // Register the main quest command with the server
            registerCommand("quest", questCommand);
            
            debug.info("Commands registered successfully");
        } catch (Exception e) {
            debug.error("Error registering commands", e);
        }
    }
    
    /**
     * Attempts to register an optional subcommand
     */
    private void tryRegisterOptionalCommand(QuestCommand questCommand, String name, String className) {
        try {
            Class<?> cmdClass = Class.forName("org.fourz.RVNKQuests.command." + className);
            SubCommand cmd = (SubCommand) cmdClass.getConstructor(RVNKQuests.class).newInstance(plugin);
            questCommand.registerSubCommand(name, cmd);
            debug.debug("Registered optional command: " + name);
        } catch (ClassNotFoundException e) {
            debug.debug("Optional command not available: " + name);
        } catch (Exception e) {
            debug.warning("Failed to register command: " + name + " - " + e.getMessage());
        }
    }

    /**
     * Registers a command with the server
     */
    private void registerCommand(String commandName, CommandExecutor executor) {
        debug.debug("Registering command: " + commandName);
        PluginCommand command = plugin.getCommand(commandName);
        
        if (command == null) {
            debug.warning("Failed to register command: " + commandName + " (not found in plugin.yml)");
            return;
        }
        
        command.setExecutor(executor);
        
        if (executor instanceof TabCompleter) {
            command.setTabCompleter((TabCompleter) executor);
            debug.debug("Tab completer registered for command: " + commandName);
        }
        
        commands.put(commandName, executor);
        debug.debug("Command registered: " + commandName);
    }

    /**
     * Gets a registered command executor
     */
    public CommandExecutor getCommand(String commandName) {
        return commands.get(commandName);
    }
}