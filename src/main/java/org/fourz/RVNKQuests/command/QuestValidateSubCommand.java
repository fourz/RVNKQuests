package org.fourz.RVNKQuests.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.util.Debug;

import java.util.Collections;
import java.util.List;

/**
 * Handles the /quest validate command to validate quest configurations
 */
public class QuestValidateSubCommand implements SubCommand {
    private final RVNKQuests plugin;
    private final Debug debug;

    public QuestValidateSubCommand(RVNKQuests plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "QuestValidateCommand", plugin.getDebugger().getLogLevel());
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.YELLOW + "Validating all quests...");
        
        try {
            boolean valid = plugin.getQuestManager().validateQuests();
            
            if (valid) {
                sender.sendMessage(ChatColor.GREEN + "All quests validated successfully!");
            } else {
                sender.sendMessage(ChatColor.RED + "Some quests failed validation. Check the server logs for details.");
            }
            
            debug.info("Quest validation performed by " + sender.getName() + " - Result: " + (valid ? "Valid" : "Invalid"));
        } catch (Exception e) {
            debug.error("Error during quest validation", e);
            sender.sendMessage(ChatColor.RED + "Error validating quests: " + e.getMessage());
        }
        
        return true;
    }

    @Override
    public String getDescription() {
        return "Validate all quest configurations";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnkquests.admin") || sender.isOp();
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
