package org.fourz.RVNKQuests.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.objective.ListenerEncounterPortal;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.util.LogManager;
import org.fourz.RVNKQuests.util.RVNKLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the /quest mobs command to manage quest mobs
 */
public class QuestMobsSubCommand implements SubCommand {
    private final RVNKQuests plugin;
    private final RVNKLogger logger;
    private final List<String> VALID_OPERATIONS = Arrays.asList("kill", "list");

    public QuestMobsSubCommand(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Please specify an operation: kill, list");
            return true;
        }
        
        String operation = args[0].toLowerCase();
        
        if (!VALID_OPERATIONS.contains(operation)) {
            sender.sendMessage(ChatColor.RED + "Unknown operation: " + operation);
            sender.sendMessage(ChatColor.RED + "Valid operations: " + String.join(", ", VALID_OPERATIONS));
            return true;
        }

        if (operation.equals("kill")) {
            return killQuestMobs(sender);
        } else if (operation.equals("list")) {
            return listQuestMobs(sender);
        }
        
        return true;
    }
    
    /**
     * Kills all tracked quest mobs
     */
    private boolean killQuestMobs(CommandSender sender) {
        int killedCount = 0;
        
        // Loop through all active quests and find encounter portals
        for (Quest quest : plugin.getQuestManager().getAllQuests()) {
            try {
                // Try to find ListenerEncounterPortal among quest listeners
                for (ListenerEncounterPortal portalListener : findPortalListeners(quest)) {
                    List<Entity> mobs = portalListener.getSpawnedMobs();
                    
                    if (mobs != null && !mobs.isEmpty()) {
                        for (Entity mob : new ArrayList<>(mobs)) {
                            if (mob != null && mob.isValid()) {
                                String mobName = mob.getCustomName() != null ? mob.getCustomName() : mob.getType().toString();
                                logger.debug("Removing quest mob: {}", mobName);
                                mob.remove();
                                killedCount++;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Error killing mobs for quest: {}", quest.getId(), e);
            }
        }
        
        if (killedCount > 0) {
            sender.sendMessage(ChatColor.GREEN + "Successfully removed " + killedCount + " quest mobs.");
            logger.info("Admin {} removed {} quest mobs", sender.getName(), killedCount);
        } else {
            sender.sendMessage(ChatColor.YELLOW + "No active quest mobs found to remove.");
        }
        
        return true;
    }
    
    /**
     * Lists all tracked quest mobs
     */
    private boolean listQuestMobs(CommandSender sender) {
        int totalCount = 0;
        
        sender.sendMessage(ChatColor.GOLD + "===== Active Quest Mobs =====");
        
        // Loop through all active quests and find encounter portals
        for (Quest quest : plugin.getQuestManager().getAllQuests()) {
            try {
                for (ListenerEncounterPortal portalListener : findPortalListeners(quest)) {
                    List<Entity> mobs = portalListener.getSpawnedMobs();
                    
                    if (mobs != null && !mobs.isEmpty()) {
                        sender.sendMessage(ChatColor.YELLOW + "Quest: " + 
                                          ChatColor.WHITE + quest.getName() + 
                                          ChatColor.GRAY + " (" + mobs.size() + " mobs)");
                        
                        for (Entity mob : mobs) {
                            if (mob != null && mob.isValid()) {
                                String mobName = mob.getCustomName() != null ? mob.getCustomName() : mob.getType().toString();
                                sender.sendMessage(ChatColor.GRAY + " - " + 
                                                 ChatColor.WHITE + mobName + 
                                                 ChatColor.GRAY + " at " + 
                                                 formatLocation(mob.getLocation()));
                                totalCount++;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Error listing mobs for quest: {}", quest.getId(), e);
            }
        }
        
        if (totalCount == 0) {
            sender.sendMessage(ChatColor.YELLOW + "No active quest mobs found.");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Total quest mobs: " + totalCount);
        }
        
        return true;
    }
    
    /**
     * Helper method to find all EncounterPortal listeners in a quest
     */
    private List<ListenerEncounterPortal> findPortalListeners(Quest quest) {
        List<ListenerEncounterPortal> result = new ArrayList<>();
        
        try {
            // This is a bit of a hack but we're looking through active listeners for the quest
            // and finding any that are EncounterPortal listeners
            Object listener = quest.getClass().getDeclaredField("portalListener").get(quest);
            if (listener instanceof ListenerEncounterPortal) {
                result.add((ListenerEncounterPortal) listener);
            }
        } catch (Exception e) {
            // Not all quests will have this field, so ignore exceptions
        }
        
        return result;
    }
    
    /**
     * Format location for display
     */
    private String formatLocation(org.bukkit.Location loc) {
        if (loc == null) return "unknown";
        return String.format("%s (%d,%d,%d)", 
            loc.getWorld().getName(),
            loc.getBlockX(),
            loc.getBlockY(),
            loc.getBlockZ());
    }

    @Override
    public String getDescription() {
        return "Manage quest mobs (kill, list)";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnkquests.admin") || sender.isOp();
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return VALID_OPERATIONS.stream()
                .filter(op -> op.startsWith(partial))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
