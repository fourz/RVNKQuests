package org.fourz.RVNKQuests.objective;

import org.bukkit.Location;
// import org.bukkit.entity.Entity; // not used
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.reward.QuestLoot;
import org.fourz.RVNKQuests.util.log.LogManager;
import org.fourz.RVNKQuests.util.log.FZLogger;
// import org.fourz.RVNKQuests.objective.ListenerPiglinEscort; // not used

public class ListenerPiglinPortalReunion implements Listener {
    private final Quest quest;
    private final ListenerPiglinEscort piglinEscortListener;
    private final ListenerEncounterPortal portalListener;
    private final QuestLoot specialLoot;
    private final FZLogger logger;
    
    private static final double PORTAL_PROXIMITY_THRESHOLD = 5.0;
    private boolean rewardGiven = false;

    public ListenerPiglinPortalReunion(Quest quest, ListenerPiglinEscort piglinEscortListener, 
                                      ListenerEncounterPortal portalListener, QuestLoot specialLoot) {
        this.quest = quest;
        this.piglinEscortListener = piglinEscortListener;
        this.portalListener = portalListener;
        this.specialLoot = specialLoot;
        this.logger = LogManager.getInstance(quest.getPlugin(), getClass());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (rewardGiven) return;
        
        Player player = event.getPlayer();
        
        // Check if this player is escorting the piglin
        if (!piglinEscortListener.isEscorting(player)) {
            return;
        }
        
        // Check if all portal mobs have been defeated
        if (!portalListener.getSpawnedMobNames().isEmpty()) {
            return;
        }
        
        Location portalLocation = portalListener.getPortalLocation();
        if (portalLocation == null) {
            return;
        }
        
        // Get the piglin being escorted
        Piglin piglin = piglinEscortListener.getEscortPiglin();
        if (piglin == null || !piglin.isValid()) {
            return;
        }
        
        // Check if the piglin is close to the portal
        if (piglin.getLocation().distance(portalLocation) <= PORTAL_PROXIMITY_THRESHOLD) {
            logger.debug("Piglin has reached the portal with player: " + player.getName());
            
            // Give special reward
            giveSpecialReward(player);
            
            // Advance quest state
            quest.advanceState(QuestState.COMPLETED);
            
            // Remove the piglin (it's going home)
            piglin.remove();
            rewardGiven = true;
        }
    }
    
    /**
     * Give the special reward for reuniting the piglin with the portal
     */
    private void giveSpecialReward(Player player) {
    logger.debug("Giving special reward to player: " + player.getName());
        
        // Add all special loot items to player's inventory
        specialLoot.generateLoot().forEach(item -> {
            player.getInventory().addItem(item);
        });
        
        // Show special effects or messages
        player.sendMessage("GrotSnout thanks you for helping him return home!");
    }
}
