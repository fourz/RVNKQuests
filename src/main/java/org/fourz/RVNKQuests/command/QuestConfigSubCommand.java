package org.fourz.RVNKQuests.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.Quest;
// import org.fourz.RVNKQuests.config.ConfigManager; // not used here

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the /quest config command for modifying the plugin configuration.
 * Extends BaseSubCommand to provide standardized subcommand functionality.
 */
public class QuestConfigSubCommand extends BaseSubCommand {
    private static final List<String> VALID_OPERATIONS = Arrays.asList("disable", "enable", "list");
    
    public QuestConfigSubCommand(RVNKQuests plugin) {
        super(plugin, "config", "Modify quest configuration (enable/disable quests)", 
              "/quest config <operation> [quest_id|all]", "rvnkquests.admin", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Please specify an operation: " + String.join(", ", VALID_OPERATIONS));
            return true;
        }
        
        String operation = args[0].toLowerCase();
        if (!VALID_OPERATIONS.contains(operation)) {
            sender.sendMessage(ChatColor.RED + "Invalid operation. Valid operations: " + String.join(", ", VALID_OPERATIONS));
            return true;
        }
        
        switch (operation) {
            case "disable":
                return handleDisable(sender, args);
            case "enable":
                return handleEnable(sender, args);
            case "list":
                return handleList(sender, args);
            default:
                return true;
        }
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return VALID_OPERATIONS.stream()
                .filter(op -> op.startsWith(partial))
                .collect(Collectors.toList());
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable"))) {
            String partial = args[1].toLowerCase();
            List<String> options = plugin.getQuestManager().getQuestIds().stream()
                .filter(id -> id.startsWith(partial))
                .collect(Collectors.toList());
            if ("all".startsWith(partial)) {
                options.add("all");
            }
            return options;
        }
        return Collections.emptyList();
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnkquests.admin") || sender.isOp();
    }
    
    private boolean handleDisable(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Please specify a quest ID or 'all'");
            return true;
        }
        
        String questId = args[1].toLowerCase();
        
        if ("all".equals(questId)) {
            // Disable all quests
            List<String> questIds = plugin.getQuestManager().getQuestIds();
            int disabledCount = 0;
            
            for (String id : questIds) {
                if (setQuestEnabled(id, false)) {
                    disabledCount++;
                }
            }
            
            sender.sendMessage(ChatColor.YELLOW + "Disabled " + disabledCount + " quests in configuration.");
            sender.sendMessage(ChatColor.YELLOW + "Use '/quest reload' to apply changes.");
            return true;
        } else {
            // Check if the quest exists
            Quest quest = plugin.getQuestManager().getQuest(questId).orElse(null);
            if (quest == null) {
                sender.sendMessage(ChatColor.RED + "Quest not found: " + questId);
                return true;
            }
            
            // Disable specific quest
            if (setQuestEnabled(questId, false)) {
                sender.sendMessage(ChatColor.YELLOW + "Disabled quest: " + quest.getName() + " (" + questId + ")");
                sender.sendMessage(ChatColor.YELLOW + "Use '/quest reload' to apply changes.");
            } else {
                sender.sendMessage(ChatColor.RED + "Failed to update configuration for quest: " + questId);
            }
            return true;
        }
    }
    
    private boolean handleEnable(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Please specify a quest ID or 'all'");
            return true;
        }
        
        String questId = args[1].toLowerCase();
        
        if ("all".equals(questId)) {
            // Enable all quests
            List<String> questIds = plugin.getQuestManager().getQuestIds();
            int enabledCount = 0;
            
            for (String id : questIds) {
                if (setQuestEnabled(id, true)) {
                    enabledCount++;
                }
            }
            
            sender.sendMessage(ChatColor.GREEN + "Enabled " + enabledCount + " quests in configuration.");
            sender.sendMessage(ChatColor.YELLOW + "Use '/quest reload' to apply changes.");
            return true;
        } else {
            // Check if the quest exists
            Quest quest = plugin.getQuestManager().getQuest(questId).orElse(null);
            if (quest == null) {
                sender.sendMessage(ChatColor.RED + "Quest not found: " + questId);
                return true;
            }
            
            // Enable specific quest
            if (setQuestEnabled(questId, true)) {
                sender.sendMessage(ChatColor.GREEN + "Enabled quest: " + quest.getName() + " (" + questId + ")");
                sender.sendMessage(ChatColor.YELLOW + "Use '/quest reload' to apply changes.");
            } else {
                sender.sendMessage(ChatColor.RED + "Failed to update configuration for quest: " + questId);
            }
            return true;
        }
    }
    
    private boolean handleList(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.GOLD + "===== Quest Configuration =====");
        
        List<String> questIds = plugin.getQuestManager().getQuestIds();
        if (questIds.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No quests are currently registered.");
            return true;
        }
        
        for (String questId : questIds) {
            Quest quest = plugin.getQuestManager().getQuest(questId).orElse(null);
            if (quest != null) {
                boolean enabled = isQuestEnabled(questId);
                String statusColor = enabled ? ChatColor.GREEN.toString() : ChatColor.RED.toString();
                String status = enabled ? "ENABLED" : "DISABLED";
                
                sender.sendMessage(
                    statusColor + "[" + status + "] " +
                    ChatColor.YELLOW + quest.getName() + 
                    ChatColor.GRAY + " (" + questId + ")"
                );
            }
        }
        
        return true;
    }
    
    private boolean setQuestEnabled(String questId, boolean enabled) {
        try {
            String path = "quests." + questId + ".enable";
            plugin.getConfigManager().getConfig().set(path, enabled);
            plugin.getConfigManager().saveConfig();
            logger.debug("Set quest " + questId + " enabled status to: " + enabled);
            return true;
        } catch (Exception e) {
            logger.error("Failed to update quest enabled status", e);
            return false;
        }
    }
    
    private boolean isQuestEnabled(String questId) {
        return plugin.getConfigManager().getConfig().getBoolean("quests." + questId + ".enable", true);
    }
}
