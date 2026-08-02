package org.fourz.RVNKQuests.trigger.generic;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.reward.QuestItem;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic mob spawn trigger — spawns any entity type when a player enters proximity.
 * Generalizes ListenerLonePiglinTrigger.
 *
 * <h3>Config keys:</h3>
 * <ul>
 *   <li>{@code entity_type} — EntityType name (e.g., "PIGLIN")</li>
 *   <li>{@code custom_name} — Display name for spawned entity</li>
 *   <li>{@code world} — World name (default: "world")</li>
 *   <li>{@code x} / {@code y} / {@code z} — where the mob is posted (optional; omit to spawn near the player)</li>
 *   <li>{@code trigger_radius} — how close a player must get to a sited post before it spawns (default: 48)</li>
 *   <li>{@code radius} — <b>adoption scan</b> radius in blocks (default: 50). NOT a trigger gate —
 *       it only bounds the search for an existing mob to adopt. The trigger's only other gate is
 *       the world name, so an unsited spawn fires anywhere in that world.</li>
 *   <li>{@code advance_state} — State to advance to (default: "TRIGGER_FOUND")</li>
 *   <li>{@code context_key} — Runtime context key to store spawned entity (default: "spawned_entity")</li>
 *   <li>{@code context_location_key} — Runtime context key to store spawn Location (optional; used by REACH/ENCOUNTER)</li>
 *   <li>{@code interact_book} — QuestItem key for the book given on right-click (optional)</li>
 *   <li>{@code beg_on_attack} — If true, mob begs before becoming killable (default: false)</li>
 *   <li>{@code beg_message} — Message the mob says when hit (default: "Please don't hurt me!")</li>
 *   <li>{@code beg_count} — Number of beg attempts before the mob can be killed (default: 1)</li>
 *   <li>{@code detect_existing} — Scan for existing mobs matching name+type+world before spawning (default: global config)</li>
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
    private final String contextLocationKey;
    private final String interactBook;
    private final boolean begOnAttack;
    private final String begMessage;
    private final int begCount;

    /**
     * Where the mob belongs, or null when the trigger is world-wide.
     *
     * <p>Without this the mob spawns a few blocks from whoever walked into the world, which cannot
     * express "a guardian posted 200 blocks north of spawn" — the placement <i>is</i> the content.
     * Same gap {@code STRUCTURE_INTERACT} had before #1894: a component that reads as sitable and
     * silently is not.</p>
     *
     * <p>Optional, so quests that genuinely want "spawn near whoever shows up" keep working.</p>
     */
    private final Double siteX;
    private final Double siteY;
    private final Double siteZ;

    /** How close a player must get to a sited spawn before it fires. */
    private final double triggerRadius;

    private final boolean detectExisting;
    private final long scanIntervalMs;
    private final Map<UUID, Long> lastScanTime = new ConcurrentHashMap<>();

    /** Tracks how many times each player has hit the mob (beg mechanic). */
    private final Map<UUID, Integer> hitCounts = new ConcurrentHashMap<>();
    /** Tracks which players have already received the quest book. */
    private final Set<UUID> bookGiven = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private Entity spawnedEntity;
    private boolean firstMoveLogged = false;

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
        this.contextLocationKey = QuestComponentFactory.getStringConfig(config, "context_location_key", null);
        this.interactBook = QuestComponentFactory.getStringConfig(config, "interact_book", null);
        this.begOnAttack = QuestComponentFactory.getBoolConfig(config, "beg_on_attack", false);
        this.begMessage = QuestComponentFactory.getStringConfig(config, "beg_message", "Please don't hurt me!");
        this.begCount = QuestComponentFactory.getIntConfig(config, "beg_count", 1);

        // All three or none — a partial coordinate is a typo, not a half-sited spawn.
        boolean sited = config.containsKey("x") && config.containsKey("y") && config.containsKey("z");
        if (!sited && (config.containsKey("x") || config.containsKey("y") || config.containsKey("z"))) {
            logger.warning("Quest '" + quest.getId() + "': PROXIMITY_MOB_SPAWN has a partial"
                + " coordinate (needs x, y AND z) — falling back to spawning near the player");
        }
        this.siteX = sited ? QuestComponentFactory.getDoubleConfig(config, "x", 0.0) : null;
        this.siteY = sited ? QuestComponentFactory.getDoubleConfig(config, "y", 0.0) : null;
        this.siteZ = sited ? QuestComponentFactory.getDoubleConfig(config, "z", 0.0) : null;
        this.triggerRadius = QuestComponentFactory.getDoubleConfig(config, "trigger_radius", 48.0);

        boolean globalDetect = plugin.getConfigManager().isMobNameTypeMatchingEnabled();
        this.detectExisting = QuestComponentFactory.getBoolConfig(config, "detect_existing", globalDetect);
        this.scanIntervalMs = plugin.getConfigManager().getMobScanIntervalMs();

        logger.debug("Trigger created for quest " + quest.getId() +
            ": type=" + entityType + " world=" + worldName + " scan_radius=" + radius +
            (sited ? " site=" + siteX + "," + siteY + "," + siteZ + " trigger_radius=" + triggerRadius
                   : " (unsited — spawns near the player)") +
            (interactBook != null ? " book=" + interactBook : "") +
            (begOnAttack ? " beg=" + begCount : "") +
            (detectExisting ? " detect=on" : ""));
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        checkMobSpawn(event.getPlayer());
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        checkMobSpawn(event.getPlayer());
    }

    private void checkMobSpawn(Player player) {
        if (!firstMoveLogged) {
            firstMoveLogged = true;
            QuestState debugState = quest.getStateForPlayer(player);
            logger.debug("First move event for quest " + quest.getId() + ": player=" + player.getName() +
                " world=" + player.getWorld().getName() + " state=" + debugState +
                " targetWorld=" + worldName);
        }

        QuestState currentState = quest.getStateForPlayer(player);

        // Path 1 — Recovery: player past NOT_STARTED but lost entity reference
        if (currentState == advanceState || currentState == QuestState.TRIGGER_FOUND
                || currentState == QuestState.QUEST_ACTIVE) {
            if (spawnedEntity == null || !spawnedEntity.isValid()) {
                if (detectExisting && customName != null
                        && player.getWorld().getName().equalsIgnoreCase(worldName)
                        && shouldScan(player.getUniqueId())) {
                    // Recovery scans the post too, so a mid-quest player who wandered off still
                    // re-acquires the mob standing where it was left.
                    Entity found = findMatchingEntity(player, siteLocation(player.getWorld()));
                    if (found != null) {
                        adoptEntity(found);
                        logger.debug("Recovered quest mob " + customName + " for quest " +
                            quest.getId() + " near " + player.getName());
                    }
                }
            }
            return;
        }

        // Only trigger spawn for players in NOT_STARTED state
        if (currentState != QuestState.NOT_STARTED) return;

        // Don't act on uncached NOT_STARTED — it's a default, not a confirmed DB state.
        // The async load will populate the cache within a few ticks; next move event will re-check.
        if (!quest.isStateCached(player)) return;

        // Check world
        World world = player.getWorld();
        if (!world.getName().equalsIgnoreCase(worldName)) return;

        // Check if entity already spawned
        if (spawnedEntity != null && !spawnedEntity.isDead()) return;

        // A sited spawn only fires once the player is actually near its post. Without this the
        // trigger fires anywhere in the world, because the world-name check above is its only gate.
        Location site = siteLocation(world);
        if (site != null && player.getLocation().distanceSquared(site) > triggerRadius * triggerRadius) {
            return;
        }

        // Path 2 — NOT_STARTED: scan for existing mob before spawning
        if (detectExisting && customName != null && shouldScan(player.getUniqueId())) {
            Entity found = findMatchingEntity(player, site);
            if (found != null) {
                adoptEntity(found);
                quest.advanceStateForPlayer(player.getUniqueId(), advanceState);
                logger.debug("Adopted existing " + entityType + " (" + customName +
                    ") for quest " + quest.getId() + " near " + player.getName());
                return;
            }
        }

        // Spawn at the sited post when one is configured, otherwise near the player as before.
        Location spawnLoc = (site != null)
            ? site.clone()
            : player.getLocation().add((Math.random() - 0.5) * 10, 0, (Math.random() - 0.5) * 10);
        // Ground-snap either way: a configured Y can be stale after terraforming, and a mob spawned
        // inside rock suffocates while one spawned in air falls somewhere unhelpful.
        spawnLoc.setY(world.getHighestBlockYAt(spawnLoc) + 1);
        Entity newEntity = world.spawnEntity(spawnLoc, entityType);

        adoptEntity(newEntity);

        // Advance state
        quest.advanceStateForPlayer(player.getUniqueId(), advanceState);

        logger.debug("Spawned " + entityType + " for quest " + quest.getId() + " near " + player.getName());
    }

    /**
     * Adopts an entity as the quest mob — sets name, metadata, persistence, and context.
     */
    private void adoptEntity(Entity entity) {
        spawnedEntity = entity;

        if (entity instanceof LivingEntity living) {
            if (customName != null && !customName.equals(living.getCustomName())) {
                living.setCustomName(customName);
                living.setCustomNameVisible(true);
            }
            living.setRemoveWhenFarAway(false);

            if (living instanceof PiglinAbstract piglin) {
                piglin.setImmuneToZombification(true);
            }
        }

        if (!entity.hasMetadata(QUEST_MOB_METADATA)) {
            entity.setMetadata(QUEST_MOB_METADATA,
                new FixedMetadataValue(plugin, quest.getId()));
        }

        quest.setContext(contextKey, entity);

        // Store spawn location in context for downstream objectives (e.g., REACH, ENCOUNTER)
        if (contextLocationKey != null) {
            quest.setContext(contextLocationKey, entity.getLocation());
            logger.debug("Stored location context '" + contextLocationKey + "' at " +
                entity.getLocation().getBlockX() + "," +
                entity.getLocation().getBlockY() + "," +
                entity.getLocation().getBlockZ());
        }
    }

    /** @return the configured post as a Location in {@code world}, or null when unsited. */
    private Location siteLocation(World world) {
        return (siteX == null) ? null : new Location(world, siteX, siteY, siteZ);
    }

    /**
     * Scans for an existing mob matching entity_type + custom_name + world.
     *
     * <p>Searches around the <b>site</b> when one is configured, not the player. Anchoring the scan
     * to the player means a guardian that drifted, or a player approaching from an odd angle, reads
     * as "no mob here" and a duplicate gets spawned — which is exactly how two Sky-Watchers ended
     * up standing in the same field.</p>
     *
     * @return the nearest match, or null if none found
     */
    private Entity findMatchingEntity(Player player, Location site) {
        if (!detectExisting || customName == null) return null;
        if (!player.getWorld().getName().equalsIgnoreCase(worldName)) return null;

        Location origin = (site != null) ? site : player.getLocation();
        Entity nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (Entity entity : player.getWorld().getNearbyEntities(origin, radius, radius, radius)) {
            if (entity.getType() != entityType) continue;
            if (!(entity instanceof LivingEntity living)) continue;
            if (!customName.equals(living.getCustomName())) continue;

            double distSq = entity.getLocation().distanceSquared(origin);
            if (distSq < nearestDistSq) {
                nearest = entity;
                nearestDistSq = distSq;
            }
        }
        return nearest;
    }

    /**
     * Throttles entity scanning per player to avoid excessive world queries.
     */
    private boolean shouldScan(UUID playerId) {
        long now = System.currentTimeMillis();
        Long last = lastScanTime.get(playerId);
        if (last != null && (now - last) < scanIntervalMs) return false;
        lastScanTime.put(playerId, now);
        return true;
    }

    /**
     * Right-click interaction: gives the player the quest book if configured.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (interactBook == null) return;
        if (event.getHand() != EquipmentSlot.HAND) return; // Ignore off-hand duplicate
        if (spawnedEntity == null || !spawnedEntity.isValid()) return;
        if (!event.getRightClicked().getUniqueId().equals(spawnedEntity.getUniqueId())) return;

        Player player = event.getPlayer();
        QuestState state = quest.getStateForPlayer(player);
        if (state != QuestState.TRIGGER_FOUND && state != advanceState) return;

        giveBookIfNeeded(player);
    }

    /**
     * Attack interaction: mob begs before becoming killable when {@code beg_on_attack} is enabled.
     * Cancels damage, knocks mob back, and shows beg message until beg_count is reached.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!begOnAttack) return;
        if (spawnedEntity == null || !spawnedEntity.isValid()) return;
        if (!event.getEntity().getUniqueId().equals(spawnedEntity.getUniqueId())) return;
        if (!(event.getDamager() instanceof Player player)) return;

        UUID playerId = player.getUniqueId();
        int hits = hitCounts.getOrDefault(playerId, 0);

        if (hits >= begCount) {
            // Player has seen enough begging — allow the kill
            return;
        }

        // Cancel damage and make the mob beg
        event.setCancelled(true);
        hitCounts.put(playerId, hits + 1);

        // Knock the mob back away from the player
        if (spawnedEntity instanceof LivingEntity living) {
            Vector knockback = living.getLocation().toVector()
                .subtract(player.getLocation().toVector())
                .normalize().multiply(1.5).setY(0.3);
            living.setVelocity(knockback);
        }

        // Show beg message as mob speech
        if (spawnedEntity instanceof LivingEntity living) {
            String name = living.getCustomName() != null ? living.getCustomName() : entityType.name();
            player.sendMessage("§e<" + name + "> §f" + begMessage);
        }

        // Also give the book on first hit if configured (player might attack instead of right-click)
        if (hits == 0 && interactBook != null) {
            giveBookIfNeeded(player);
        }

        logger.debug(player.getName() + " hit quest mob — beg " + (hits + 1) + "/" + begCount);
    }

    private void giveBookIfNeeded(Player player) {
        if (!bookGiven.add(player.getUniqueId())) return; // Already given

        ItemStack book = QuestItem.getQuestItem(interactBook);
        if (book == null) {
            logger.warning("Quest book not found for key: " + interactBook);
            return;
        }

        player.getInventory().addItem(book);
        logger.debug(player.getName() + " received quest book: " + interactBook);
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
