package org.fourz.RVNKQuests.quests;

import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.quests.Quest;

/**
 * Extended Quest interface that adds player-specific quest operations.
 * Extends the core Quest interface with methods for player interaction.
 */
public interface Quest extends org.fourz.RVNKQuests.quest.Quest {
    
    /**
     * Starts the quest for the specified player.
     * 
     * @param player The player who is starting the quest
     * @return true if the quest was successfully started
     */
    boolean start(Player player);
    
    /**
     * Updates the quest progress for the specified player.
     * 
     * @param player The player whose quest progress is being updated
     * @return true if the update was successful
     */
    boolean update(Player player);
    
    /**
     * Completes the quest for the specified player.
     * 
     * @param player The player who is completing the quest
     * @return true if the quest was successfully completed
     */
    boolean complete(Player player);
}
