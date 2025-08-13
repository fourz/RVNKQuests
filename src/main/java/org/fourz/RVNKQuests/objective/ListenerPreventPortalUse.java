package org.fourz.RVNKQuests.objective;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.RVNKQuests.util.LogManager;
import org.fourz.RVNKQuests.util.RVNKLogger;

/**
 * Prevents quest mobs from using portals during encounters
 */
public class ListenerPreventPortalUse implements Listener {
    private final JavaPlugin plugin;
    private final RVNKLogger logger;
    private boolean isRegistered = true;

    public ListenerPreventPortalUse(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        logger.debug("Initialized portal use prevention listener");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        Entity entity = event.getEntity();
        
        if (entity.hasMetadata(ListenerEncounterPortal.QUEST_MOB_METADATA)) {
            logger.debug("Prevented quest mob from using portal: {}", entity.getCustomName());
            event.setCancelled(true);
            
            // Move the entity away from the portal slightly
            Location safeLocation = entity.getLocation().add(
                Math.random() * 2 - 1,  // Random X offset (-1 to 1)
                0,                       // No Y offset
                Math.random() * 2 - 1    // Random Z offset (-1 to 1)
            );
            
            // Teleport in the next tick to ensure smooth movement
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (entity.isValid()) {
                    entity.teleport(safeLocation);
                }
            });
        }
    }

    /**
     * Unregisters this listener from the server
     */
    public void unregister() {
        if (isRegistered) {
            HandlerList.unregisterAll(this);
            isRegistered = false;
            logger.debug("Unregistered portal use prevention listener");
        }
    }
    
    /**
     * Checks if this listener is registered
     * @return true if registered
     */
    public boolean isRegistered() {
        return isRegistered;
    }
}
