package org.fourz.RVNKQuests.objective.generic;

import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
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
 *   <li>{@code credit_radius} — How close the player must be to a mob that died with no player
 *       killer for it to count (default: 64.0). Measured from the mob, not the spawn point, so
 *       terrain kills set up away from the arena still count (#1904)</li>
 *   <li>{@code context_location_key} — Runtime context key for spawn location (optional)</li>
 *   <li>{@code world} — World name for fixed coordinates (default: "world")</li>
 *   <li>{@code x}, {@code y}, {@code z} — Fixed spawn coordinates (if no context key)</li>
 *   <li>{@code custom_name} — Optional display name for spawned mobs</li>
 *   <li>{@code required_state} — QuestState player must be in (default: "QUEST_ACTIVE")</li>
 *   <li>{@code advance_state} — State to advance to on completion (default: "OBJECTIVE_FOUND")</li>
 *   <li>{@code prevent_infighting} — If true, quest mobs won't target each other (default: false)</li>
 *   <li>{@code block_portals} — If true, quest mobs are blocked from using portals (default: false)</li>
 *   <li>{@code loot_drops} — Map of Material name to count, added to the last mob's death drops (optional)</li>
 *   <li>{@code requires_path} — Only active when player's pathChoice matches (optional)</li>
 *   <li>{@code sets_path} — Sets pathChoice on completion (optional)</li>
 * </ul>
 */
public class GenericEncounterObjective implements Listener {

    private static final String QUEST_MOB_METADATA = "rvnkquests.questmob";

    /**
     * How close the owner must be to a mob that died without a player killer for it to count
     * (#1904). Generous on purpose: this guards against abandoning a wave, not against winning the
     * fight with the terrain.
     */
    private static final double DEFAULT_CREDIT_RADIUS = 64.0;

    private final RVNKQuests plugin;
    private final DataDrivenQuest quest;
    private final LogManager logger;

    private final EntityType entityType;
    private final int spawnCount;
    private final int requiredKills;
    private final double spawnRadius;
    private final double triggerRadius;
    private final double creditRadius;
    private final String contextLocationKey;
    private final String worldName;
    private final double spawnX;
    private final double spawnY;
    private final double spawnZ;
    private final String customName;
    private final QuestState requiredState;
    private final QuestState advanceState;
    private final boolean preventInfighting;
    private final boolean blockPortals;
    private final Map<Material, Integer> lootDrops;
    private final String requiresPath;
    private final String setsPath;

    /** Track spawned entities per player encounter. */
    private final Map<UUID, List<Entity>> spawnedMobs = new ConcurrentHashMap<>();
    /** Kill count per player. */
    private final Map<UUID, Integer> killCounts = new ConcurrentHashMap<>();
    /** Whether the encounter has been triggered per player. */
    private final Map<UUID, Boolean> encounterTriggered = new ConcurrentHashMap<>();
    /** Players already warned that the encounter can't spawn at PEACEFUL — throttles the log to once. */
    private final Set<UUID> peacefulWarned = ConcurrentHashMap.newKeySet();

    public GenericEncounterObjective(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config) {
        this.plugin = plugin;
        this.quest = quest;
        this.logger = LogManager.getInstance(plugin, "GenericEncounterObjective");

        this.entityType = parseEntityType(QuestComponentFactory.getStringConfig(config, "entity_type", "ZOMBIE"));
        this.spawnCount = QuestComponentFactory.getIntConfig(config, "spawn_count", 3);
        this.requiredKills = QuestComponentFactory.getIntConfig(config, "required_kills", spawnCount);
        this.spawnRadius = QuestComponentFactory.getDoubleConfig(config, "spawn_radius", 5.0);
        this.triggerRadius = QuestComponentFactory.getDoubleConfig(config, "trigger_radius", 20.0);
        this.creditRadius = QuestComponentFactory.getDoubleConfig(config, "credit_radius", DEFAULT_CREDIT_RADIUS);
        this.contextLocationKey = QuestComponentFactory.getStringConfig(config, "context_location_key", null);
        this.worldName = QuestComponentFactory.getStringConfig(config, "world", "world");
        this.spawnX = QuestComponentFactory.getDoubleConfig(config, "x", 0);
        this.spawnY = QuestComponentFactory.getDoubleConfig(config, "y", 64);
        this.spawnZ = QuestComponentFactory.getDoubleConfig(config, "z", 0);
        this.customName = QuestComponentFactory.getStringConfig(config, "custom_name", null);
        this.requiredState = parseState(QuestComponentFactory.getStringConfig(config, "required_state", "QUEST_ACTIVE"));
        this.advanceState = parseState(QuestComponentFactory.getStringConfig(config, "advance_state", "OBJECTIVE_FOUND"));
        this.preventInfighting = QuestComponentFactory.getBoolConfig(config, "prevent_infighting", false);
        this.blockPortals = QuestComponentFactory.getBoolConfig(config, "block_portals", false);

        // Parse loot drops
        this.lootDrops = new LinkedHashMap<>();
        Object lootObj = config.get("loot_drops");
        if (lootObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> lootMap = (Map<String, Object>) lootObj;
            for (Map.Entry<String, Object> entry : lootMap.entrySet()) {
                try {
                    Material mat = Material.valueOf(entry.getKey().toUpperCase());
                    int count = entry.getValue() instanceof Number n ? n.intValue() : 1;
                    lootDrops.put(mat, count);
                } catch (IllegalArgumentException e) {
                    logger.warning("Unknown loot material: " + entry.getKey());
                }
            }
        }

        this.requiresPath = QuestComponentFactory.getStringConfig(config, "requires_path", null);
        this.setsPath = QuestComponentFactory.getStringConfig(config, "sets_path", null);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        checkEncounter(event.getPlayer(), event.getTo());
    }

    /**
     * Arrival by teleport or portal counts as arrival (#1932).
     *
     * <p>{@link PlayerTeleportEvent} extends {@link PlayerMoveEvent} but declares its own
     * {@code HandlerList}, so the move handler above never sees a teleport. Without this, a player
     * who portals into the arena stands inside {@code trigger_radius} with no wave until they
     * physically take a step — everything looks right and nothing happens. Tales From A Hat moves
     * players between scenarios by portal, so that is the normal path, not an edge case.
     * {@code GenericReachObjective} and four other components already do this; the encounter was
     * the outlier.</p>
     */
    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        checkEncounter(event.getPlayer(), event.getTo());
    }

    /** Shared arrival check. {@code arrival} is the destination, which on a teleport is not yet the player's location. */
    private void checkEncounter(Player player, Location arrival) {
        if (quest.getStateForPlayer(player) != requiredState) return;

        // Check path restriction
        if (requiresPath != null) {
            String playerPath = quest.getPathChoiceCached(player);
            if (!requiresPath.equals(playerPath)) return;
        }

        UUID playerId = player.getUniqueId();
        if (Boolean.TRUE.equals(encounterTriggered.get(playerId))) return;

        Location spawnLoc = getSpawnLocation(player);
        if (spawnLoc == null) return;

        // Check if player is close enough to trigger the encounter. Measured against the arrival
        // location: on a teleport the player has not been moved yet, so getLocation() is still the
        // origin and would fail this check for the very case #1932 exists to fix.
        if (spawnLoc.getWorld() != null && !arrival.getWorld().equals(spawnLoc.getWorld())) return;
        if (arrival.distanceSquared(spawnLoc) > triggerRadius * triggerRadius) return;

        // #1765 Bug 1: a monster cannot spawn at PEACEFUL — spawnEntity throws IllegalStateException,
        // and (unhandled) it re-throws on every PlayerMoveEvent while the player sits in the trigger →
        // log flood / availability risk. Skip gracefully WITHOUT marking triggered (so it fires once the
        // world difficulty is raised), and log at most once per player.
        World world = spawnLoc.getWorld();
        if (world == null) return;
        if (world.getDifficulty() == Difficulty.PEACEFUL && isMonster(entityType)) {
            if (peacefulWarned.add(playerId)) {
                logger.warning("Encounter '" + quest.getId() + "': cannot spawn " + entityType
                    + " in world '" + world.getName() + "' at PEACEFUL difficulty — encounter skipped."
                    + " Raise the world to EASY+ to enable it.");
            }
            return;
        }
        peacefulWarned.remove(playerId);

        // #1765 Bug 2: mark triggered BEFORE spawning so a re-entered move cannot double-spawn, and wrap
        // the spawn so an unexpected failure rolls back partial mobs and never floods or leaves a
        // half-spawned wave. Exactly spawnCount mobs spawn once per activation.
        encounterTriggered.put(playerId, true);
        List<Entity> mobs = new ArrayList<>();
        Random rng = new Random();
        try {
            for (int i = 0; i < spawnCount; i++) {
                double[] offset = discOffset(rng, spawnRadius);
                Location mobLoc = spawnLoc.clone().add(offset[0], 0, offset[1]);

                Entity mob = world.spawnEntity(mobLoc, entityType);
                if (mob instanceof LivingEntity living) {
                    if (customName != null) {
                        living.setCustomName(customName);
                        living.setCustomNameVisible(true);
                    }
                    living.setRemoveWhenFarAway(false);

                    // Prevent piglins from zombifying in the overworld
                    if (living instanceof PiglinAbstract piglin) {
                        piglin.setImmuneToZombification(true);
                    }
                }
                mob.setMetadata(QUEST_MOB_METADATA, new FixedMetadataValue(plugin, quest.getId()));
                mobs.add(mob);
            }
        } catch (Exception e) {
            // Roll back the partial wave; keep encounterTriggered set so we don't re-spawn / flood.
            for (Entity m : mobs) {
                if (m.isValid() && !m.isDead()) m.remove();
            }
            logger.error("Encounter '" + quest.getId() + "' spawn failed for " + player.getName()
                + " (" + entityType + " in '" + world.getName() + "'): " + e.getMessage());
            return;
        }

        spawnedMobs.put(playerId, mobs);
        killCounts.put(playerId, 0);

        logger.debug("Encounter triggered for " + player.getName() + " on quest " + quest.getId() +
            " — spawned " + spawnCount + " " + entityType);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // Quick filter before any per-player work
        if (!entity.hasMetadata(QUEST_MOB_METADATA)) return;
        if (entity.getType() != entityType) return;

        // #1904: credit is driven by OWNERSHIP, not by who landed the blow. The mob was spawned for
        // one player's encounter and it is now dead; a knight that falls off the arena should not cost
        // the player the four they already killed. Resolve the owner from the spawn registry first,
        // because an environmental death has no killer at all.
        UUID playerId = findOwner(entity);
        Player killer = entity.getKiller();
        if (playerId == null && killer != null) {
            playerId = killer.getUniqueId();
        }
        if (playerId == null) return;

        List<Entity> mobs = spawnedMobs.get(playerId);
        if (mobs == null) return;
        mobs.removeIf(m -> m.getUniqueId().equals(entity.getUniqueId()));

        Player owner = plugin.getServer().getPlayer(playerId);
        if (owner == null || quest.getStateForPlayer(owner) != requiredState) return;

        // The exploit this guards: spawn a wave, walk away, let the terrain clear it. A player kill
        // always counts — they were demonstrably there. An environmental death only counts while the
        // owner is still engaged.
        boolean credited = killer != null || isOwnerEngaged(owner, entity.getLocation());

        if (!credited) {
            // Not credited, but NOT discarded either — the kills already banked stay banked.
            logger.debug("Encounter mob died with " + owner.getName() + " away from the fight on quest "
                + quest.getId() + " — no credit, existing progress kept");
            if (mobs.isEmpty()) {
                // No player-facing message here: the owner walked away from this wave, so telling
                // them to "approach again" is noise about something they chose to leave. The
                // silent-reset complaint in #1904 was about killing 4 of 5 and losing the lot —
                // that case now credits and completes, so it cannot reach this branch.
                logger.debug("Encounter wave for " + owner.getName() + " on quest " + quest.getId()
                    + " expired with the owner away — encounter reset, no message sent");
                cleanupEncounter(playerId, mobs);
            }
            return;
        }

        int count = killCounts.merge(playerId, 1, Integer::sum);
        logger.debug(owner.getName() + (killer != null ? " killed" : " was credited for")
            + " encounter mob (" + count + "/" + requiredKills + ") on quest " + quest.getId());

        if (count >= requiredKills) {
            // Loot rides on the last mob's death. An environmental death still drops it — the
            // objective is complete either way, and withholding it would punish the terrain.
            if (!lootDrops.isEmpty()) {
                for (Map.Entry<Material, Integer> loot : lootDrops.entrySet()) {
                    event.getDrops().add(new ItemStack(loot.getKey(), loot.getValue()));
                }
            }
            cleanupEncounter(playerId, mobs);
            if (setsPath != null) quest.setPathChoice(owner, setsPath);
            // Party fan-out (#1986): checkpoint is the ARENA POST, not the last mob's death spot.
            // A running fight legitimately ends far from the post — that is why kill credit is
            // judged from the mob (#1904) — but the arena is the fixed thing the wave belongs to,
            // and a party member who stayed in the arena while the firer chased the last knight
            // over a ridge should still share the beat. Falls back to the death location only if
            // the spawn point cannot be resolved.
            Location postLoc = getSpawnLocation(owner);
            quest.advanceStateForPlayer(playerId, advanceState,
                org.fourz.RVNKQuests.party.PartyBeatContext.of(
                    postLoc != null ? postLoc : entity.getLocation(), triggerRadius, requiredState));
            logger.debug(owner.getName() + " completed encounter objective for quest " + quest.getId());
        } else if (mobs.isEmpty()) {
            // Every mob is gone but the bar was not met. With ownership-based credit this is only
            // reachable when required_kills exceeds the number actually spawned — an authoring
            // error, not something the player did or can act on, so it goes to the log and not to
            // their chat. The WARN names both numbers so the misconfiguration is obvious.
            cleanupEncounter(playerId, mobs);
            logger.warning("Encounter '" + quest.getId() + "': wave exhausted for " + owner.getName()
                + " at " + count + "/" + requiredKills + " — required_kills may exceed spawn_count ("
                + requiredKills + " > " + spawnCount + "). Encounter reset.");
        }
    }

    /**
     * A random horizontal offset lying inside a disc of {@code radius} (#1904).
     *
     * <p>The previous form offset each axis by {@code ±radius} independently, which is a
     * <em>square</em>: its corners sit at {@code radius * sqrt(2)}, so a configured
     * {@code spawn_radius: 8} could place a mob 11.3 blocks out. {@code spawn_radius} was
     * therefore never the bound it advertises. Polar sampling honours it exactly, and the
     * {@code sqrt} on the radius keeps the distribution uniform over the disc rather than
     * bunching mobs at the centre.</p>
     *
     * @return {@code [offsetX, offsetZ]}, with {@code hypot(offsetX, offsetZ) <= radius}
     */
    static double[] discOffset(Random rng, double radius) {
        double angle = rng.nextDouble() * Math.PI * 2;
        double dist = Math.sqrt(rng.nextDouble()) * radius;
        return new double[] { Math.cos(angle) * dist, Math.sin(angle) * dist };
    }

    /** @return the player whose encounter spawned this entity, or null if it belongs to no live wave. */
    private UUID findOwner(LivingEntity entity) {
        for (Map.Entry<UUID, List<Entity>> entry : spawnedMobs.entrySet()) {
            for (Entity m : entry.getValue()) {
                if (m.getUniqueId().equals(entity.getUniqueId())) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /**
     * True when the owner was fighting <em>this mob</em> when it died.
     *
     * <p>Measured against the <b>mob's death location</b>, not the spawn post. Killing quest mobs
     * with the terrain is a legitimate tactic — kite a knight off a ledge, into lava, onto a golem —
     * and a running fight naturally leaves the arena. Judging engagement by distance from the post
     * would refuse credit for exactly those plays: with {@code trigger_radius: 25}, luring a mob 30
     * blocks to a drop puts the player out of bounds at the moment it lands, and the kill they set
     * up would not count. The player is near what they killed; that is the thing worth testing.</p>
     *
     * <p>{@code credit_radius} is deliberately generous (default {@value #DEFAULT_CREDIT_RADIUS})
     * because it is only guarding against spawn-a-wave-and-abandon-it, not policing how the fight is
     * won. Same world and still online carry most of that weight already.</p>
     */
    private boolean isOwnerEngaged(Player owner, Location deathLoc) {
        if (!owner.isOnline()) return false;
        if (deathLoc == null || deathLoc.getWorld() == null) return false;
        if (!owner.getWorld().equals(deathLoc.getWorld())) return false;
        return owner.getLocation().distanceSquared(deathLoc) <= creditRadius * creditRadius;
    }


    private void cleanupEncounter(UUID playerId, List<Entity> mobs) {
        for (Entity mob : mobs) {
            if (mob.isValid() && !mob.isDead()) mob.remove();
        }
        spawnedMobs.remove(playerId);
        killCounts.remove(playerId);
        encounterTriggered.remove(playerId);
        peacefulWarned.remove(playerId);
    }

    /** True when the entity type is a hostile monster (which cannot spawn at PEACEFUL difficulty). */
    private static boolean isMonster(EntityType type) {
        Class<?> cls = type.getEntityClass();
        return cls != null && Monster.class.isAssignableFrom(cls);
    }

    /**
     * Prevents quest mobs from targeting each other when {@code prevent_infighting} is enabled.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (!preventInfighting) return;
        if (event.getTarget() == null) return;

        boolean sourceIsQuestMob = event.getEntity().hasMetadata(QUEST_MOB_METADATA);
        boolean targetIsQuestMob = event.getTarget().hasMetadata(QUEST_MOB_METADATA);

        if (sourceIsQuestMob && targetIsQuestMob) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevents quest mobs from entering portals when {@code block_portals} is enabled.
     * Nudges the mob away from the portal to prevent re-triggering.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (!blockPortals) return;
        if (!event.getEntity().hasMetadata(QUEST_MOB_METADATA)) return;

        event.setCancelled(true);

        // Nudge entity away from portal to prevent re-triggering
        Entity entity = event.getEntity();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (entity.isValid()) {
                Random rng = new Random();
                Location loc = entity.getLocation();
                loc.add(rng.nextDouble() * 2 - 1, 0, rng.nextDouble() * 2 - 1);
                entity.teleport(loc);
            }
        }, 1L);
    }

    /**
     * Returns all currently tracked spawned mobs across all player encounters.
     * Used by admin commands to list/kill quest mobs.
     */
    public List<Entity> getAllSpawnedMobs() {
        List<Entity> all = new ArrayList<>();
        for (List<Entity> mobs : spawnedMobs.values()) {
            for (Entity mob : mobs) {
                if (mob != null && mob.isValid()) {
                    all.add(mob);
                }
            }
        }
        return all;
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
