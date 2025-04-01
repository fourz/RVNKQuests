package org.fourz.RVNKQuests.trigger;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestPiglinFarFromHome;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.util.Debug;

import java.util.Random;
import java.util.logging.Level;

/**
 * Handles the execution of quest trigger commands.
 */
public class TriggerCommand {
    private final RVNKQuests plugin;
    private final Debug debug;
    private final Random random = new Random();
    private static final int AROUND_RADIUS = 40;

    public TriggerCommand(RVNKQuests plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "TriggerCommand", Level.FINE);
    }

    /**
     * Triggers a quest for a player at the specified location type.
     *
     * @param player The player triggering the quest
     * @param questId The ID of the quest to trigger
     * @param locationType "here" or "around" for location handling
     * @return true if successful, false otherwise
     */
    public boolean triggerQuest(Player player, String questId, String locationType) {
        Quest quest = plugin.getQuestManager().getQuest(questId);
        
        if (quest == null) {
            debug.warning("Quest not found: " + questId);
            player.sendMessage(ChatColor.RED + "Quest not found: " + questId);
            return false;
        }

        // Calculate the trigger location based on the type
        Location triggerLocation = getLocationForType(player.getLocation(), locationType);
        
        debug.debug("Triggering quest " + questId + " for player " + player.getName() + 
                    " at " + triggerLocation.getWorld().getName() + " " + 
                    triggerLocation.getBlockX() + "," + 
                    triggerLocation.getBlockY() + "," + 
                    triggerLocation.getBlockZ());

        // Handle quest-specific triggering
        boolean success = handleQuestTrigger(quest, player, triggerLocation);
        
        if (success) {
            player.sendMessage(ChatColor.GREEN + "Successfully triggered quest: " + quest.getName());
            player.sendMessage(ChatColor.GRAY + "Location: " + formatLocation(triggerLocation));
        }
        
        return success;
    }

    /**
     * Handles quest-specific trigger implementations
     */
    private boolean handleQuestTrigger(Quest quest, Player player, Location location) {
        // Special handling for different quest types
        if (quest instanceof QuestPiglinFarFromHome) {
            QuestPiglinFarFromHome piglinQuest = (QuestPiglinFarFromHome) quest;
            
            // Only set the spawn location if the quest is not already started
            if (quest.getCurrentState() == QuestState.NOT_STARTED) {
                // This allows the spawner listeners to use this location
                piglinQuest.setSpawnLocation(location);
                quest.advanceState(QuestState.TRIGGER_FOUND);
                debug.debug("Set piglin quest spawn location and advanced state to TRIGGER_FOUND");
                return true;
            } else {
                debug.debug("Piglin quest already started, current state: " + quest.getCurrentState());
                player.sendMessage(ChatColor.YELLOW + "Quest is already in progress (state: " + quest.getCurrentState() + ")");
                return false;
            }
        } else {
            // Generic handling for other quest types
            if (quest.getCurrentState() == QuestState.NOT_STARTED) {
                quest.advanceState(QuestState.TRIGGER_FOUND);
                debug.debug("Advanced generic quest state to TRIGGER_FOUND");
                return true;
            } else {
                debug.debug("Quest already started, current state: " + quest.getCurrentState());
                player.sendMessage(ChatColor.YELLOW + "Quest is already in progress (state: " + quest.getCurrentState() + ")");
                return false;
            }
        }
    }

    /**
     * Calculates the location based on the location type.
     */
    private Location getLocationForType(Location playerLocation, String locationType) {
        Location result = playerLocation.clone();
        
        if (locationType.equals("around")) {
            // Generate a random location within AROUND_RADIUS blocks
            int xOffset = random.nextInt(AROUND_RADIUS * 2) - AROUND_RADIUS;
            int zOffset = random.nextInt(AROUND_RADIUS * 2) - AROUND_RADIUS;
            
            result.add(xOffset, 0, zOffset);
            
            // Set Y to the highest block at that X,Z coordinate
            result.setY(playerLocation.getWorld().getHighestBlockYAt(result));
        }
        
        return result;
    }

    /**
     * Formats a location for display to players.
     */
    private String formatLocation(Location location) {
        return location.getWorld().getName() + " (" + 
               location.getBlockX() + ", " + 
               location.getBlockY() + ", " + 
               location.getBlockZ() + ")";
    }
}
