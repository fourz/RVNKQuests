package org.fourz.RVNKQuests.trigger.generic;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.party.PartyBeatContext;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.util.AdvanceFeedback;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Map;

/**
 * {@code WORLD_EVENT} trigger component — a quest beat that fires from the world rather than from
 * anything the player does (#1017).
 *
 * <h2>Why this holds no {@code @EventHandler}</h2>
 *
 * <p>Every other generic trigger listens for its own Bukkit event. This one does not, and the
 * reason is <b>priority arbitration</b>: when several quests declare the same {@code event_type} in
 * the same world, only the highest-priority quest may fire per player per event. That decision
 * needs to see every candidate at once, which no single component instance can.</p>
 *
 * <p>So {@link org.fourz.RVNKQuests.trigger.WorldEventScheduler} owns all detection and dispatch,
 * and this class owns one component's configuration, eligibility rule and advance. Bukkit still
 * registers it as a listener with no handlers, which is a harmless no-op and keeps the factory
 * contract unchanged.</p>
 *
 * <h3>Config keys</h3>
 * <ul>
 *   <li>{@code event_type} — one of {@link Type} (required; an unknown value makes construction
 *       fail so {@code quest validate} reports it rather than the quest silently never firing)</li>
 *   <li>{@code world} — world name (required; WORLD_EVENT is per-world by design)</li>
 *   <li>{@code priority} — lower number wins, default 10</li>
 *   <li>{@code moon_phase} — for {@code MOON_PHASE}: {@code FULL}, {@code CRESCENT},
 *       {@code QUARTER} or {@code NEW}</li>
 *   <li>{@code required_state} — default {@code NOT_STARTED}; overridden at runtime by the
 *       {@code state_mapping} bucket (#1764)</li>
 *   <li>{@code advance_state} — default {@code QUEST_ACTIVE}</li>
 * </ul>
 */
public class GenericWorldEventTrigger implements Listener {

    /** Default priority when a component does not declare one. Lower number wins. */
    public static final int DEFAULT_PRIORITY = 10;

    /** The world events a quest may hang a beat on. */
    public enum Type {
        STORM_START,
        STORM_END,
        TIME_NIGHT,
        TIME_DAY,
        PLAYER_JOIN,
        MOON_PHASE;

        /** True for the two types the scheduler polls rather than receiving as a Bukkit event. */
        public boolean isPolled() {
            return this == TIME_NIGHT || this == TIME_DAY || this == MOON_PHASE;
        }
    }

    /**
     * Named moon phases, mapped to vanilla phase indices.
     *
     * <p>Phases 3 and 5 are deliberately unmapped: the specification named four aliases and those
     * two indices fall under none of them. A component asking for {@code QUARTER} therefore fires
     * on 2 or 6 and stays silent on 3 and 5, rather than being quietly widened to "about a
     * quarter".</p>
     */
    public enum MoonPhase {
        FULL(0),
        CRESCENT(1, 7),
        QUARTER(2, 6),
        NEW(4);

        private final int[] indices;

        MoonPhase(int... indices) {
            this.indices = indices;
        }

        public boolean matches(int phaseIndex) {
            for (int i : indices) {
                if (i == phaseIndex) return true;
            }
            return false;
        }
    }

    private final RVNKQuests plugin;
    private final DataDrivenQuest quest;
    private final LogManager logger;
    private final AdvanceFeedback advanceFeedback;

    private final Type eventType;
    private final String worldName;
    private final int priority;
    private final MoonPhase moonPhase;
    private final QuestState requiredState;
    private final QuestState advanceState;

