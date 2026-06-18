package org.fourz.RVNKQuests.trigger;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Random;

/**
 * Handles the execution of quest trigger commands.
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
     * Trigger a quest for a specific target player.
     *
     * @param sender  Admin/console issuing the command (receives feedback)
     * @param target  Player to trigger the quest for
     * @param questId Quest ID
     * @param force   If true, bypass required_state check and advance to QUEST_ACTIVE regardless
     * @return true if the quest was advanced
     */
    public boolean triggerQuestForPlayer(CommandSender sender, Player target, String questId, boolean force) {
        Quest quest = plugin.getQuestManager().getQuest(questId).orElse(null);
        if (quest == null) {
            sender.sendMessage(ChatColor.RED + "Quest not found: " + questId);
            logger.warning("trigger: quest not found: " + questId);
            return false;
        }

        QuestState currentState = quest.getStateForPlayer(target);

        if (!force && currentState != QuestState.NOT_STARTED) {
            sender.sendMessage(ChatColor.YELLOW + target.getName() + " is already on quest '"
                    + questId + "' (state: " + currentState + "). Use --force to override.");
            return false;
        }

        QuestState advanceTo = force ? QuestState.QUEST_ACTIVE : QuestState.TRIGGER_FOUND;
        quest.advanceStateForPlayer(target.getUniqueId(), advanceTo);

        String forceNote = force ? " [--force → " + advanceTo + "]" : "";
        sender.sendMessage(ChatColor.GREEN + "Triggered '" + questId + "' for " + target.getName() + forceNote);
        logger.debug("trigger: '" + questId + "' → " + advanceTo + " for " + target.getName()
                + " by " + sender.getName() + (force ? " (forced)" : ""));
        return true;
    }

    /**
     * Legacy: trigger for the sender player at a location (kept for backward compat).
     */
    public boolean triggerQuest(Player player, String questId, String locationType) {
        Quest quest = plugin.getQuestManager().getQuest(questId).orElse(null);
        if (quest == null) {
            logger.warning("Quest not found: " + questId);
            player.sendMessage(ChatColor.RED + "Quest not found: " + questId);
            return false;
        }

        Location triggerLocation = getLocationForType(player.getLocation(), locationType);
        logger.debug("Triggering quest " + questId + " for player " + player.getName()
                + " at " + triggerLocation.getBlockX() + "," + triggerLocation.getBlockY()
                + "," + triggerLocation.getBlockZ()
                + " in world " + triggerLocation.getWorld().getName());

        QuestState currentState = quest.getStateForPlayer(player);
        if (currentState == QuestState.NOT_STARTED) {
            quest.advanceStateForPlayer(player.getUniqueId(), QuestState.TRIGGER_FOUND);
            player.sendMessage(ChatColor.GREEN + "Successfully triggered quest: " + quest.getName());
            player.sendMessage(ChatColor.GRAY + "Location: " + formatLocation(triggerLocation));
            return true;
        }

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
        return location.getWorld().getName() + " ("
                + location.getBlockX() + ", "
                + location.getBlockY() + ", "
                + location.getBlockZ() + ")";
    }
}
