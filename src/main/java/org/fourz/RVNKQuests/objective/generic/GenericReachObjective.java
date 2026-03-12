package org.fourz.RVNKQuests.objective.generic;

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
 * Generic reach objective — player must reach a location within a radius.
 *
 * <h3>Config keys:</h3>
 * <ul>
 *   <li>{@code world} — World name</li>
 *   <li>{@code x}, {@code y}, {@code z} — Target coordinates</li>
 *   <li>{@code radius} — Completion radius (default: 5.0)</li>
 *   <li>{@code required_state} — QuestState player must be in (default: "QUEST_ACTIVE")</li>
 *   <li>{@code advance_state} — State to advance to (default: "OBJECTIVE_FOUND")</li>
 *   <li>{@code context_location_key} — Read target from runtime context instead of config (optional)</li>
 * </ul>
 */
public class GenericReachObjective implements Listener {

    private final DataDrivenQuest quest;
    private final LogManager logger;

    private final String worldName;
    private final double targetX;
    private final double targetY;
    private final double targetZ;
    private final double radius;
    private final QuestState requiredState;
    private final QuestState advanceState;
    private final String contextLocationKey;

    public GenericReachObjective(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config) {
        this.quest = quest;
        this.logger = LogManager.getInstance(plugin, "GenericReachObjective");

        this.worldName = QuestComponentFactory.getStringConfig(config, "world", "world");
        this.targetX = QuestComponentFactory.getDoubleConfig(config, "x", 0);
        this.targetY = QuestComponentFactory.getDoubleConfig(config, "y", 64);
        this.targetZ = QuestComponentFactory.getDoubleConfig(config, "z", 0);
        this.radius = QuestComponentFactory.getDoubleConfig(config, "radius", 5.0);
        this.requiredState = parseState(QuestComponentFactory.getStringConfig(config, "required_state", "QUEST_ACTIVE"));
        this.advanceState = parseState(QuestComponentFactory.getStringConfig(config, "advance_state", "OBJECTIVE_FOUND"));
        this.contextLocationKey = QuestComponentFactory.getStringConfig(config, "context_location_key", null);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;

        Player player = event.getPlayer();
        if (quest.getStateForPlayer(player) != requiredState) return;

        Location target = getTargetLocation(player);
        if (target == null) return;

        if (target.getWorld() != null && !player.getWorld().equals(target.getWorld())) return;

        if (player.getLocation().distanceSquared(target) <= radius * radius) {
            quest.advanceStateForPlayer(player.getUniqueId(), advanceState);
            logger.debug(player.getName() + " reached target location for quest " + quest.getId());
        }
    }

    private Location getTargetLocation(Player player) {
        // Check runtime context first
        if (contextLocationKey != null) {
            Location ctxLoc = quest.getContext(contextLocationKey, Location.class);
            if (ctxLoc != null) return ctxLoc;
        }

        // Fall back to config coordinates
        World world = player.getServer().getWorld(worldName);
        if (world == null) return null;
        return new Location(world, targetX, targetY, targetZ);
    }

    private QuestState parseState(String name) {
        try {
            return QuestState.valueOf(name);
        } catch (IllegalArgumentException e) {
            return QuestState.QUEST_ACTIVE;
        }
    }
}
