package org.fourz.RVNKQuests.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.util.log.LogManager;
import org.fourz.RVNKQuests.util.log.FZLogger;
import org.bukkit.event.Listener;

import java.util.Collections;
import java.util.List;

/**
 * Handles the /quest validate command to validate quest configurations
 */
public class QuestValidateSubCommand implements SubCommand {
    private final RVNKQuests plugin;
    private final FZLogger logger;

    public QuestValidateSubCommand(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.YELLOW + "Validating all quests...");
        
        try {
            boolean allValid = validateAllQuests(sender);
            
            if (allValid) {
                sender.sendMessage(ChatColor.GREEN + "All quests validated successfully!");
            } else {
                sender.sendMessage(ChatColor.RED + "Some quests failed validation. Check the server logs for details.");
            }
            
            logger.info("Quest validation performed by {} - Result: {}", sender.getName(), (allValid ? "Valid" : "Invalid"));
        } catch (Exception e) {
            logger.error("Error during quest validation", e);
            sender.sendMessage(ChatColor.RED + "Error validating quests: " + e.getMessage());
        }
        
        return true;
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
                logger.error("Exception validating quest: {}", quest.getId(), e);
                sender.sendMessage(ChatColor.RED + "[ERROR] " + 
                    ChatColor.YELLOW + quest.getName() + 
                    ChatColor.GRAY + " (" + quest.getId() + "): " + e.getMessage());
                allValid = false;
            }
        }
        
        return allValid;
    }
    
    private boolean validateQuest(Quest quest) {
        logger.debug("Validating quest: {}", quest.getId());
        boolean valid = true;
        
        // Basic validation
        if (quest.getId() == null || quest.getId().isEmpty()) {
            logger.warning("Quest has null or empty ID");
            valid = false;
        }
        
        if (quest.getName() == null || quest.getName().isEmpty()) {
            logger.warning("Quest has null or empty name: {}", quest.getId());
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
                logger.error("Error creating listeners for quest {} state {}", quest.getId(), state, e);
                valid = false;
            }
        }
        
        logger.debug("Quest validation result for {}: {}", quest.getId(), (valid ? "Valid" : "Invalid"));
        return valid;
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
