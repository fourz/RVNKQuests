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
        registerCommands();
    }

    /**
     * Registers all commands for the plugin
     */
    private void registerCommands() {
        debug.debug("Registering commands...");
        registerCommand("quest", new QuestCommand(plugin));
        debug.debug("Commands registered successfully");
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
