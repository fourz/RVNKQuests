package org.fourz.RVNKQuests.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.util.Debug;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Subcommand for reloading the plugin and resetting all quests
 * Usage: /quest reload
 */
public class QuestReloadSubCommand implements SubCommand {
    private final RVNKQuests plugin;
    private final Debug debug;

    public QuestReloadSubCommand(RVNKQuests plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "QuestReloadCommand", Level.FINE);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        debug.debug("Executing reload command");
        sender.sendMessage(ChatColor.YELLOW + "Reloading RVNKQuests plugin and resetting all quests...");
        
        // Clean up existing quests
        plugin.getQuestManager().cleanupQuests();
        
        // Reload configuration
        plugin.getConfigManager().reloadConfig();
        debug.setLogLevel(plugin.getConfigManager().getLogLevel());
        
        // Re-initialize quests
        plugin.getQuestManager().initializeQuests();
        
        sender.sendMessage(ChatColor.GREEN + "RVNKQuests plugin has been reloaded successfully!");
        return true;
    }

    @Override
    public String getDescription() {
        return "Reloads the plugin and resets all quests";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnkquests.command.reload") || sender.isOp();
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        // No additional arguments for reload command
        return new ArrayList<>();
    }
}
