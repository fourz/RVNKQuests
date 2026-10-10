package org.fourz.RVNKQuests.waypoint;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.repository.IPreferenceRepository;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Objective waypoints (#2264): one tracked quest per player, pointed at by a bossbar, a compass, or a
 * short particle trail.
 *
 * <h3>Threading</h3>
 * Everything that touches a player, a bar or a tracker runs on the main thread. The only database
 * work is the preference load on join and the preference writes, all asynchronous; their results
 * hop back through {@code mainThread}. The tick task reads memory only: the tracker, the quest's
 * precomputed waypoint sets, and the quest's per-player state cache. When that cache has no entry
 * yet, the tick asks the quest to load it (asynchronously, at most every few seconds) and shows
 * nothing until it lands.
 *
 * <h3>When the bar goes away</h3>
 * The tracked quest completes, is abandoned or reset (it is also untracked), its state has no
 * waypoint, {@code /quest track off}, {@code /quest prefs waypoints off}, quit, or plugin disable.
 * Each of these calls {@link #hide}, which removes every viewer from the bar and drops it.
 */
public class WaypointService implements Listener {

    /** Bar refresh period, in ticks. */
    public static final long PERIOD_TICKS = 10L;
    /** How long {@code --trail} runs. */
    public static final long TRAIL_MILLIS = 5_000L;
    /** How far along the line toward the target the trail reaches, in blocks. */
    public static final int TRAIL_BLOCKS = 9;
    /** How often the tick may ask a quest to load an uncached state. */
    private static final long STATE_LOAD_RETRY_MILLIS = 3_000L;

    private final RVNKQuests plugin;
    private final IPreferenceRepository prefs;
    private final BooleanSupplier storageAvailable;
    private final Function<String, DataDrivenQuest> questLookup;
    private final Executor mainThread;
    private final LogManager logger;
    private final WorldDisplayNames worldNames;

    /** Online players only. Created on join, removed on quit. */
    private final Map<UUID, Tracker> trackers = new ConcurrentHashMap<>();

    private BukkitTask task;
    private Particle.DustOptions trailDust;

    /**
     * @param prefs            the local preference table; may be null
     * @param storageAvailable false in YAML fallback, where the preference table does not exist
     * @param questLookup      quest id to its live data-driven quest, or null
     * @param mainThread       runs a task on the server thread
     */
    public WaypointService(RVNKQuests plugin, IPreferenceRepository prefs, BooleanSupplier storageAvailable,
                           Function<String, DataDrivenQuest> questLookup, Executor mainThread) {
        this.plugin = plugin;
        this.prefs = prefs;
        this.storageAvailable = storageAvailable;
        this.questLookup = questLookup;
        this.mainThread = mainThread;
        this.logger = LogManager.getInstance(plugin, "WaypointService");
        this.worldNames = new WorldDisplayNames(logger);
    }

    /** The production wiring. */
    public static WaypointService create(RVNKQuests plugin) {
        return new WaypointService(plugin, plugin.getPreferenceRepository(),
            () -> plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isAvailable(),
            id -> plugin.getQuestManager() != null
                && plugin.getQuestManager().findQuestQuietly(id) instanceof DataDrivenQuest d ? d : null,
            r -> {
                if (plugin.isEnabled()) plugin.getServer().getScheduler().runTask(plugin, r);
            });
    }

    // ==================== Lifecycle ====================

    /** Registers the join/quit listener, starts the tick task, and loads players already online (reload). */
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, PERIOD_TICKS);
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            load(p.getUniqueId());
        }
        // RVNKWorlds may enable after us; ask for display names once it has had a moment.
        plugin.getServer().getScheduler().runTaskLater(plugin, worldNames::refresh, 100L);
    }

    /** Cancels the task and removes every bar. */
    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Map.Entry<UUID, Tracker> e : trackers.entrySet()) {
            hide(e.getValue(), plugin.getServer().getPlayer(e.getKey()));
        }
        trackers.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Tracker t = trackers.remove(event.getPlayer().getUniqueId());
        if (t != null) hide(t, event.getPlayer());
    }

    /**
     * Creates the player's tracker and loads their waypoint prefs asynchronously. Until the load
     * lands, waypoints are on and nothing is tracked.
     */
    void load(UUID playerId) {
        Tracker t = new Tracker();
        trackers.put(playerId, t);
        if (prefs == null || !storageAvailable.getAsBoolean()) {
            t.loaded = true;
            return;
        }
        prefs.getAllPreferences(playerId).whenComplete((stored, ex) -> mainThread.execute(() -> {
            if (trackers.get(playerId) != t) return; // quit (and maybe rejoined) meanwhile
            if (ex != null) {
                logger.warning("Waypoint prefs not loaded for " + playerId + " - defaults used: " + ex.getMessage());
            } else {
                t.enabled = WaypointPrefs.enabledFrom(stored);
                if (t.trackedQuestId == null) {
                    // An auto-track that landed during the load wins; it is newer.
                    t.trackedQuestId = WaypointPrefs.trackedFrom(stored);
                }
            }
            t.loaded = true;
        }));
    }

    // ==================== Player-facing operations (main thread) ====================

    /** The tracked quest id, or null. */
    public String getTracked(UUID playerId) {
        Tracker t = trackers.get(playerId);
        return t == null ? null : t.trackedQuestId;
    }

    /** Whether the player has waypoints on (default true; true for an unknown player). */
    public boolean isEnabled(UUID playerId) {
        Tracker t = trackers.get(playerId);
        return t == null || t.enabled;
    }

    /**
     * Tracks a quest for a player, replacing any other, and saves it.
     *
     * @param trail true to run the 5-second particle trail as well
     * @return false when the player has no tracker (not online)
     */
    public boolean track(Player player, String questId, boolean trail) {
        Tracker t = trackers.get(player.getUniqueId());
        if (t == null) return false;
        retarget(t, player);
        t.trackedQuestId = questId;
        t.lastState = null;
        t.trailUntil = trail ? System.currentTimeMillis() + TRAIL_MILLIS : 0L;
        persist(player.getUniqueId(), WaypointPrefs.KEY_TRACKED, questId);
        return true;
    }

    /** Stops tracking and removes the bar. */
    public void untrack(UUID playerId) {
        Tracker t = trackers.get(playerId);
        if (t == null) return;
        hide(t, plugin.getServer().getPlayer(playerId));
        boolean had = t.trackedQuestId != null;
        t.trackedQuestId = null;
        t.lastState = null;
        t.trailUntil = 0L;
        if (had) persist(playerId, WaypointPrefs.KEY_TRACKED, null);
    }

    /** Turns waypoints on or off for a player and saves it. Off removes the bar; tracking is kept. */
    public void setEnabled(UUID playerId, boolean enabled) {
        Tracker t = trackers.get(playerId);
        if (t != null) {
            t.enabled = enabled;
            if (!enabled) hide(t, plugin.getServer().getPlayer(playerId));
        }
        persist(playerId, WaypointPrefs.KEY_ENABLED, String.valueOf(enabled));
    }

    /** True when prefs and tracking are saved; false in YAML fallback (memory only until quit). */
    public boolean isPersistent() {
        return prefs != null && storageAvailable.getAsBoolean();
    }

    /**
     * Called on the main thread after a state change commits (from {@code AbstractQuest}).
     *
     * <ul>
     *   <li>The tracked quest completed, was abandoned or reset: untrack.</li>
     *   <li>The tracked quest moved on: the next tick re-resolves the target.</li>
     *   <li>A quest with waypoints started (from NOT_STARTED) and nothing is tracked: track it.</li>
     * </ul>
     */
    public void onStateCommitted(UUID playerId, String questId, QuestState from, QuestState to) {
        Tracker t = trackers.get(playerId);
        if (t == null || questId == null) return;
        if (questId.equals(t.trackedQuestId)) {
            if (isEnd(to)) {
                untrack(playerId);
            } else {
                t.lastState = null;
            }
            return;
        }
        if (t.trackedQuestId == null && from == QuestState.NOT_STARTED && isActive(to)) {
            DataDrivenQuest quest = questLookup.apply(questId);
            if (quest == null || !quest.hasWaypoints()) return;
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) return;
            track(player, questId, false);
            if (t.enabled) {
                player.sendMessage("§7Tracking §e" + quest.getName() + "§7. §8/quest track off §7to stop.");
            }
        }
    }

    static boolean isActive(QuestState s) {
        return s == QuestState.TRIGGER_FOUND || s == QuestState.QUEST_ACTIVE || s == QuestState.OBJECTIVE_FOUND;
    }

    static boolean isEnd(QuestState s) {
        return s == QuestState.COMPLETED || s == QuestState.ABANDONED || s == QuestState.NOT_STARTED;
    }

    private void persist(UUID playerId, String key, String value) {
        if (!isPersistent()) return;
        (value == null ? prefs.deletePreference(playerId, key) : prefs.savePreference(playerId, key, value))
            .exceptionally(ex -> {
                logger.warning("Waypoint pref " + key + " not saved for " + playerId + ": " + ex.getMessage());
                return null;
            });
    }

    // ==================== Tick ====================

    /** One pass over online trackers. Memory reads only. */
    void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Tracker> e : trackers.entrySet()) {
            Tracker t = e.getValue();
            if (t.trackedQuestId == null && t.bar == null) continue;
            Player p = plugin.getServer().getPlayer(e.getKey());
            if (p == null) continue;
            try {
                update(p, t, now);
            } catch (RuntimeException ex) {
                logger.warning("Waypoint update failed for " + p.getName() + ": " + ex.getMessage());
                hide(t, p);
            }
        }
    }

    private void update(Player p, Tracker t, long now) {
        if (!t.enabled || t.trackedQuestId == null) {
            hide(t, p);
            return;
        }
        DataDrivenQuest quest = questLookup.apply(t.trackedQuestId);
        if (quest == null) {
            hide(t, p);
            return;
        }
        if (!quest.isStateCached(p)) {
            if (now - t.stateLoadRequestedAt >= STATE_LOAD_RETRY_MILLIS) {
                t.stateLoadRequestedAt = now;
                quest.getStateForPlayer(p); // kicks the async load; returns at once
            }
            hide(t, p);
            return;
        }
        QuestState state = quest.getStateForPlayer(p);
        if (state != t.lastState) {
            t.lastState = state;
            if (isEnd(state)) {
                untrack(p.getUniqueId());
                return;
            }
        }
        List<Waypoint> active = quest.getActiveWaypoints(state);
        if (active.isEmpty()) {
            hide(t, p);
            return;
        }

        Location here = p.getLocation(t.scratch);
        World world = p.getWorld();
        String worldName = world.getName();
        Waypoint wp = WaypointResolver.select(active, worldName, here.getX(), here.getY(), here.getZ());
        if (!Objects.equals(wp, t.target)) {
            retarget(t, p);
            t.target = wp;
        }
        boolean same = wp.isInWorld(worldName);
        if (same && (t.targetLoc == null || t.targetLoc.getWorld() != world)) {
            t.targetLoc = new Location(world, wp.x(), wp.y(), wp.z());
        }

        double dist = same ? Math.sqrt(wp.distanceSquared(here.getX(), here.getY(), here.getZ())) : -1;
        if (same && t.startDist < 0) {
            t.startDist = dist;
        }

        if (wp.style() == WaypointStyle.COMPASS && same && holdsCompass(p)) {
            if (!t.compassSet) {
                p.setCompassTarget(t.targetLoc);
                t.compassSet = true;
            }
            hideBar(t);
        } else {
            showBar(t, p, wp, same, dist, here);
        }

        if (t.trailUntil != 0L) {
            if (now >= t.trailUntil) {
                t.trailUntil = 0L;
            } else if (same) {
                spawnTrail(p, here, wp, dist);
            }
        }
    }

    private static boolean holdsCompass(Player p) {
        PlayerInventory inv = p.getInventory();
        return inv.getItemInMainHand().getType() == Material.COMPASS
            || inv.getItemInOffHand().getType() == Material.COMPASS;
    }

    private void showBar(Tracker t, Player p, Waypoint wp, boolean same, double dist, Location here) {
        int meters = same ? (int) Math.round(dist) : -1;
        int sector = same ? BearingArrow.sector(here.getYaw(), wp.x() - here.getX(), wp.z() - here.getZ()) : -2;
        if (t.bar == null || meters != t.lastMeters || sector != t.lastSector) {
            String title = same
                ? title(wp.label(), meters, BearingArrow.arrowFor(sector))
                : crossWorldTitle(wp.label(), worldNames.displayName(wp.world()));
            if (t.bar == null) {
                t.bar = plugin.getServer().createBossBar(title, BarColor.YELLOW, BarStyle.SOLID);
                t.bar.addPlayer(p);
                t.lastProgress = -1;
            } else {
                t.bar.setTitle(title);
            }
            t.lastMeters = meters;
            t.lastSector = sector;
        }
        double progress = same ? WaypointResolver.progress(dist, t.startDist) : 0.0;
        if (Math.abs(progress - t.lastProgress) > 0.002) {
            t.bar.setProgress(progress);
            t.lastProgress = progress;
        }
    }

    /** {@code "<label> - <N>m <arrow>"}. */
    public static String title(String label, int meters, String arrow) {
        return "§e" + label + " §7- §f" + meters + "m §e" + arrow;
    }

    /** {@code "<label> - in <world display name>"}. */
    public static String crossWorldTitle(String label, String worldDisplay) {
        return "§e" + label + " §7- in §f" + worldDisplay;
    }

    private void spawnTrail(Player p, Location here, Waypoint wp, double dist) {
        if (trailDust == null) {
            trailDust = new Particle.DustOptions(Color.fromRGB(255, 200, 40), 1.2f);
        }
        double sx = here.getX();
        double sy = here.getY() + 1.0;
        double sz = here.getZ();
        double dx = wp.x() + 0.5 - sx;
        double dy = wp.y() + 0.5 - sy;
        double dz = wp.z() + 0.5 - sz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0) return;
        dx /= len;
        dy /= len;
        dz /= len;
        int steps = (int) Math.min(TRAIL_BLOCKS, Math.floor(Math.min(len, dist)));
        for (int i = 1; i <= steps; i++) {
            p.spawnParticle(Particle.DUST, sx + dx * i, sy + dy * i, sz + dz * i, 1, 0, 0, 0, 0, trailDust);
        }
    }

    /** Forget the current target: next tick picks it again and captures a new start distance. */
    private void retarget(Tracker t, Player p) {
        restoreCompass(t, p);
        t.target = null;
        t.targetLoc = null;
        t.startDist = -1;
        t.lastMeters = Integer.MIN_VALUE;
        t.lastSector = Integer.MIN_VALUE;
    }

    /** Removes the bar and gives the compass back its normal target. */
    void hide(Tracker t, Player p) {
        hideBar(t);
        restoreCompass(t, p);
        t.target = null;
        t.targetLoc = null;
    }

    private static void hideBar(Tracker t) {
        if (t.bar != null) {
            t.bar.removeAll();
            t.bar = null;
        }
        t.lastMeters = Integer.MIN_VALUE;
        t.lastSector = Integer.MIN_VALUE;
        t.lastProgress = -1;
    }

    private static void restoreCompass(Tracker t, Player p) {
        if (!t.compassSet) return;
        t.compassSet = false;
        if (p != null && p.isOnline()) {
            p.setCompassTarget(p.getWorld().getSpawnLocation());
        }
    }

    // ==================== Diagnostics ====================

    /**
     * {@code /quest debug waypoint <player>}: the resolved target, world, distance and style.
     * Main thread; reads memory only.
     */
    public List<String> describe(Player p) {
        List<String> out = new ArrayList<>();
        Tracker t = trackers.get(p.getUniqueId());
        if (t == null) {
            out.add("&cNo waypoint tracker for " + p.getName() + " (not loaded yet?)");
            return out;
        }
        out.add("&6=== Waypoint: " + p.getName() + " ===");
        out.add("&7Waypoints: " + (t.enabled ? "&aon" : "&coff") + " &7| prefs loaded: &f" + t.loaded
            + " &7| saved: &f" + isPersistent());
        if (t.trackedQuestId == null) {
            out.add("&7Tracked quest: &fnone");
            return out;
        }
        DataDrivenQuest quest = questLookup.apply(t.trackedQuestId);
        out.add("&7Tracked quest: &f" + t.trackedQuestId + (quest == null ? " &c(not a loaded data-driven quest)" : ""));
        if (quest == null) return out;
        if (!quest.isStateCached(p)) {
            out.add("&7State: &enot cached yet");
            return out;
        }
        QuestState state = quest.getStateForPlayer(p);
        List<Waypoint> active = quest.getActiveWaypoints(state);
        out.add("&7State: &f" + state + " &7| active waypoints: &f" + active.size());
        for (Waypoint w : active) {
            out.add("&8  - " + w.componentId() + " " + w.world() + " " + fmt(w.x()) + " " + fmt(w.y()) + " "
                + fmt(w.z()) + " (" + w.style().key() + (w.derived() ? ", auto" : "") + ")");
        }
        Location here = p.getLocation();
        Waypoint wp = WaypointResolver.select(active, p.getWorld().getName(), here.getX(), here.getY(), here.getZ());
        if (wp == null) {
            out.add("&7Target: &fnone in this state");
            return out;
        }
        out.add("&7Target: &f" + wp.componentId() + " &7\"" + wp.label() + "&7\"");
        out.add("&7World: &f" + wp.world() + " &7(" + worldNames.displayName(wp.world()) + ") &7at &f"
            + fmt(wp.x()) + " " + fmt(wp.y()) + " " + fmt(wp.z()));
        if (wp.isInWorld(p.getWorld().getName())) {
            double d = Math.sqrt(wp.distanceSquared(here.getX(), here.getY(), here.getZ()));
            out.add("&7Distance: &f" + Math.round(d) + "m &7arrow &f"
                + BearingArrow.arrow(here.getYaw(), wp.x() - here.getX(), wp.z() - here.getZ())
                + " &7| start: &f" + (t.startDist < 0 ? "-" : Math.round(t.startDist) + "m")
                + " &7| progress: &f" + Math.round(100 * WaypointResolver.progress(d, t.startDist)) + "%");
        } else {
            out.add("&7Distance: &fother world &7(player in " + p.getWorld().getName() + ")");
        }
        out.add("&7Style: &f" + wp.style().key() + " &7| bar shown: &f" + (t.bar != null)
            + " &7| compass set: &f" + t.compassSet
            + " &7| trail: &f" + (t.trailUntil > System.currentTimeMillis() ? "running" : "off"));
        return out;
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    /** Test and diagnostics access. */
    Tracker tracker(UUID playerId) {
        return trackers.get(playerId);
    }

    /** Per-player state. Main thread only, except {@code loaded}, which is only read for display. */
    static final class Tracker {
        volatile boolean loaded;
        boolean enabled = true;
        String trackedQuestId;
        QuestState lastState;
        Waypoint target;
        Location targetLoc;
        double startDist = -1;
        BossBar bar;
        int lastMeters = Integer.MIN_VALUE;
        int lastSector = Integer.MIN_VALUE;
        double lastProgress = -1;
        long trailUntil;
        boolean compassSet;
        long stateLoadRequestedAt;
        /** Reused by {@code Player.getLocation(Location)} so the tick allocates no Location. */
        final Location scratch = new Location(null, 0, 0, 0);
    }
}
