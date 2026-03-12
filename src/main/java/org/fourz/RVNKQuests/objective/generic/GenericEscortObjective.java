package org.fourz.RVNKQuests.objective.generic;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.util.EntityFollow;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic escort objective — escort an entity to a destination.
 * Reuses {@link EntityFollow} for pathfinding/following behavior.
 *
 * <h3>Config keys:</h3>
 * <ul>
 *   <li>{@code context_entity_key} — Runtime context key for the entity to escort (default: "spawned_entity")</li>
 *   <li>{@code context_location_key} — Runtime context key for destination (optional)</li>
 *   <li>{@code world} — Destination world name (default: "world")</li>
 *   <li>{@code x}, {@code y}, {@code z} — Destination coordinates (if no context key)</li>
 *   <li>{@code radius} — Completion radius (default: 10.0)</li>
 *   <li>{@code follow_distance} — How close entity follows player (default: 3.0)</li>
 *   <li>{@code follow_speed} — Entity follow speed (default: 1.05)</li>
 *   <li>{@code required_state} — QuestState player must be in (default: "TRIGGER_FOUND")</li>
 *   <li>{@code advance_state} — State to advance to on arrival (default: "QUEST_ACTIVE")</li>
 *   <li>{@code fail_state} — State to set if entity dies during escort (optional)</li>
 * </ul>
 */
public class GenericEscortObjective implements Listener {

    private final RVNKQuests plugin;
    private final DataDrivenQuest quest;
    private final LogManager logger;

    private final String contextEntityKey;
    private final String contextLocationKey;
    private final String worldName;
    private final double destX;
    private final double destY;
    private final double destZ;
    private final double radius;
    private final double followDistance;
    private final double followSpeed;
    private final QuestState requiredState;
    private final QuestState advanceState;
    private final QuestState failState;

    /** Active follow tasks per player UUID. */
    private final Map<UUID, EntityFollow> activeFollows = new ConcurrentHashMap<>();

    public GenericEscortObjective(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config) {
        this.plugin = plugin;
        this.quest = quest;
        this.logger = LogManager.getInstance(plugin, "GenericEscortObjective");

        this.contextEntityKey = QuestComponentFactory.getStringConfig(config, "context_entity_key", "spawned_entity");
        this.contextLocationKey = QuestComponentFactory.getStringConfig(config, "context_location_key", null);
        this.worldName = QuestComponentFactory.getStringConfig(config, "world", "world");
        this.destX = QuestComponentFactory.getDoubleConfig(config, "x", 0);
        this.destY = QuestComponentFactory.getDoubleConfig(config, "y", 64);
        this.destZ = QuestComponentFactory.getDoubleConfig(config, "z", 0);
        this.radius = QuestComponentFactory.getDoubleConfig(config, "radius", 10.0);
        this.followDistance = QuestComponentFactory.getDoubleConfig(config, "follow_distance", 3.0);
        this.followSpeed = QuestComponentFactory.getDoubleConfig(config, "follow_speed", 1.05);
        this.requiredState = parseState(QuestComponentFactory.getStringConfig(config, "required_state", "TRIGGER_FOUND"));
        this.advanceState = parseState(QuestComponentFactory.getStringConfig(config, "advance_state", "QUEST_ACTIVE"));

        String failStateStr = QuestComponentFactory.getStringConfig(config, "fail_state", null);
        this.failState = failStateStr != null ? parseState(failStateStr) : null;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;

        Player player = event.getPlayer();
        if (quest.getStateForPlayer(player) != requiredState) return;

        Entity escortEntity = quest.getContext(contextEntityKey, Entity.class);
        if (escortEntity == null || escortEntity.isDead()) return;

        // Start following if not already
        UUID playerId = player.getUniqueId();
        if (!activeFollows.containsKey(playerId)) {
            EntityFollow follow = new EntityFollow(plugin)
                .withDistances(followDistance, 30.0)
                .withSpeed(followSpeed)
                .useNavigator(true)
                .ignoreQuestMobs(true);

            if (follow.start(escortEntity, player)) {
                activeFollows.put(playerId, follow);
                logger.debug("Started escort follow for " + player.getName() + " on quest " + quest.getId());
            }
        }

        // Check if escort entity has reached the destination
        Location dest = getDestination(player);
        if (dest == null) return;

        if (escortEntity.getLocation().distanceSquared(dest) <= radius * radius) {
            // Cleanup follow task
            EntityFollow follow = activeFollows.remove(playerId);
            if (follow != null) {
                follow.cleanup();
            }

            quest.advanceStateForPlayer(playerId, advanceState);
            logger.debug(player.getName() + " completed escort objective for quest " + quest.getId());
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (failState == null) return;

        Entity escortEntity = quest.getContext(contextEntityKey, Entity.class);
        if (escortEntity == null) return;

        if (event.getEntity().getUniqueId().equals(escortEntity.getUniqueId())) {
            // Escort entity died — fail for all active players
            for (Map.Entry<UUID, EntityFollow> entry : activeFollows.entrySet()) {
                entry.getValue().cleanup();
                quest.advanceStateForPlayer(entry.getKey(), failState);
            }
            activeFollows.clear();
            logger.debug("Escort entity died for quest " + quest.getId() + " — advancing to fail state");
        }
    }

    private Location getDestination(Player player) {
        if (contextLocationKey != null) {
            Location ctxLoc = quest.getContext(contextLocationKey, Location.class);
            if (ctxLoc != null) return ctxLoc;
        }

        org.bukkit.World world = player.getServer().getWorld(worldName);
        if (world == null) return null;
        return new Location(world, destX, destY, destZ);
    }

    private QuestState parseState(String name) {
        try {
            return QuestState.valueOf(name);
        } catch (IllegalArgumentException e) {
            return QuestState.QUEST_ACTIVE;
        }
    }
}
