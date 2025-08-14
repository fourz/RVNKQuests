package org.fourz.RVNKQuests.objective;

import org.bukkit.Location;
import org.bukkit.entity.Drowned;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.reward.QuestLoot;
import org.fourz.RVNKQuests.util.log.LogManager;
import org.fourz.RVNKQuests.util.log.FZLogger;

public class ListenerForgottenSiteDefeated implements Listener {
    private final Quest quest;
    private final ListenerForgottenSite siteListener;
    private final QuestLoot questLoot;
    private final FZLogger logger;

    public ListenerForgottenSiteDefeated(Quest quest, ListenerForgottenSite siteListener, QuestLoot questLoot) {
        this.quest = quest;
        this.siteListener = siteListener;
        this.questLoot = questLoot;
        this.logger = LogManager.getInstance(quest.getPlugin(), getClass());
    }

    @EventHandler
    public void onDrownedDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Drowned)) return;
        
        if (siteListener.getDefenders().contains(event.getEntity())) {
            logger.debug("Defender drowned killed: {}", event.getEntity().getCustomName());
            siteListener.getDefenders().remove(event.getEntity());
            
            if (siteListener.getDefenders().isEmpty()) {
                logger.debug("All defenders defeated, generating loot");
                event.getDrops().addAll(questLoot.generateLoot());
                
                quest.getPlugin().getServer().broadcastMessage(
                    "§b[Ancient Guardian] §fThe ancient defenders have fallen, revealing their treasured secrets!"
                );
                
                // Record this discovery in the lore database if available
                if (quest.getPlugin().hasLoreDatabase()) {
                    Location loc = event.getEntity().getLocation();
                    quest.getPlugin().getLoreDatabase().recordDiscovery(
                        "ancient_ruin", 
                        loc.getWorld().getName(), 
                        loc.getBlockX(), 
                        loc.getBlockY(), 
                        loc.getBlockZ(),
                        "Ancient underwater ruins discovered after defeating the guardians."
                    );
                }
                
                quest.advanceState(QuestState.COMPLETED);
            }
        }
    }
}
