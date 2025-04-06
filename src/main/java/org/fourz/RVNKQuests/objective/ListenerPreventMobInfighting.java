package org.fourz.RVNKQuests.objective;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.RVNKQuests.util.Debug;

/**
 * Prevents quest mobs from targeting each other during encounters
 */
public class ListenerPreventMobInfighting implements Listener {
    private final JavaPlugin plugin;
    private final Debug debug;
    private boolean isRegistered = true;

    public ListenerPreventMobInfighting(JavaPlugin plugin) {
        this.plugin = plugin;
        // Fixed: Use null to inherit the plugin's default log level
        this.debug = Debug.createDebugger(plugin, "PreventMobInfighting", null);
        debug.debug("Initialized mob infighting prevention listener");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        Entity source = event.getEntity();
        Entity target = event.getTarget();

        // Skip if either entity is null
        if (source == null || target == null) return;

        // Check if both entities are quest mobs
        if (isQuestMob(source) && isQuestMob(target)) {
            debug.debug("Prevented quest mob " + source.getCustomName() + 
                       " from targeting quest mob " + target.getCustomName());
            event.setCancelled(true);
        }
    }

    /**
     * Checks if an entity is a quest mob
     */
    private boolean isQuestMob(Entity entity) {
        return entity.hasMetadata(ListenerEncounterPortal.QUEST_MOB_METADATA);
    }
    
    /**
     * Unregisters this listener from the server
     */
    public void unregister() {
        if (isRegistered) {
            HandlerList.unregisterAll(this);
            isRegistered = false;
            debug.debug("Unregistered mob infighting prevention listener");
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
