package org.fourz.RVNKQuests.trigger;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.scheduler.BukkitTask;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.trigger.generic.GenericWorldEventTrigger;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects {@code WORLD_EVENT} conditions and dispatches them to the highest-priority quest
 * (#1017).
 *
 * <p>Owns everything a {@link GenericWorldEventTrigger} deliberately does not: the Bukkit event
 * handlers, the 60-second poll for the timed types, once-per-cycle bookkeeping, and cross-quest
 * priority arbitration.</p>
 *
 * <h2>Why arbitration lives here</h2>
 *
 * <p>The rule is that when several quests declare the same {@code event_type} in the same world,
 * only the highest-priority one fires per player per event. A component cannot enforce that about
 * itself — it would need to know every sibling. So detection is centralised and the components are
 * passive.</p>
 *
 * <h2>Why there is no registry of components</h2>
 *
 * <p>Components are discovered on demand by walking the live quest list, not by self-registering on
 * construction. Registration would need a matching deregistration, and there is no hook for one:
 * quest listeners are torn down with {@code HandlerList.unregisterAll}, which tells the listener
 * nothing. Every reload, import or {@code quest reset} would leave a stale entry pointing at a dead
 * component, firing beats for a definition that no longer exists.</p>
 *
 * <p>Discovery walks {@code getAllQuests()} and asks each {@code DataDrivenQuest} for its cached
 * listeners, so the live definition is always the only source of truth. At a 60-second poll over a
 * few dozen quests the cost is irrelevant, and the failure mode it removes is silent.</p>
 *
 * <h2>Timed events and the day counter</h2>
 *
 * <p>A 60-second poll cannot observe the instant world time crosses 13000, so "fires once per
 * night/day cycle" is implemented as a window plus a per-cycle latch: fire when the world is inside
 * the window and this component has not already fired for that game-day, where game-day is
 * {@code getFullTime() / 24000}. That is the behaviour the specification asks for, and it is robust
 * to a missed tick, a lagging server, or a {@code /time set} — none of which a crossing detector
 * would survive.</p>
 */
public class WorldEventScheduler implements Listener {

    /** Poll interval in ticks. 1200 = 60s, the resolution the MOON_PHASE spec calls for. */
    private static final long POLL_TICKS = 1200L;

    /** World time at which night begins. */
    private static final long NIGHT_START = 13000L;

    /** World time at which night ends and the day window begins again. */
    private static final long NIGHT_END = 23000L;

    private final RVNKQuests plugin;
    private final LogManager logger;

    /**
     * Last game-day each timed beat fired, keyed by {@code questId|eventType|world}.
     *
     * <p>Keyed by quest and component rather than by world alone: two quests watching the same
     * world's nightfall are independent beats and must each fire once, not race for a single
     * slot.</p>
     */
    private final Map<String, Long> lastFiredDay = new ConcurrentHashMap<>();

    private BukkitTask task;

    public WorldEventScheduler(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "WorldEventScheduler");
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────────

    /** Registers the Bukkit handlers and starts the poll. Idempotent. */
    public void start() {
        if (task != null) {
            logger.debug("WorldEventScheduler already started");
            return;
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::poll, POLL_TICKS, POLL_TICKS);
        logger.debug("WorldEventScheduler started (poll every " + (POLL_TICKS / 20) + "s)");
    }

    /** Cancels the poll and unregisters the handlers. Safe to call when never started. */
    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        org.bukkit.event.HandlerList.unregisterAll(this);
        lastFiredDay.clear();
        logger.debug("WorldEventScheduler stopped");
    }

    // ── Bukkit-driven events ────────────────────────────────────────────────────

    /**
     * Storms. {@code WeatherChangeEvent#toWeatherState()} is true for the onset of rain.
     *
     * <p>Read at {@code MONITOR} priority and ignoring cancelled events: a beat must not fire for
     * weather another plugin vetoes.</p>
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        GenericWorldEventTrigger.Type type = event.toWeatherState()
                ? GenericWorldEventTrigger.Type.STORM_START
                : GenericWorldEventTrigger.Type.STORM_END;
        dispatchToWorld(type, event.getWorld());
    }

    /**
     * Player joins.
     *
     * <p>The spec's exception: PLAYER_JOIN fires only if the player's <b>login-spawn</b> world
     * matches the component's world. At the moment {@code PlayerJoinEvent} fires, the player's
     * current world <i>is</i> their login-spawn world, so that check is simply their world here —
     * which is why this must be read on join and not on any later event.</p>
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        dispatchToPlayer(GenericWorldEventTrigger.Type.PLAYER_JOIN,
                player.getWorld(), List.of(player));
    }

    // ── Polled events ───────────────────────────────────────────────────────────

    /**
     * Evaluates the timed types for every loaded world.
     *
     * <p>Skips a world with nobody in it before doing any work. A beat requires an eligible player
     * in the component's world, and the spec's rule for "no eligible players" is a silent skip with
     * no queuing — so an empty world can be dismissed without consulting a single quest.</p>
     */
    private void poll() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;

            long fullTime = world.getFullTime();
            long day = fullTime / 24000L;
            long timeOfDay = world.getTime();

            boolean night = timeOfDay >= NIGHT_START && timeOfDay < NIGHT_END;
            pollTimed(GenericWorldEventTrigger.Type.TIME_NIGHT, world, day, night);
            pollTimed(GenericWorldEventTrigger.Type.TIME_DAY, world, day, !night);

            // MOON_PHASE has no engine debounce by design — required_state is the dedup gate — so
            // it is dispatched whenever the phase matches, with no day latch.
            dispatchToWorld(GenericWorldEventTrigger.Type.MOON_PHASE, world);
        }
    }

    /**
     * Fires a day/night beat at most once per game-day per component.
     *
     * @param inWindow whether the world is currently inside this type's window
     */
    private void pollTimed(GenericWorldEventTrigger.Type type, World world, long day, boolean inWindow) {
        if (!inWindow) return;

        List<GenericWorldEventTrigger> candidates = candidates(type, world.getName());
        if (candidates.isEmpty()) return;

        // One latch for the whole (type, world) group, not one per component.
        //
        // This was per-component, with a comment claiming two quests watching the same nightfall
        // are independent beats that must each fire. That was wrong: the specification says only
        // the highest-priority quest fires per player per event, and per-component latching fired
        // every eligible candidate with no arbitration at all. So TIME_NIGHT and TIME_DAY quietly
        // ignored priority while STORM_*, PLAYER_JOIN and MOON_PHASE honoured it — and
        // describeRegistered() printed "(priority order)" for the timed types, advertising a rule
        // the code did not apply. Found by reading this method while building a live test for it.
        String key = type + "|" + world.getName().toLowerCase(java.util.Locale.ROOT);
        Long last = lastFiredDay.get(key);
        if (last != null && last == day) return;

        // Latch before firing, not after. A fire that throws must not leave the beat armed to
        // retry sixty seconds later for the rest of the night.
        lastFiredDay.put(key, day);

        logger.debug(type + " day " + day + " in " + world.getName() + " - "
                + candidates.size() + " candidate beat(s), arbitrating per player");
        dispatchToPlayer(type, world, world.getPlayers());
    }

    // ── Dispatch and arbitration ────────────────────────────────────────────────

    private void dispatchToWorld(GenericWorldEventTrigger.Type type, World world) {
        List<Player> players = world.getPlayers();
        if (players.isEmpty()) return;
        dispatchToPlayer(type, world, players);
    }

    /**
     * Dispatches one event to a set of players, resolving priority per player.
     *
     * <p>Arbitration is <b>per player</b>, not per event, and that is deliberate: the rule is
     * "highest-priority quest per player per event". Two players in the same world can be at
     * different points in their own chains, so the winning quest for one may be ineligible for the
     * other. Picking a single global winner would silently deny the second player a beat they
     * qualify for.</p>
     */
    private void dispatchToPlayer(GenericWorldEventTrigger.Type type, World world, List<Player> players) {
        List<GenericWorldEventTrigger> candidates = candidates(type, world.getName());
        if (candidates.isEmpty()) return;

        // Lowest number wins. Ties fall to quest id so the outcome is stable across restarts
        // rather than depending on map iteration order.
        candidates.sort(Comparator
                .comparingInt(GenericWorldEventTrigger::getPriority)
                .thenComparing(c -> c.getQuest().getId()));

        List<GenericWorldEventTrigger> eligible = new ArrayList<>();
        for (GenericWorldEventTrigger component : candidates) {
            if (component.matchesWorldState(world)) eligible.add(component);
        }
        if (eligible.isEmpty()) return;

        for (Player player : players) {
            tryInPriorityOrder(player, eligible, 0);
        }
    }

    /**
     * Offers one event to a player, walking the priority list until one beat takes it.
     *
     * <p>Sequential rather than parallel, and recursive rather than a loop, because
     * {@link GenericWorldEventTrigger#fire} is now asynchronous — it reads the authoritative quest
     * state rather than the cache, for the reason documented on that method. Firing every candidate
     * at once and keeping the first success would let a lower-priority quest advance the player
     * before a higher-priority one had finished deciding, which is exactly the arbitration this
     * exists to enforce.</p>
     */
    private void tryInPriorityOrder(Player player, List<GenericWorldEventTrigger> ordered, int index) {
        if (index >= ordered.size()) return;
        ordered.get(index).fire(player).thenAccept(fired -> {
            if (!fired) {
                tryInPriorityOrder(player, ordered, index + 1);
            }
            // Fired: the highest-priority eligible beat has taken this event for this player, and
            // no further quest sees it.
        });
    }

    // ── Discovery ───────────────────────────────────────────────────────────────

    /**
     * Live {@code WORLD_EVENT} components of the given type watching the given world.
     *
     * <p>Walks the quest list on every call — see the class note on why there is no registry. A
     * component appears here through the same cached listener instances the quest registers with
     * Bukkit, so a reload swaps them out automatically.</p>
     */
    private List<GenericWorldEventTrigger> candidates(GenericWorldEventTrigger.Type type, String world) {
        List<GenericWorldEventTrigger> out = new ArrayList<>();
        if (plugin.getQuestManager() == null) return out;

        for (Quest quest : plugin.getQuestManager().getAllQuests()) {
            if (!(quest instanceof DataDrivenQuest)) continue;
            for (GenericWorldEventTrigger component : componentsOf(quest)) {
                if (component.getEventType() == type && component.watches(world)) {
                    out.add(component);
                }
            }
        }
        return out;
    }

    /**
     * A quest's distinct {@code WORLD_EVENT} components.
     *
     * <p>De-duplicated by identity. {@code createListenersForState} is asked for every state and
     * the factory caches one instance per component id, so a component mapped into two states is
     * returned twice and would otherwise fire twice.</p>
     */
    private List<GenericWorldEventTrigger> componentsOf(Quest quest) {
        Map<GenericWorldEventTrigger, Boolean> seen = new LinkedHashMap<>();
        for (QuestState state : QuestState.values()) {
            List<Listener> listeners;
            try {
                listeners = quest.createListenersForState(state);
            } catch (Exception e) {
                // A quest mid-reload can throw here. One bad quest must not stop the poll for the
                // rest of the server.
                logger.debug("Skipping quest " + quest.getId() + " state " + state
                        + " during WORLD_EVENT discovery: " + e.getMessage());
                continue;
            }
            if (listeners == null) continue;
            for (Listener listener : listeners) {
                if (listener instanceof GenericWorldEventTrigger wet) {
                    seen.putIfAbsent(wet, Boolean.TRUE);
                }
            }
        }
        return new ArrayList<>(seen.keySet());
    }

    // ── Diagnostics ─────────────────────────────────────────────────────────────

    /**
     * Every live WORLD_EVENT component, for {@code /quest debug diagnostics}.
     *
     * @return event type -> rendered component descriptions, priority order
     */
    public Map<String, List<String>> describeRegistered() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (plugin.getQuestManager() == null) return out;

        Map<GenericWorldEventTrigger.Type, List<GenericWorldEventTrigger>> byType = new HashMap<>();
        for (Quest quest : plugin.getQuestManager().getAllQuests()) {
            if (!(quest instanceof DataDrivenQuest)) continue;
            for (GenericWorldEventTrigger component : componentsOf(quest)) {
                byType.computeIfAbsent(component.getEventType(), k -> new ArrayList<>()).add(component);
            }
        }

        for (GenericWorldEventTrigger.Type type : GenericWorldEventTrigger.Type.values()) {
            List<GenericWorldEventTrigger> list = byType.get(type);
            if (list == null || list.isEmpty()) continue;
            list.sort(Comparator
                    .comparingInt(GenericWorldEventTrigger::getPriority)
                    .thenComparing(c -> c.getQuest().getId()));
            List<String> rendered = new ArrayList<>();
            for (GenericWorldEventTrigger component : list) {
                rendered.add(component.getQuest().getId() + " world=" + component.getWorldName()
                        + " priority=" + component.getPriority()
                        + (component.getMoonPhase() == null ? "" : " phase=" + component.getMoonPhase())
                        + " -> " + component.getAdvanceState());
            }
            out.put(type.name(), rendered);
        }
        return out;
    }

    /** True while the poll task is running. */
    public boolean isRunning() {
        return task != null;
    }
}
