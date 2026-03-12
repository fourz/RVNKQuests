package org.fourz.RVNKQuests.trigger;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Random;

/**
 * Handles the execution of quest trigger commands.
 * Advances any quest from NOT_STARTED to TRIGGER_FOUND for a player.
 */
public class TriggerCommand {
    private final RVNKQuests plugin;
    private final LogManager logger;
    private final Random random = new Random();
    private static final int AROUND_RADIUS = 40;

    public TriggerCommand(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
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
        Quest quest = plugin.getQuestManager().getQuest(questId).orElse(null);

        if (quest == null) {
            logger.warning("Quest not found: " + questId);
            player.sendMessage(ChatColor.RED + "Quest not found: " + questId);
            return false;
        }

        Location triggerLocation = getLocationForType(player.getLocation(), locationType);

        logger.debug("Triggering quest " + questId + " for player " + player.getName() +
                    " at " + triggerLocation.getBlockX() + "," + triggerLocation.getBlockY() + "," + triggerLocation.getBlockZ() +
                    " in world " + triggerLocation.getWorld().getName());

        if (quest.getStateForPlayer(player) == QuestState.NOT_STARTED) {
            quest.advanceStateForPlayer(player.getUniqueId(), QuestState.TRIGGER_FOUND);
            player.sendMessage(ChatColor.GREEN + "Successfully triggered quest: " + quest.getName());
            player.sendMessage(ChatColor.GRAY + "Location: " + formatLocation(triggerLocation));
            return true;
        }

        QuestState currentState = quest.getStateForPlayer(player);
        logger.debug("Quest already started, current state: " + currentState);
        player.sendMessage(ChatColor.YELLOW + "Quest is already in progress (state: " + currentState + ")");
        return false;
    }

    private Location getLocationForType(Location playerLocation, String locationType) {
        Location result = playerLocation.clone();

        if (locationType.equals("around")) {
            int xOffset = random.nextInt(AROUND_RADIUS * 2) - AROUND_RADIUS;
            int zOffset = random.nextInt(AROUND_RADIUS * 2) - AROUND_RADIUS;
            result.add(xOffset, 0, zOffset);
            result.setY(playerLocation.getWorld().getHighestBlockYAt(result));
        }

        return result;
    }

    private String formatLocation(Location location) {
        return location.getWorld().getName() + " (" +
               location.getBlockX() + ", " +
               location.getBlockY() + ", " +
               location.getBlockZ() + ")";
    }
}
