package org.fourz.RVNKQuests.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.util.Debug;

import java.util.List;
import java.util.logging.Level;

/**
 * Handles the /quest reload command to reload plugin configuration
 */
public class QuestReloadSubCommand implements SubCommand {
    private final RVNKQuests plugin;
    private final Debug debug;

    public QuestReloadSubCommand(RVNKQuests plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "QuestReloadCommand", plugin.getDebugger().getLogLevel());
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.YELLOW + "Reloading RVNKQuests configuration...");
        
        try {
            // Reload the configuration
            plugin.getConfigManager().reloadConfig();
            
            // Update the log level based on new config using the global method
            Level newLogLevel = plugin.getConfigManager().getLogLevel();
            plugin.updateGlobalLogLevel(newLogLevel);
            
            debug.info("Configuration reloaded by " + sender.getName());
            sender.sendMessage(ChatColor.GREEN + "Configuration reloaded successfully!");
            sender.sendMessage(ChatColor.YELLOW + "Current log level: " + 
                ChatColor.GREEN + getLevelName(newLogLevel));
        } catch (Exception e) {
            debug.error("Error reloading configuration", e);
            sender.sendMessage(ChatColor.RED + "Error reloading configuration: " + e.getMessage());
        }
        
        return true;
    }
    
    private String getLevelName(Level level) {
        if (level == Level.FINE) {
            return "DEBUG";
        } else if (level == Level.INFO) {
            return "INFO";
        } else if (level == Level.WARNING) {
            return "WARNING";
        } else if (level == Level.SEVERE) {
            return "SEVERE";
        } else if (level == Level.OFF) {
            return "OFF";
        }
        return level.getName();
    }

    @Override
    public String getDescription() {
        return "Reload the plugin configuration";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnkquests.admin") || sender.isOp();
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        return null; // No completions for this command
    }
}
