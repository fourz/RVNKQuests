package org.fourz.RVNKQuests.objective;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestPiglinFarFromHome;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.trigger.ListenerLonePiglinTrigger;
import org.fourz.RVNKQuests.util.Debug;

/**
 * Listener for the escort path of the Piglin Far From Home quest.
 */
public class ListenerPiglinEscort implements Listener {
    private final Quest quest;
    private final ListenerLonePiglinTrigger piglinTrigger;
    private final Debug debug;
    private Player activeEscorter = null;

    public ListenerPiglinEscort(Quest quest, ListenerLonePiglinTrigger piglinTrigger) {
        this.quest = quest;
        this.piglinTrigger = piglinTrigger;
        this.debug = Debug.createDebugger(quest.getPlugin(), "PiglinEscort", quest.getPlugin().getDebugger().getLogLevel());
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();
        
        // Check if the entity is our quest piglin
        if (!piglinTrigger.isQuestPiglin(entity)) {
            return;
        }
        
        debug.debug("Player " + player.getName() + " interacted with quest piglin");
        
        // Set the player's path in the quest to escort path
        if (quest instanceof QuestPiglinFarFromHome) {
            ((QuestPiglinFarFromHome) quest).setPlayerPath(player, QuestPiglinFarFromHome.QuestPath.ESCORT_PATH);
            
            // Store the active escorter
            setActiveEscorter(player);
            
            // Send a message to the player
            player.sendMessage(ChatColor.GOLD + "The piglin looks at you with hope. " +
                    "Maybe you can help it find its way home...");
            
            // Advance the quest state
            quest.advanceState(QuestState.QUEST_ACTIVE);
        }
    }
    
    /**
     * Checks if a player is currently escorting the piglin
     * @param player The player to check
     * @return true if this player is escorting the piglin
     */
    public boolean isEscorting(Player player) {
        return player != null && player.equals(activeEscorter) && 
               piglinTrigger.getSpawnedPiglin() != null && 
               !piglinTrigger.getSpawnedPiglin().isDead();
    }
    
    /**
     * Gets the piglin being escorted
     * @return The piglin entity or null if none
     */
    public Piglin getEscortPiglin() {
        Entity piglin = piglinTrigger.getSpawnedPiglin();
        return (piglin instanceof Piglin) ? (Piglin)piglin : null;
    }
    
    /**
     * Sets the active escorter
     * @param player The player escorting the piglin
     */
    private void setActiveEscorter(Player player) {
        if (activeEscorter != null && !activeEscorter.equals(player)) {
            activeEscorter.sendMessage(ChatColor.RED + "Someone else is now escorting the piglin.");
        }
        activeEscorter = player;
        debug.debug("Set active escorter to: " + (player != null ? player.getName() : "null"));
    }
    
    /**
     * Gets the player who is currently escorting the piglin
     * @return The escorting player or null if none
     */
    public Player getActiveEscorter() {
        return activeEscorter;
    }
    
    /**
     * Cleans up any resources used by this listener
     */
    public void cleanup() {
        debug.debug("Cleaning up PiglinEscort listener");
        activeEscorter = null;
    }
}
