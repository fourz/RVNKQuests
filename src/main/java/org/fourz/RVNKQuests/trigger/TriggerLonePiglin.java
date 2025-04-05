package org.fourz.RVNKQuests.trigger;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.RVNKQuests.quest.Quest;

/**
 * @deprecated This class is maintained for backwards compatibility with existing quests.
 * It delegates all functionality to {@link ListenerLonePiglinTrigger}, following the naming convention
 * where "Listener" prefixes event-based functionality. Use ListenerLonePiglinTrigger directly in new code.
 */
@Deprecated
public class TriggerLonePiglin implements Listener {
    private final ListenerLonePiglinTrigger delegate;

    public TriggerLonePiglin(Quest quest, JavaPlugin plugin) {
        this.delegate = new ListenerLonePiglinTrigger(quest, plugin);
    }
    
    public TriggerLonePiglin(Quest quest, JavaPlugin plugin, String worldName, Location location, double radius) {
        this.delegate = new ListenerLonePiglinTrigger(quest, plugin, worldName, location, radius);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        delegate.onPlayerMove(event);
    }

    public void cleanup() {
        delegate.cleanup();
    }

    public void setPiglinName(String name) {
        delegate.setPiglinName(name);
    }

    public boolean isQuestPiglin(Entity entity) {
        return delegate.isQuestPiglin(entity);
    }

    public Entity getSpawnedPiglin() {
        return delegate.getSpawnedPiglin();
    }
}