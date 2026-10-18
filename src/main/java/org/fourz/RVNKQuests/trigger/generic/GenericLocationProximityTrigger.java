package org.fourz.RVNKQuests.trigger.generic;

import org.bukkit.Location;
import org.bukkit.World;
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
 * Generic location proximity trigger — triggers when player enters a radius around a fixed coordinate.
 * Unlike entity-based triggers, this requires no entity to be present.
 *
 * <h3>Config keys:</h3>
 * <ul>
 *   <li>{@code world} — World name (required)</li>
 *   <li>{@code x} — X coordinate (required)</li>
 *   <li>{@code y} — Y coordinate (required)</li>
 *   <li>{@code z} — Z coordinate (required)</li>
 *   <li>{@code site} — A named site of the world instead of x/y/z, e.g. {@code drowned:spawn} (#2316): its position
 *       comes from the plugin that generated the world ({@link org.fourz.RVNKQuests.util.SiteResolver})</li>
 *   <li>{@code radius} — Detection radius in blocks (default: 20)</li>
 *   <li>{@code required_state} — State required for this trigger to fire (default: "NOT_STARTED")</li>
 *   <li>{@code advance_state} — State to advance to (default: "TRIGGER_FOUND")</li>
 * </ul>
 */
public class GenericLocationProximityTrigger implements Listener {

    private final DataDrivenQuest quest;
    private final LogManager logger;

    /** Optional per-beat line + cue sent when this component advances the quest (#2025). */
    private final org.fourz.RVNKQuests.util.AdvanceFeedback advanceFeedback;

    /** Runs the advance line on the server thread once the advance commits (#2249). */
    private final java.util.concurrent.Executor mainThread;

    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final String site;
    private final double radius;
    private final double radiusSquared;
    private final QuestState requiredState;
    private final QuestState advanceState;

    /**
     * When each player inside the radius last fired (#1938). A refused advance (unmet prerequisites, out of step) leaves
     * the state as it was, so without this the trigger fired on every move: 5-10 times a second for a player standing
     * still. It now fires on entering the radius and at most once per {@link #REARM_MS} while the player stays; leaving
     * the radius, or a state change, re-arms it at once.
     */
    private final Map<java.util.UUID, Long> lastFired = new java.util.concurrent.ConcurrentHashMap<>();
    static final long REARM_MS = 10_000L;
    private java.util.function.LongSupplier clock = System::currentTimeMillis;

    public GenericLocationProximityTrigger(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config) {
        this.quest = quest;
        this.logger = LogManager.getInstance(plugin, "GenericLocationProximityTrigger");

        this.worldName = QuestComponentFactory.getStringConfig(config, "world", "world");
        this.x = QuestComponentFactory.getDoubleConfig(config, "x", 0.0);
        this.y = QuestComponentFactory.getDoubleConfig(config, "y", 64.0);
        this.z = QuestComponentFactory.getDoubleConfig(config, "z", 0.0);
        this.site = QuestComponentFactory.getStringConfig(config, "site", null);
        this.radius = QuestComponentFactory.getDoubleConfig(config, "radius", 20.0);
        this.radiusSquared = radius * radius;
        this.requiredState = parseState(QuestComponentFactory.getStringConfig(config, "required_state", "NOT_STARTED"));
        this.advanceFeedback = org.fourz.RVNKQuests.util.AdvanceFeedback.from(config);
        this.mainThread = org.fourz.RVNKQuests.util.AdvanceFeedback.mainThread(plugin);
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

        checkProximity(event.getPlayer(), event.getTo());
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        checkProximity(event.getPlayer(), event.getTo());
    }

    /** Test seam: the clock the re-arm interval is measured on. */
    void setClock(java.util.function.LongSupplier clock) {
        this.clock = clock;
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        lastFired.remove(event.getPlayer().getUniqueId());
    }

    private void checkProximity(Player player, Location destination) {
        java.util.UUID id = player.getUniqueId();
        if (quest.getStateForPlayer(player) != requiredState) {
            lastFired.remove(id);
            return;
        }

        if (!destination.getWorld().getName().equalsIgnoreCase(worldName)) {
            lastFired.remove(id);
            return;
        }

        double tx = x, ty = y, tz = z;
        if (site != null) {
            double[] p = org.fourz.RVNKQuests.util.SiteResolver.resolve(worldName, site);
            if (p == null) return;                       // the site is not known (yet): nothing to reach
            tx = p[0];
            ty = p[1];
            tz = p[2];
        }
        double dx = destination.getX() - tx;
        double dy = destination.getY() - ty;
        double dz = destination.getZ() - tz;

        if (dx * dx + dy * dy + dz * dz > radiusSquared) {
            lastFired.remove(id);                        // left the radius: the next entry fires at once
            return;
        }
        long now = clock.getAsLong();
        Long last = lastFired.get(id);
        if (last != null && now - last < REARM_MS) return;   // already tried while inside (#1938)
        lastFired.put(id, now);
        // Party fan-out (#1982): carry the checkpoint so qualifying members share the beat.
        // Notify only after the advance commits (#2249).
        advanceFeedback.notifyIfCommitted(player,
                org.fourz.RVNKQuests.quest.ComponentAdvance.tryAdvance(quest, this, player.getUniqueId(), advanceState,
                new org.fourz.RVNKQuests.party.PartyBeatContext(worldName, tx, ty, tz, radius, requiredState)),
                mainThread);
        logger.debug("Location proximity trigger fired for " + player.getName()
                + " at " + worldName + " " + (int) tx + "," + (int) ty + "," + (int) tz
                + " (quest: " + quest.getId() + ")");
    }

    private QuestState parseState(String name) {
        try {
            return QuestState.valueOf(name);
        } catch (IllegalArgumentException e) {
            return QuestState.TRIGGER_FOUND;
        }
    }
}
