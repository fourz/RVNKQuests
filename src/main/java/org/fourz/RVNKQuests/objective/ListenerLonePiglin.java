package org.fourz.RVNKQuests.objective;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Piglin;
import org.bukkit.event.Listener;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.util.log.LogManager;
import org.fourz.RVNKQuests.util.log.FZLogger;
import java.util.UUID;

/**
 * Listener that handles the spawning and tracking of a lone piglin in the overworld
 */
public class ListenerLonePiglin implements Listener {
    private final Quest quest;
    private final RVNKQuests plugin;
    private final String worldName;
    private final Location fixedLocation;
    private final double spawnRadius;
    private final FZLogger logger;
    
    private Piglin questPiglin;
    private String piglinName = "Lost Piglin";
    private UUID piglinUUID;

    public ListenerLonePiglin(Quest quest, RVNKQuests plugin, String worldName, Location fixedLocation, double spawnRadius) {
        this.quest = quest;
        this.plugin = plugin;
        this.worldName = worldName;
        this.fixedLocation = fixedLocation;
        this.spawnRadius = spawnRadius;
        this.logger = LogManager.getInstance(plugin, getClass());
        
        logger.debug("Initialized with world: {}, radius: " + worldName, spawnRadius + "");
    }
    
    /**
     * Check if an entity is the quest piglin
     * @param entity The entity to check
     * @return true if this is the quest piglin
     */
    public boolean isQuestPiglin(Entity entity) {
        if (entity == null) return false;
        
        // Check by UUID if we have one
        if (piglinUUID != null) {
            return entity.getUniqueId().equals(piglinUUID);
        }
        
        // Otherwise check by name
        return entity instanceof Piglin && 
               entity.getCustomName() != null && 
               entity.getCustomName().equals(piglinName);
    }
    
    /**
     * Get the quest piglin entity
     * @return The quest piglin or null if not yet spawned
     */
    public Piglin getQuestPiglin() {
        return questPiglin;
    }
    
    /**
     * Set the name for the quest piglin
     * @param name The new name
     */
    public void setPiglinName(String name) {
        this.piglinName = name;
        logger.debug("Set piglin name to: " + name + "");
        
        // Update existing piglin if it exists
        if (questPiglin != null && questPiglin.isValid()) {
            questPiglin.setCustomName(name);
            questPiglin.setCustomNameVisible(true);
        }
    }
    
    /**
     * Set the quest piglin
     * @param piglin The piglin entity
     */
    public void setQuestPiglin(Piglin piglin) {
        this.questPiglin = piglin;
        if (piglin != null) {
            this.piglinUUID = piglin.getUniqueId();
            logger.debug("Set quest piglin: " + piglinUUID + "");
        }
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        logger.debug("Cleaning up LonePiglin listener");
        if (questPiglin != null && questPiglin.isValid()) {
            questPiglin.remove();
            logger.debug("Removed quest piglin");
        }
        questPiglin = null;
        piglinUUID = null;
    }
}
