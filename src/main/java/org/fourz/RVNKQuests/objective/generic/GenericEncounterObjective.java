package org.fourz.RVNKQuests.objective.generic;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic encounter objective — spawns a group of mobs at a location that
 * the player must defeat. Combines spawn + kill into a single component.
 *
 * <h3>Config keys:</h3>
 * <ul>
 *   <li>{@code entity_type} — EntityType to spawn (e.g., "ZOMBIFIED_PIGLIN")</li>
 *   <li>{@code spawn_count} — Number of mobs to spawn (default: 3)</li>
 *   <li>{@code required_kills} — Number of kills to complete (default: same as spawn_count)</li>
 *   <li>{@code spawn_radius} — Radius around spawn point to spread mobs (default: 5.0)</li>
 *   <li>{@code trigger_radius} — Distance from location to trigger spawn (default: 20.0)</li>
 *   <li>{@code context_location_key} — Runtime context key for spawn location (optional)</li>
 *   <li>{@code world} — World name for fixed coordinates (default: "world")</li>
 *   <li>{@code x}, {@code y}, {@code z} — Fixed spawn coordinates (if no context key)</li>
 *   <li>{@code custom_name} — Optional display name for spawned mobs</li>
 *   <li>{@code required_state} — QuestState player must be in (default: "QUEST_ACTIVE")</li>
 *   <li>{@code advance_state} — State to advance to on completion (default: "OBJECTIVE_FOUND")</li>
 * </ul>
 */
public class GenericEncounterObjective implements Listener {

    private static final String QUEST_MOB_METADATA = "rvnkquests.questmob";

    private final RVNKQuests plugin;
    private final DataDrivenQuest quest;
    private final LogManager logger;

    private final EntityType entityType;
    private final int spawnCount;
    private final int requiredKills;
    private final double spawnRadius;
    private final double triggerRadius;
    private final String contextLocationKey;
    private final String worldName;
    private final double spawnX;
    private final double spawnY;
    private final double spawnZ;
    private final String customName;
    private final QuestState requiredState;
    private final QuestState advanceState;

    /** Track spawned entities per player encounter. */
    private final Map<UUID, List<Entity>> spawnedMobs = new ConcurrentHashMap<>();
    /** Kill count per player. */
    private final Map<UUID, Integer> killCounts = new ConcurrentHashMap<>();
    /** Whether the encounter has been triggered per player. */
    private final Map<UUID, Boolean> encounterTriggered = new ConcurrentHashMap<>();

    public GenericEncounterObjective(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config) {
        this.plugin = plugin;
        this.quest = quest;
        this.logger = LogManager.getInstance(plugin, "GenericEncounterObjective");

        this.entityType = parseEntityType(QuestComponentFactory.getStringConfig(config, "entity_type", "ZOMBIE"));
        this.spawnCount = QuestComponentFactory.getIntConfig(config, "spawn_count", 3);
        this.requiredKills = QuestComponentFactory.getIntConfig(config, "required_kills", spawnCount);
        this.spawnRadius = QuestComponentFactory.getDoubleConfig(config, "spawn_radius", 5.0);
        this.triggerRadius = QuestComponentFactory.getDoubleConfig(config, "trigger_radius", 20.0);
        this.contextLocationKey = QuestComponentFactory.getStringConfig(config, "context_location_key", null);
        this.worldName = QuestComponentFactory.getStringConfig(config, "world", "world");
        this.spawnX = QuestComponentFactory.getDoubleConfig(config, "x", 0);
        this.spawnY = QuestComponentFactory.getDoubleConfig(config, "y", 64);
        this.spawnZ = QuestComponentFactory.getDoubleConfig(config, "z", 0);
        this.customName = QuestComponentFactory.getStringConfig(config, "custom_name", null);
        this.requiredState = parseState(QuestComponentFactory.getStringConfig(config, "required_state", "QUEST_ACTIVE"));
        this.advanceState = parseState(QuestComponentFactory.getStringConfig(config, "advance_state", "OBJECTIVE_FOUND"));
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;

        Player player = event.getPlayer();
        if (quest.getStateForPlayer(player) != requiredState) return;

        UUID playerId = player.getUniqueId();
        if (Boolean.TRUE.equals(encounterTriggered.get(playerId))) return;

        Location spawnLoc = getSpawnLocation(player);
        if (spawnLoc == null) return;

        // Check if player is close enough to trigger the encounter
        if (spawnLoc.getWorld() != null && !player.getWorld().equals(spawnLoc.getWorld())) return;
        if (player.getLocation().distanceSquared(spawnLoc) > triggerRadius * triggerRadius) return;

        // Trigger the encounter — spawn mobs
        encounterTriggered.put(playerId, true);
        List<Entity> mobs = new ArrayList<>();
        Random rng = new Random();

        for (int i = 0; i < spawnCount; i++) {
            double offsetX = (rng.nextDouble() - 0.5) * spawnRadius * 2;
            double offsetZ = (rng.nextDouble() - 0.5) * spawnRadius * 2;
            Location mobLoc = spawnLoc.clone().add(offsetX, 0, offsetZ);

            Entity mob = spawnLoc.getWorld().spawnEntity(mobLoc, entityType);
            if (mob instanceof LivingEntity living) {
                if (customName != null) {
                    living.setCustomName(customName);
                    living.setCustomNameVisible(true);
                }
                living.setRemoveWhenFarAway(false);
            }
            mob.setMetadata(QUEST_MOB_METADATA, new FixedMetadataValue(plugin, quest.getId()));
            mobs.add(mob);
        }

        spawnedMobs.put(playerId, mobs);
        killCounts.put(playerId, 0);

        logger.debug("Encounter triggered for " + player.getName() + " on quest " + quest.getId() +
            " — spawned " + spawnCount + " " + entityType);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        UUID playerId = killer.getUniqueId();
        if (quest.getStateForPlayer(killer) != requiredState) return;

        // Check if this was one of our spawned mobs
        if (!entity.hasMetadata(QUEST_MOB_METADATA)) return;
        if (entity.getType() != entityType) return;

        List<Entity> mobs = spawnedMobs.get(playerId);
        if (mobs == null) return;

        // Remove from tracked mobs
        mobs.removeIf(m -> m.getUniqueId().equals(entity.getUniqueId()));

        int count = killCounts.merge(playerId, 1, Integer::sum);
        logger.debug(killer.getName() + " killed encounter mob (" + count + "/" + requiredKills + ")");

        if (count >= requiredKills) {
            // Clean up remaining mobs
            for (Entity remaining : mobs) {
                if (remaining.isValid() && !remaining.isDead()) {
                    remaining.remove();
                }
            }
            spawnedMobs.remove(playerId);
            killCounts.remove(playerId);
            encounterTriggered.remove(playerId);

            quest.advanceStateForPlayer(playerId, advanceState);
            logger.debug(killer.getName() + " completed encounter objective for quest " + quest.getId());
        }
    }

    private Location getSpawnLocation(Player player) {
        if (contextLocationKey != null) {
            Location ctxLoc = quest.getContext(contextLocationKey, Location.class);
            if (ctxLoc != null) return ctxLoc;
        }

        World world = player.getServer().getWorld(worldName);
        if (world == null) return null;
        return new Location(world, spawnX, spawnY, spawnZ);
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
            return QuestState.QUEST_ACTIVE;
        }
    }
}
