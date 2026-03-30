package org.fourz.RVNKQuests.trigger.generic;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Map;

/**
 * Generic location proximity trigger — triggers when player enters a radius around a fixed coordinate.
 * Unlike entity-based triggers, this requires no entity to be present.
 *
 * <h3>Config keys:</h3>
 * <ul>
 *   <li>{@code world} — World name (required)</li>
 *   <li>{@code x} — X coordinate (required)</li>
 *   <li>{@code y} — Y coordinate (required)</li>
 *   <li>{@code z} — Z coordinate (required)</li>
 *   <li>{@code radius} — Detection radius in blocks (default: 20)</li>
 *   <li>{@code required_state} — State required for this trigger to fire (default: "NOT_STARTED")</li>
 *   <li>{@code advance_state} — State to advance to (default: "TRIGGER_FOUND")</li>
 * </ul>
 */
public class GenericLocationProximityTrigger implements Listener {

    private final DataDrivenQuest quest;
    private final LogManager logger;

    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final double radiusSquared;
    private final QuestState requiredState;
    private final QuestState advanceState;

    public GenericLocationProximityTrigger(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config) {
        this.quest = quest;
        this.logger = LogManager.getInstance(plugin, "GenericLocationProximityTrigger");

        this.worldName = QuestComponentFactory.getStringConfig(config, "world", "world");
        this.x = QuestComponentFactory.getDoubleConfig(config, "x", 0.0);
        this.y = QuestComponentFactory.getDoubleConfig(config, "y", 64.0);
        this.z = QuestComponentFactory.getDoubleConfig(config, "z", 0.0);
        double radius = QuestComponentFactory.getDoubleConfig(config, "radius", 20.0);
        this.radiusSquared = radius * radius;
        this.requiredState = parseState(QuestComponentFactory.getStringConfig(config, "required_state", "NOT_STARTED"));
        this.advanceState = parseState(QuestComponentFactory.getStringConfig(config, "advance_state", "TRIGGER_FOUND"));
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;

        // Only check on block-level movement (not head rotation)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        if (quest.getStateForPlayer(player) != requiredState) return;

        World world = player.getWorld();
        if (!world.getName().equalsIgnoreCase(worldName)) return;

        Location playerLoc = player.getLocation();
        double dx = playerLoc.getX() - x;
        double dy = playerLoc.getY() - y;
        double dz = playerLoc.getZ() - z;
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq <= radiusSquared) {
            quest.advanceStateForPlayer(player.getUniqueId(), advanceState);
            logger.debug("Location proximity trigger fired for " + player.getName()
                    + " at " + worldName + " " + (int) x + "," + (int) y + "," + (int) z
                    + " (quest: " + quest.getId() + ")");
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