    public GenericWorldEventTrigger(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config) {
        this.quest = quest;
        this.logger = LogManager.getInstance(plugin, "GenericWorldEventTrigger");
        this.advanceFeedback = AdvanceFeedback.from(config);
        this.plugin = plugin;

        String rawType = QuestComponentFactory.getStringConfig(config, "event_type", null);
        if (rawType == null) {
            // Thrown, not defaulted. The factory catches construction failures and surfaces them
            // through getComponentFailures(), which quest validate reports — a default here would
            // make a typo into a quest that loads and never fires (#1424).
            throw new IllegalArgumentException("WORLD_EVENT component requires 'event_type'");
        }
        this.eventType = parseEnum(Type.class, rawType,
                "Unknown WORLD_EVENT event_type '" + rawType + "'; expected one of "
                        + java.util.Arrays.toString(Type.values()));

        this.worldName = QuestComponentFactory.getStringConfig(config, "world", null);
        if (worldName == null) {
            throw new IllegalArgumentException(
                    "WORLD_EVENT component requires 'world' - the type is per-world by design");
        }

        this.priority = QuestComponentFactory.getIntConfig(config, "priority", DEFAULT_PRIORITY);

        String rawPhase = QuestComponentFactory.getStringConfig(config, "moon_phase", null);
        if (eventType == Type.MOON_PHASE) {
            if (rawPhase == null) {
                throw new IllegalArgumentException(
                        "MOON_PHASE component requires 'moon_phase' (FULL, CRESCENT, QUARTER or NEW)");
            }
            this.moonPhase = parseEnum(MoonPhase.class, rawPhase,
                    "Unknown moon_phase '" + rawPhase + "'; expected FULL, CRESCENT, QUARTER or NEW");
        } else {
            this.moonPhase = null;
        }

        this.requiredState = parseState(
                QuestComponentFactory.getStringConfig(config, "required_state", "NOT_STARTED"),
                QuestState.NOT_STARTED);
        this.advanceState = parseState(
                QuestComponentFactory.getStringConfig(config, "advance_state", "QUEST_ACTIVE"),
                QuestState.QUEST_ACTIVE);
    }

    // ── Accessors used by the scheduler ─────────────────────────────────────────

