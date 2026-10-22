package org.fourz.RVNKQuests.objective.generic;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * BLOCK_STATE objective (#2389): completes when every configured block check matches at once.
 * Built for lever and switch puzzles, but any BlockData property works ({@code powered},
 * {@code open}, {@code facing}, {@code power}, {@code lit}, ...).
 *
 * <h3>Config keys</h3>
 * <ul>
 *   <li>{@code checks} — list of {@code {world, x, y, z, block?, state}} (required, non-empty).
 *       {@code state} maps BlockData property names to values. See {@link BlockStateCheck}.</li>
 *   <li>{@code world} — default world for checks that omit it (optional)</li>
 *   <li>{@code radius} — when a redstone change has no player, credit active holders within this
 *       many blocks of the first check (default 16)</li>
 *   <li>{@code required_state} — default {@code QUEST_ACTIVE}; the state_mapping bucket overrides it</li>
 *   <li>{@code advance_state} — default {@code OBJECTIVE_FOUND}</li>
 *   <li>{@code requires_path} / {@code sets_path} — branching, as on INTERACT (optional)</li>
 *   <li>{@code advance_message} and friends — see {@link org.fourz.RVNKQuests.util.AdvanceFeedback}</li>
 * </ul>
 *
 * <h3>When it evaluates</h3>
 * <p>On events, never on a poll. A {@link PlayerInteractEvent} on a target block credits that
 * player. A {@link BlockRedstoneEvent} within one block of a target (a door half, a lamp, a lever
 * pulled by a plate) credits active holders within {@code radius}. Both events fire before the
 * block changes, so the check runs one tick later. All events in one tick share one evaluation.
 * {@code BlockPhysicsEvent} is not used: it fires for every block update on the server and is too
 * hot to filter cheaply.</p>
 */
public class GenericBlockStateObjective implements Listener {

    public static final String TYPE_NAME = "BLOCK_STATE";

    /** Default credit radius for a redstone change that has no player. */
    public static final double DEFAULT_RADIUS = 16.0;

    /** A redstone event this many blocks (per axis) from a check counts as "near" it. */
    private static final int REDSTONE_REACH = 1;

    private final DataDrivenQuest quest;
    private final LogManager logger;
    private final org.fourz.RVNKQuests.util.AdvanceFeedback advanceFeedback;
    private final Executor mainThread;
    private final Executor nextTick;

    private final List<BlockStateCheck> checks;
    private final double radius;
    private final QuestState requiredState;
    private final QuestState advanceState;
    private final String requiresPath;
    private final String setsPath;

    /** Players to credit at the next evaluation; guarded by {@code this}. */
    private final Set<Player> pendingPlayers = new LinkedHashSet<>();
    /** True when the pending evaluation came from redstone and should credit nearby holders. */
    private boolean pendingNearby;
    /** True while one evaluation is scheduled. */
    private boolean scheduled;

    public GenericBlockStateObjective(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config) {
        this(plugin, quest, config,
            org.fourz.RVNKQuests.util.AdvanceFeedback.mainThread(plugin),
            org.fourz.RVNKQuests.util.AdvanceFeedback.mainThread(plugin));
    }

    /** Test seam: {@code nextTick} runs the deferred evaluation; {@code mainThread} the advance line. */
    GenericBlockStateObjective(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config,
                               Executor nextTick, Executor mainThread) {
        this.quest = quest;
        this.logger = LogManager.getInstance(plugin, "GenericBlockStateObjective");
        String defaultWorld = QuestComponentFactory.getStringConfig(config, "world", null);
        // Thrown, not defaulted: the factory records it and quest validate reports the quest (#1424).
        this.checks = BlockStateCheck.parseAll(config.get("checks"), defaultWorld);
        this.radius = QuestComponentFactory.getDoubleConfig(config, "radius", DEFAULT_RADIUS);
        this.requiredState = parseState(QuestComponentFactory.getStringConfig(config, "required_state", "QUEST_ACTIVE"),
            QuestState.QUEST_ACTIVE);
        this.advanceState = parseState(QuestComponentFactory.getStringConfig(config, "advance_state", "OBJECTIVE_FOUND"),
            QuestState.OBJECTIVE_FOUND);
        this.requiresPath = QuestComponentFactory.getStringConfig(config, "requires_path", null);
        this.setsPath = QuestComponentFactory.getStringConfig(config, "sets_path", null);
        this.advanceFeedback = org.fourz.RVNKQuests.util.AdvanceFeedback.from(config);
        this.mainThread = mainThread;
        this.nextTick = nextTick;
    }

    // ==================== Events ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || !isTarget(block)) return;
        schedule(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRedstone(BlockRedstoneEvent event) {
        Block block = event.getBlock();
        if (event.getOldCurrent() == event.getNewCurrent() || !isNear(block)) return;
        schedule(null, true);
    }

    private boolean isTarget(Block block) {
        String world = block.getWorld().getName();
        for (BlockStateCheck c : checks) {
            if (c.isNear(world, block.getX(), block.getY(), block.getZ(), 0)) return true;
        }
        return false;
    }

    private boolean isNear(Block block) {
        String world = block.getWorld().getName();
        for (BlockStateCheck c : checks) {
            if (c.isNear(world, block.getX(), block.getY(), block.getZ(), REDSTONE_REACH)) return true;
        }
        return false;
    }

    /** Coalesces every event in a tick into one evaluation on the next tick. */
    void schedule(Player player, boolean nearby) {
        synchronized (this) {
            if (player != null) pendingPlayers.add(player);
            if (nearby) pendingNearby = true;
            if (scheduled) return;
            scheduled = true;
        }
        try {
            nextTick.execute(this::runPending);
        } catch (RuntimeException e) {
            // The scheduler refuses tasks while the plugin disables.
            synchronized (this) {
                scheduled = false;
                pendingPlayers.clear();
                pendingNearby = false;
            }
        }
    }

    private void runPending() {
        List<Player> players;
        boolean nearby;
        synchronized (this) {
            players = new ArrayList<>(pendingPlayers);
            nearby = pendingNearby;
            pendingPlayers.clear();
            pendingNearby = false;
            scheduled = false;
        }
        if (!allMatch(this::readBlockData)) return;
        Set<Player> candidates = new LinkedHashSet<>(players);
        if (nearby) candidates.addAll(nearbyPlayers());
        tryComplete(candidates, null);
    }

    // ==================== Evaluation ====================

    /**
     * True when every check matches.
     *
     * @param lookup block data string for a check's position; null means unknown (unloaded chunk,
     *               missing world) and counts as a mismatch
     */
    boolean allMatch(Function<BlockStateCheck, String> lookup) {
        for (BlockStateCheck c : checks) {
            if (!c.matches(lookup.apply(c))) return false;
        }
        return true;
    }

    /**
     * Advances every candidate that passes the path and state gates, when every check matches.
     *
     * @param lookup null when the caller already checked {@link #allMatch}
     * @return the number of candidates advanced
     */
    int tryComplete(Collection<Player> candidates, Function<BlockStateCheck, String> lookup) {
        if (lookup != null && !allMatch(lookup)) return 0;
        Location checkpoint = checkpoint();
        int advanced = 0;
        for (Player player : candidates) {
            if (player == null || !player.isOnline()) continue;
            // Path ahead of state, as on INTERACT: the other branch is not out of order.
            if (requiresPath != null && !requiresPath.equals(quest.getPathChoiceCached(player))) continue;
            if (quest.getStateForPlayer(player) != requiredState) continue;

            if (setsPath != null) {
                quest.setPathChoice(player, setsPath);
            }
            UUID id = player.getUniqueId();
            Location at = checkpoint != null ? checkpoint : player.getLocation();
            advanceFeedback.notifyIfCommitted(player,
                org.fourz.RVNKQuests.quest.ComponentAdvance.tryAdvance(quest, this, id, advanceState,
                    org.fourz.RVNKQuests.party.PartyBeatContext.of(at, checkpoint != null ? radius : 0.0, requiredState)),
                mainThread);
            advanced++;
            logger.debug(player.getName() + " completed BLOCK_STATE (" + checks.size() + " checks) for quest "
                + quest.getId() + " -> " + advanceState);
        }
        return advanced;
    }

    /** {@code BlockData.getAsString()} at a check, or null when the world or chunk is not loaded. */
    private String readBlockData(BlockStateCheck c) {
        World world = org.bukkit.Bukkit.getWorld(c.world());
        if (world == null || !world.isChunkLoaded(c.x() >> 4, c.z() >> 4)) return null;
        return world.getBlockAt(c.x(), c.y(), c.z()).getBlockData().getAsString();
    }

    /** Players within {@code radius} of the first check, in its world. */
    private List<Player> nearbyPlayers() {
        Location center = checkpoint();
        if (center == null || center.getWorld() == null) return List.of();
        double r2 = radius * radius;
        List<Player> out = new ArrayList<>();
        for (Player p : center.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(center) <= r2) out.add(p);
        }
        return out;
    }

    /** Centre of the first check's block, or null when its world is not loaded. */
    private Location checkpoint() {
        BlockStateCheck first = checks.get(0);
        World world;
        try {
            world = org.bukkit.Bukkit.getWorld(first.world());
        } catch (RuntimeException e) {
            return null;
        }
        return world == null ? null : new Location(world, first.x() + 0.5, first.y() + 0.5, first.z() + 0.5);
    }

    public List<BlockStateCheck> getChecks() {
        return checks;
    }

    public double getRadius() {
        return radius;
    }

    private QuestState parseState(String name, QuestState fallback) {
        try {
            return QuestState.valueOf(name);
        } catch (IllegalArgumentException e) {
            logger.warning("Quest '" + quest.getId() + "': unknown quest state '" + name
                + "' - falling back to " + fallback);
            return fallback;
        }
    }
}
