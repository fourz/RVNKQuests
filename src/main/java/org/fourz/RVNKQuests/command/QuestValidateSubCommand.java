package org.fourz.RVNKQuests.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.bukkit.event.Listener;

import java.util.Collections;
import java.util.List;

/**
 * Handles the /quest validate command to validate quest configurations.
 * Extends BaseSubCommand to provide standardized subcommand functionality.
 */
public class QuestValidateSubCommand extends BaseSubCommand {

    public QuestValidateSubCommand(RVNKQuests plugin) {
        super(plugin, "validate", "Validate all quest configurations", 
              "/quest validate", "rvnkquests.admin.validate", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        sendInfoMessage(sender, "Validating all quests...");
        
        try {
            boolean allValid = validateAllQuests(sender);
            
            if (allValid) {
                sendSuccessMessage(sender, "All quests validated successfully!");
            } else {
                sendErrorMessage(sender, "Some quests failed validation. Check the server logs for details.");
            }
            
            logger.info("Quest validation performed by " + sender.getName() + " - Result: " + (allValid ? "Valid" : "Invalid"));
        } catch (Exception e) {
            logger.error("Error during quest validation", e);
            sendErrorMessage(sender, "Error validating quests: " + e.getMessage());
        }
        
        return true;
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnkquests.admin.validate") || sender.isOp();
    }
    
    private boolean validateAllQuests(CommandSender sender) {
        boolean allValid = true;
        List<Quest> quests = plugin.getQuestManager().getAllQuests();
        
        sender.sendMessage(ChatColor.YELLOW + "Found " + quests.size() + " quests to validate...");
        
        for (Quest quest : quests) {
            try {
                boolean questValid = validateQuest(quest);
                
                String statusColor = questValid ? ChatColor.GREEN.toString() : ChatColor.RED.toString();
                String status = questValid ? "VALID" : "INVALID";
                
                sender.sendMessage(
                    statusColor + "[" + status + "] " +
                    ChatColor.YELLOW + quest.getName() + 
                    ChatColor.GRAY + " (" + quest.getId() + ")"
                );
                
                if (!questValid) {
                    allValid = false;
                }
            } catch (Exception e) {
                logger.error("Exception validating quest: " + quest.getId(), e);
                sender.sendMessage(ChatColor.RED + "[ERROR] " + 
                    ChatColor.YELLOW + quest.getName() + 
                    ChatColor.GRAY + " (" + quest.getId() + "): " + e.getMessage());
                allValid = false;
            }
        }
        
        return allValid;
    }
    
    private boolean validateQuest(Quest quest) {
    logger.debug("Validating quest: " + quest.getId());
        boolean valid = true;
        
        // Basic validation
        if (quest.getId() == null || quest.getId().isEmpty()) {
            logger.warning("Quest has null or empty ID");
            valid = false;
        }
        
        if (quest.getName() == null || quest.getName().isEmpty()) {
            logger.warning("Quest has null or empty name: " + quest.getId());
            valid = false;
        }
        
        // Check listener creation for each state
        for (QuestState state : QuestState.values()) {
            try {
                List<Listener> listeners = quest.createListenersForState(state);
                if (listeners == null) {
                    logger.warning("" + quest.getId() + " returned null listeners for state: " + state + "");
                    valid = false;
                }
            } catch (Exception e) {
                logger.error("Error creating listeners for quest " + quest.getId() + " state " + state, e);
                valid = false;
            }
        }
        
    logger.debug("Quest validation result for " + quest.getId() + ": " + (valid ? "Valid" : "Invalid"));
        return valid;
    }
}
