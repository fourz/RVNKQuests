package org.fourz.RVNKQuests.trigger.generic;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Map;

/**
 * Generic mob spawn trigger — spawns any entity type when a player enters proximity.
 * Generalizes ListenerLonePiglinTrigger.
 *
 * <h3>Config keys:</h3>
 * <ul>
 *   <li>{@code entity_type} — EntityType name (e.g., "PIGLIN")</li>
 *   <li>{@code custom_name} — Display name for spawned entity</li>
 *   <li>{@code world} — World name (default: "world")</li>
 *   <li>{@code radius} — Trigger radius in blocks (default: 50)</li>
 *   <li>{@code advance_state} — State to advance to (default: "TRIGGER_FOUND")</li>
 *   <li>{@code context_key} — Runtime context key to store spawned entity (default: "spawned_entity")</li>
 * </ul>
 */
public class GenericMobSpawnTrigger implements Listener {

    private static final String QUEST_MOB_METADATA = "rvnkquests.questmob";

    private final RVNKQuests plugin;
    private final DataDrivenQuest quest;
    private final LogManager logger;
    private final Map<String, Object> config;

    private final EntityType entityType;
    private final String customName;
    private final String worldName;
    private final double radius;
    private final QuestState advanceState;
    private final String contextKey;

    private Entity spawnedEntity;

    public GenericMobSpawnTrigger(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config) {
        this.plugin = plugin;
        this.quest = quest;
        this.config = config;
        this.logger = LogManager.getInstance(plugin, "GenericMobSpawnTrigger");

        this.entityType = parseEntityType(QuestComponentFactory.getStringConfig(config, "entity_type", "ZOMBIE"));
        this.customName = QuestComponentFactory.getStringConfig(config, "custom_name", null);
        this.worldName = QuestComponentFactory.getStringConfig(config, "world", "world");
        this.radius = QuestComponentFactory.getDoubleConfig(config, "radius", 50.0);
        this.advanceState = parseState(QuestComponentFactory.getStringConfig(config, "advance_state", "TRIGGER_FOUND"));
        this.contextKey = QuestComponentFactory.getStringConfig(config, "context_key", "spawned_entity");
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;

        Player player = event.getPlayer();

        // Only trigger for players in NOT_STARTED state
        if (quest.getStateForPlayer(player) != QuestState.NOT_STARTED) return;

        // Check world
        World world = player.getWorld();
        if (!world.getName().equalsIgnoreCase(worldName)) return;

        // Check if entity already spawned
        if (spawnedEntity != null && !spawnedEntity.isDead()) return;

        // Proximity check to spawn location (or player location if no fixed spawn)
        Location spawnLoc = player.getLocation();

        // Spawn the entity
        spawnedEntity = world.spawnEntity(spawnLoc.add(
            (Math.random() - 0.5) * 10, 0, (Math.random() - 0.5) * 10
        ), entityType);

        if (spawnedEntity instanceof LivingEntity living) {
            if (customName != null) {
                living.setCustomName(customName);
                living.setCustomNameVisible(true);
            }
            living.setRemoveWhenFarAway(false);
        }

        // Tag as quest mob
        spawnedEntity.setMetadata(QUEST_MOB_METADATA,
            new FixedMetadataValue(plugin, quest.getId()));

        // Store in runtime context
        quest.setContext(contextKey, spawnedEntity);

        // Advance state
        quest.advanceStateForPlayer(player.getUniqueId(), advanceState);

        logger.debug("Spawned " + entityType + " for quest " + quest.getId() + " near " + player.getName());
    }

    public Entity getSpawnedEntity() {
        return spawnedEntity;
    }

    private EntityType parseEntityType(String name) {
        try {
            return EntityType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown entity type: " + name + ", defaulting to ZOMBIE");
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
