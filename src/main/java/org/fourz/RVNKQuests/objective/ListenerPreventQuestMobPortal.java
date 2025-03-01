package org.fourz.RVNKQuests.objective;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.RVNKQuests.util.Debug;

import java.util.logging.Level;

public class ListenerPreventQuestMobPortal implements Listener {
    private final Plugin plugin;
    private final Debug debug;

    public ListenerPreventQuestMobPortal(JavaPlugin plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "EncounterPortalDefeated", Level.FINE);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        Entity entity = event.getEntity();
        
        if (entity.hasMetadata(ListenerEncounterPortal.QUEST_MOB_METADATA)) {
            debug.debug("Prevented quest mob from using portal: " + entity.getCustomName());
            event.setCancelled(true);
            
            // Move the entity away from the portal slightly
            Location safeLocation = entity.getLocation().add(
                Math.random() * 2 - 1,  // Random X offset (-1 to 1)
                0,                      // No Y offset
                Math.random() * 2 - 1   // Random Z offset (-1 to 1)
            );
            
            // Teleport in the next tick to ensure smooth movement
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (entity.isValid()) {
                    entity.teleport(safeLocation);
                }
            });
        }
    }
}
