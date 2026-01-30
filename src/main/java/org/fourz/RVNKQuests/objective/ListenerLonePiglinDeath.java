package org.fourz.RVNKQuests.objective;

import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestPiglinFarFromHome;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.trigger.ListenerLonePiglinTrigger;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Listener for the death of the lone piglin, used in the combat path
 * of the Piglin Far From Home quest.
 */
public class ListenerLonePiglinDeath implements Listener {
    private final Quest quest;
    private final ListenerLonePiglinTrigger piglinTrigger;
    private final ItemStack journalItem;
    private final LogManager logger;

    public ListenerLonePiglinDeath(Quest quest, ListenerLonePiglinTrigger piglinTrigger, ItemStack journalItem) {
        this.quest = quest;
        this.piglinTrigger = piglinTrigger;
        this.journalItem = journalItem;
        this.logger = LogManager.getInstance(quest.getPlugin(), getClass());
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        
        // Check if the entity that died is our quest piglin
        if (!piglinTrigger.isQuestPiglin(entity)) {
            return;
        }
        
        logger.debug("Quest piglin died");
        Player killer = (entity instanceof org.bukkit.entity.LivingEntity) ? 
                       ((org.bukkit.entity.LivingEntity) entity).getKiller() : null;
        
        if (killer == null) {
            logger.debug("Piglin died but no player killer found - dropping journal anyway");
            // Drop the journal even if there's no player killer
            if (journalItem != null) {
                entity.getWorld().dropItemNaturally(entity.getLocation(), journalItem);
            }
            return;
        }
        
    logger.debug("Piglin killed by player: " + killer.getName());
        
        // Set the player's path in the quest to combat path
        if (quest instanceof QuestPiglinFarFromHome) {
            ((QuestPiglinFarFromHome) quest).setPlayerPath(killer, QuestPiglinFarFromHome.QuestPath.COMBAT_PATH);
        }
        
        // Drop the journal item
        if (journalItem != null) {
            entity.getWorld().dropItemNaturally(entity.getLocation(), journalItem);
            killer.sendMessage(ChatColor.GOLD + "You found a written journal on the piglin's body...");
        }
        
        // Advance the quest state
        quest.advanceState(QuestState.QUEST_ACTIVE);
    }
}
