package org.fourz.RVNKQuests.trigger.generic;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Map;

/**
 * Generic entity proximity trigger — triggers when player comes near a specific entity type.
 * Generalizes ListenerGuardianAwakening.
 *
 * <h3>Config keys:</h3>
 * <ul>
 *   <li>{@code entity_type} — EntityType name</li>
 *   <li>{@code world} — World name</li>
 *   <li>{@code radius} — Detection radius (default: 50)</li>
 *   <li>{@code advance_state} — State to advance to (default: "TRIGGER_FOUND")</li>
 * </ul>
 */
public class GenericEntityProximityTrigger implements Listener {

    private final DataDrivenQuest quest;
    private final LogManager logger;

    private final EntityType entityType;
    private final String worldName;
    private final double radius;
    private final QuestState advanceState;

    public GenericEntityProximityTrigger(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config) {
        this.quest = quest;
        this.logger = LogManager.getInstance(plugin, "GenericEntityProximityTrigger");

        this.entityType = parseEntityType(QuestComponentFactory.getStringConfig(config, "entity_type", "ELDER_GUARDIAN"));
        this.worldName = QuestComponentFactory.getStringConfig(config, "world", "world");
        this.radius = QuestComponentFactory.getDoubleConfig(config, "radius", 50.0);
        this.advanceState = parseState(QuestComponentFactory.getStringConfig(config, "advance_state", "TRIGGER_FOUND"));
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        checkEntityProximity(event.getPlayer());
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        checkEntityProximity(event.getPlayer());
    }

    private void checkEntityProximity(Player player) {
        if (quest.getStateForPlayer(player) != QuestState.NOT_STARTED) return;

        World world = player.getWorld();
        if (!world.getName().equalsIgnoreCase(worldName)) return;

        // Check for nearby entities of the specified type
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity.getType() == entityType) {
                quest.setContext("trigger_entity", entity);
                quest.advanceStateForPlayer(player.getUniqueId(), advanceState);
                logger.debug("Entity proximity trigger fired for " + player.getName() + " near " + entityType);
                return;
            }
        }
    }

    private EntityType parseEntityType(String name) {
        try {
            return EntityType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return EntityType.ZOMBIE;
        }
    }

    private QuestState parseState(String name) {
        try {
            return QuestState.valueOf(name);
        } catch (IllegalArgumentException e) {
            return QuestState.TRIGGER_FOUND;
        }
    }
}
