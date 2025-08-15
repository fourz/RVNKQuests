package org.fourz.RVNKQuests.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Handles the /quest reload command to reload plugin configuration.
 * Extends BaseSubCommand to provide standardized subcommand functionality.
 */
public class QuestReloadSubCommand extends BaseSubCommand {

    public QuestReloadSubCommand(RVNKQuests plugin) {
        super(plugin, "reload", "Reload the plugin configuration. Add 'reset' to reset all quests.", 
              "/quest reload [reset]", "rvnkquests.admin", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        // Check if this is a reset reload
        boolean resetReload = args.length > 0 && "reset".equalsIgnoreCase(args[0]);
        
        if (resetReload) {
            sendErrorMessage(sender, "Performing reset reload - resetting all quests and reloading configuration...");
            logger.warning("Reset reload initiated by " + sender.getName() + " - all quests will be reset");
        } else {
            sendInfoMessage(sender, "Reloading RVNKQuests configuration...");
        }
        
        try {
            // First reload the configuration - this will update the quest enable status
            plugin.getConfigManager().reloadConfig();
            
            // Display quest status after reload
            if (resetReload) {
                displayQuestStatus(sender);
            }
            
            // Update the log level based on new config using the global method
            Level newLogLevel = plugin.getConfigManager().getLogLevel();
            plugin.updateGlobalLogLevel(newLogLevel);
            
            // If this is a reset reload, reset all quests
            if (resetReload) {
                resetQuests(sender);
            }
            
            logger.info("Configuration reloaded by " + sender.getName() + " " + (resetReload ? "with quest reset" : ""));
            sendSuccessMessage(sender, "Configuration reloaded successfully!");
            sendInfoMessage(sender, "Current log level: " + ChatColor.GREEN + getLevelName(newLogLevel));
        } catch (Exception e) {
            logger.error("Error during " + (resetReload ? "reset reload" : "reload"), e);
            sendErrorMessage(sender, "Error reloading configuration: " + e.getMessage());
        }
        
        return true;
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        // Return "reset" as a tab completion option for the first argument
        if (args.length == 1) {
            return Collections.singletonList("reset");
        }
        return Collections.emptyList();
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnkquests.admin") || sender.isOp();
    }
    
    /**
     * Display the status of all quests to the sender
     * @param sender The command sender
     */
    private void displayQuestStatus(CommandSender sender) {
        Map<String, Boolean> questStatus = plugin.getConfigManager().getQuestEnableStatus();
        
        if (questStatus.isEmpty()) {
            sendInfoMessage(sender, "No quest configuration found.");
            return;
        }
        
        sendInfoMessage(sender, "Quest Status:");
        
        for (Map.Entry<String, Boolean> entry : questStatus.entrySet()) {
            String questId = entry.getKey();
            boolean enabled = entry.getValue();
            
            if (enabled) {
                sender.sendMessage(ChatColor.GREEN + "  ✓ " + questId + ": Enabled");
            } else {
                sender.sendMessage(ChatColor.RED + "  ✗ " + questId + ": Disabled");
            }
        }
    }
    
    /**
     * Reset all quests in the quest manager
     * @param sender The command sender to receive feedback messages
     */
    private void resetQuests(CommandSender sender) {
        sendInfoMessage(sender, "Resetting all quests...");
        
        try {
            // Clean up all existing quests first
            plugin.getQuestManager().cleanupQuests();
            
            // Reinitialize quests as if plugin was restarted
            plugin.getQuestManager().initializeQuests();
            
            // TODO: In future development, reset player quest progress in database
            
            sendSuccessMessage(sender, "All quests have been reset!");
            logger.info("Quest reset completed by " + sender.getName());
        } catch (Exception e) {
            logger.error("Error resetting quests", e);
            sendErrorMessage(sender, "Error resetting quests: " + e.getMessage());
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
}
