package org.fourz.RVNKQuests.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.util.Debug;

import java.util.Arrays;
import java.util.Collections;
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
        // Check if this is a debug reload
        boolean debugReload = args.length > 0 && "debug".equalsIgnoreCase(args[0]);
        
        if (debugReload) {
            sender.sendMessage(ChatColor.RED + "Performing debug reload - resetting all quests and reloading configuration...");
            debug.warning("Debug reload initiated by " + sender.getName() + " - all quests will be reset");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Reloading RVNKQuests configuration...");
        }
        
        try {
            // First reload the configuration
            plugin.getConfigManager().reloadConfig();
            
            // Update the log level based on new config using the global method
            Level newLogLevel = plugin.getConfigManager().getLogLevel();
            plugin.updateGlobalLogLevel(newLogLevel);
            
            // If this is a debug reload, reset all quests
            if (debugReload) {
                resetQuests(sender);
            }
            
            debug.info("Configuration reloaded by " + sender.getName() + (debugReload ? " with quest reset" : ""));
            sender.sendMessage(ChatColor.GREEN + "Configuration reloaded successfully!");
            sender.sendMessage(ChatColor.YELLOW + "Current log level: " + 
                ChatColor.GREEN + getLevelName(newLogLevel));
        } catch (Exception e) {
            debug.error("Error during " + (debugReload ? "debug reload" : "reload"), e);
            sender.sendMessage(ChatColor.RED + "Error reloading configuration: " + e.getMessage());
        }
        
        return true;
    }
    
    /**
     * Reset all quests in the quest manager
     * @param sender The command sender to receive feedback messages
     */
    private void resetQuests(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Resetting all quests...");
        
        try {
            // Clean up all existing quests first
            plugin.getQuestManager().cleanupQuests();
            
            // Reinitialize quests as if plugin was restarted
            plugin.getQuestManager().initializeQuests();
            
            // TODO: In future development, reset player quest progress in database
            
            sender.sendMessage(ChatColor.GREEN + "All quests have been reset!");
            debug.info("Quest reset completed by " + sender.getName());
        } catch (Exception e) {
            debug.error("Error resetting quests", e);
            sender.sendMessage(ChatColor.RED + "Error resetting quests: " + e.getMessage());
        }
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
        return "Reload the plugin configuration. Add 'debug' to reset all quests.";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnkquests.admin") || sender.isOp();
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        // Return "debug" as a tab completion option for the first argument
        if (args.length == 1) {
            return Collections.singletonList("debug");
        }
        return Collections.emptyList();
    }
}