    public Type getEventType() {
        return eventType;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getPriority() {
        return priority;
    }

    public MoonPhase getMoonPhase() {
        return moonPhase;
    }

    public QuestState getRequiredState() {
        return requiredState;
    }

    public QuestState getAdvanceState() {
        return advanceState;
    }

    public DataDrivenQuest getQuest() {
        return quest;
    }

    /** True when this component watches the named world, case-insensitively (#1627). */
    public boolean watches(String world) {
        return worldName.equalsIgnoreCase(world);
    }

    /**
     * Whether this beat currently matches the world's own state.
     *
     * <p>Only meaningful for {@link Type#MOON_PHASE}; the scheduler handles the day/night windows
     * and its own once-per-cycle bookkeeping, and the storm and join types are edge-triggered by
     * Bukkit so there is no standing condition to test.</p>
     */
    public boolean matchesWorldState(World world) {
        if (eventType != Type.MOON_PHASE) return true;
        return moonPhase != null && moonPhase.matches(moonPhaseOf(world));
    }

    /**
     * Vanilla moon phase index, 0 (full) to 7.
     *
     * <p>Computed from {@code getFullTime()} rather than read from a {@code getMoonPhase()} API.
     * The arithmetic is the vanilla definition, it is stable across API versions, and it cannot
     * differ between Spigot and Paper — this plugin builds against spigot-api, where the
     * convenience accessor is not guaranteed to exist.</p>
     */
    public static int moonPhaseOf(World world) {
        return (int) (world.getFullTime() / 24000L % 8L);
    }

    // ── Firing ──────────────────────────────────────────────────────────────────

    /**
     * Cheap world pre-filter. The state check is deliberately <b>not</b> here — see {@link #fire}.
     *
     * <p>The player must be in this component's world. For {@link Type#PLAYER_JOIN} the scheduler
     * has already established that this is the login-spawn world, which at join time is the same
     * thing as the current world.</p>
     */
    public boolean watchesWorldOf(Player player) {
        return watches(player.getWorld().getName());
    }

    /**
     * Advance this player's quest for the beat, if they are eligible.
     *
     * <h2>Why the state read is asynchronous</h2>
     *
     * <p>This originally used {@code quest.getStateForPlayer(Player)}, the synchronous convenience
     * accessor, and that was <b>wrong</b> — caught in live QA on Dev. That accessor reads
     * {@code AbstractQuest.stateCache} and returns {@code NOT_STARTED} when the entry is missing,
     * kicking off an async load for next time. The interface says so explicitly: "an uncached
     * NOT_STARTED is a default, not a confirmed database state."</p>
     *
     * <p>A hot-reload, an import or a restart builds a <b>new</b> quest object with an empty cache.
     * The move-based triggers tolerate that because {@code PlayerMoveEvent} fires constantly and
     * the next event sees a warmed cache. <b>A world event is one-shot.</b> Nightfall happens once
     * per cycle and the day latch is already spent; a storm starts once. So a cold cache does not
     * delay the beat, it loses it — and worse, a beat gated on {@code NOT_STARTED} would fire for a
     * player who is actually mid-chain, because the default it read happens to match.</p>
     *
     * <p>Hence the authoritative {@code getStateForPlayer(UUID)} read, then a hop back to the main
     * thread to advance. The world is re-checked on that hop: the player can change world between
     * the read completing and the advance running.</p>
     *
     * <p>The advance carries a {@link PartyBeatContext} built from the player's own position with
     * radius 0, the same shape {@code GenericItemDiscoveryTrigger} uses — a world event has no place
     * of its own, so members share it on the footing of a kill and the party service applies its
     * configured minimum share radius.</p>
     *
     * @return completes with true if the advance was dispatched, false if the player was ineligible
     */
    public java.util.concurrent.CompletableFuture<Boolean> fire(Player player) {
        if (!watchesWorldOf(player)) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }

        java.util.UUID playerId = player.getUniqueId();
        java.util.concurrent.CompletableFuture<Boolean> result =
                new java.util.concurrent.CompletableFuture<>();

        quest.getStateForPlayer(playerId).thenAccept(state -> {
            if (state != requiredState) {
                logger.debug("WORLD_EVENT " + eventType + " skipped " + player.getName()
                        + " - at " + state + ", beat requires " + requiredState
                        + " (quest: " + quest.getId() + ")");
                result.complete(false);
                return;
            }
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                Player live = org.bukkit.Bukkit.getPlayer(playerId);
                if (live == null || !watchesWorldOf(live)) {
                    // Left the world, or logged out, between the read and this tick.
                    result.complete(false);
                    return;
                }
                quest.advanceStateForPlayer(playerId, advanceState,
                        PartyBeatContext.of(live.getLocation(), 0.0, requiredState));
                announce(live);
                logger.debug("WORLD_EVENT " + eventType + " fired for " + live.getName()
                        + " in " + worldName + " (quest: " + quest.getId()
                        + ", priority " + priority + ", -> " + advanceState + ")");
                result.complete(true);
            });
        }).exceptionally(ex -> {
            logger.warning("WORLD_EVENT " + eventType + " state lookup failed for "
                    + player.getName() + " on quest " + quest.getId() + ": " + ex);
            result.complete(false);
            return null;
        });

        return result;
    }

    /**
     * Tells the player the quest activated.
     *
     * <p>A world event is unprompted — nothing the player did caused it — so unlike a trigger they
     * walked into, silence here reads as nothing having happened. The spec asks for the standard
     * quest-start notification on activation, and the central advance path does not provide one:
     * {@code AbstractQuest.performAdvance} notifies on {@code COMPLETED} only.</p>
     *
     * <p>An authored per-beat line wins when one is configured, so a quest that has written its own
     * wording is not talked over by a generic notice. Only when none is configured does this fall
     * back to {@code NotificationService}, which is also where the player's notification
     * preferences are honoured.</p>
     *
     * <p><b>The fallback notice fires only on the opening beat.</b> Found in live QA: a three-beat
     * storm chain sent "quest start notification" on all three, so a player mid-quest was told the
     * quest had started again on every world event. {@code notifyQuestStart} means what it says, so
     * it is gated on the beat that actually starts the quest. A mid-chain beat with no authored line
     * stays silent — a quest that wants to speak on every beat should author an
     * {@code AdvanceFeedback} line, which is exactly what that mechanism is for.</p>
     */
    private void announce(Player player) {
        if (advanceFeedback.isConfigured()) {
            advanceFeedback.notifyAdvanced(player);
            return;
        }
        if (requiredState != QuestState.NOT_STARTED) {
            return;
        }
        // Fetched at fire time, not cached in the constructor. Quest components are built during
        // quest registration, which runs before the service layer is assigned in onEnable — a
        // constructor-cached null would silently disable this notice forever. Same trap
        // AbstractQuest.advanceStateForPlayer documents for the party service.
        org.fourz.RVNKQuests.service.INotificationService notifier = plugin.getNotificationService();
        if (notifier != null) {
            notifier.notifyQuestStart(player, quest.getName(), quest.getDefinition().description());
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String message) {
        for (E candidate : type.getEnumConstants()) {
            if (candidate.name().equalsIgnoreCase(raw.trim())) return candidate;
        }
        throw new IllegalArgumentException(message);
    }

    private static QuestState parseState(String name, QuestState fallback) {
        if (name == null) return fallback;
        for (QuestState state : QuestState.values()) {
            if (state.name().equalsIgnoreCase(name)) return state;
        }
        return fallback;
    }

    @Override
    public String toString() {
        return "WORLD_EVENT[" + eventType + " world=" + worldName + " priority=" + priority
                + (moonPhase == null ? "" : " phase=" + moonPhase) + "]";
    }
}
