package org.fourz.RVNKQuests.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.fourz.RVNKQuests.RVNKQuests;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Handles the /quest debug command which can change the log level at runtime.
 * Extends BaseSubCommand to provide standardized subcommand functionality.
 */
public class QuestDebugSubCommand extends BaseSubCommand {
    private static final List<String> VALID_LEVELS = Arrays.asList(
        "debug", "info", "warning", "severe", "off"
    );

    public QuestDebugSubCommand(RVNKQuests plugin) {
        super(plugin, "debug", "View or change the plugin's debug level", 
              "/quest debug [level]", "rvnkquests.admin", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            // Show current debug level from config
            Level currentLevel = plugin.getConfigManager().getLogLevel();
            sender.sendMessage(ChatColor.YELLOW + "Current debug level: " + 
                ChatColor.GREEN + getLevelName(currentLevel));
            sender.sendMessage(ChatColor.YELLOW + "Usage: /quest debug [level]");
            sender.sendMessage(ChatColor.YELLOW + "Valid levels: " + 
                String.join(", ", VALID_LEVELS));
            return true;
        }

        String levelArg = args[0].toLowerCase();
        if (!VALID_LEVELS.contains(levelArg)) {
            sendErrorMessage(sender, "Invalid log level: " + levelArg);
            sendInfoMessage(sender, "Valid levels: " + String.join(", ", VALID_LEVELS));
            return true;
        }

        // Convert string to Level
        Level newLevel = getLevel(levelArg);
        
        // Update config
        FileConfiguration config = plugin.getConfig();
        config.set("general.logLevel", levelArg.toUpperCase());
        plugin.saveConfig();
        
        // Update runtime debug level
        updateLogLevel(newLevel);
        
        logger.info("Log level changed to " + newLevel.getName() + " by " + sender.getName());
        sendSuccessMessage(sender, "Log level set to: " + levelArg);
        
        return true;
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return VALID_LEVELS.stream()
                .filter(level -> level.startsWith(partial))
                .collect(Collectors.toList());
        }
        return super.getTabCompletionOptions(sender, args);
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnkquests.admin") || sender.isOp();
    }

    private void updateLogLevel(Level newLevel) {
        // Update entire plugin's log level using the global method
        plugin.updateGlobalLogLevel(newLevel);
        
        // Reload config to ensure all new debuggers get correct level
        plugin.getConfigManager().reloadConfig();
        
        // Log the change at the new level
        logger.info("Log level changed globally to: " + newLevel.getName());
    }
    
    private Level getLevel(String levelStr) {
        if (levelStr.equalsIgnoreCase("debug")) {
            return Level.FINE;
        } else if (levelStr.equalsIgnoreCase("info")) {
            return Level.INFO;
        } else if (levelStr.equalsIgnoreCase("warning")) {
            return Level.WARNING;
        } else if (levelStr.equalsIgnoreCase("severe")) {
            return Level.SEVERE;
        } else if (levelStr.equalsIgnoreCase("off")) {
            return Level.OFF;
        }
        return Level.INFO; // Default
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
