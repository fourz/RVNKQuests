package org.fourz.RVNKQuests.util;

import org.fourz.RVNKQuests.quest.QuestState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Per-player stream of quest state decisions, for {@code /quest debug trace} (#2093).
 *
 * <h2>Why this exists</h2>
 *
 * <p>{@code AbstractQuest.applyStateChange} has five exits and only one of them advances the quest.
 * The other four — already-at-state, party in-step skip, non-forward-progress rejection, and
 * prerequisite block — all complete the future <b>normally</b>, so from the caller's side a
 * silently-dropped advance is indistinguishable from a successful one. Every one of those exits
 * logs, but at {@code debug}, interleaved with every other player's traffic.</p>
 *
 * <p>#1853 was diagnosed from two log lines plus the observation that the same input produced
 * different outcomes. This turns that inference into a readout: one line per decision, for one
 * player, naming which exit was taken and why.</p>
 *
 * <h2>Cost when nobody is tracing</h2>
 *
 * <p>{@link #isActive()} is a single volatile int read, checked before any {@link Event} is
 * constructed. Nothing is allocated on the hot path while no trace is running, which matters
 * because these hooks sit inside the write chain that serialises every state change.</p>
 *
 * <h2>Threading</h2>
 *
 * <p>Emissions arrive on the async write-chain pool, <b>not</b> the main thread. Subscribers are
 * invoked there and must hop themselves before touching the Bukkit API. The subscriber registered
 * by {@code QuestTraceSubCommand} does exactly that.</p>
 */
public final class QuestTrace {

    /** Which exit of the state machine was taken. */
    public enum Decision {
        /** The advance was applied. */
        ADVANCED("&a", "ADVANCED"),
        /** Player already held the target state; side effects deliberately not re-fired. */
        ALREADY("&7", "already"),
        /** Rejected by the monotonic guard — would have moved the quest backwards (#1853). */
        NOT_FORWARD("&e", "not-forward"),
        /** Party fan-out skipped this member: they are not at the beat's starting state (#1982). */
        PARTY_OUT_OF_STEP("&e", "party-skip"),
        /** Trigger blocked because the quest's prerequisites are unmet. */
        PREREQ_BLOCKED("&c", "prereq-block");

        private final String colour;
        private final String label;

        Decision(String colour, String label) {
            this.colour = colour;
            this.label = label;
        }

        public String colour() {
            return colour;
        }

        public String label() {
            return label;
        }
    }

    /**
     * One decision.
     *
     * @param checkpoint where the beat fired, when the caller was a positional component carrying a
     *                   {@code PartyBeatContext}; null for admin paths and non-positional
     *                   components. This is the only component attribution available here —
     *                   {@code advanceStateForPlayer} does not receive a component id — so a
     *                   trace identifies a positional component by its coordinates.
     */
    public record Event(UUID playerId, String questId, QuestState from, QuestState to,
                        Decision decision, String detail, String checkpoint) {

        /** A single console/chat line, colour-coded by decision. */
        public String render() {
            StringBuilder sb = new StringBuilder();
            sb.append(decision.colour()).append(String.format("%-12s", decision.label()));
            sb.append("&f").append(questId).append(" &7").append(from).append(" &8-> &7").append(to);
            if (checkpoint != null) {
                sb.append(" &8@ ").append(checkpoint);
            }
            if (detail != null && !detail.isBlank()) {
                sb.append(" &8(").append(detail).append(")");
            }
            return sb.toString();
        }
    }

    /** Traced player -> sink. A player traces at most once; re-tracing replaces the sink. */
    private static final Map<UUID, Consumer<Event>> SINKS = new ConcurrentHashMap<>();

    /**
     * Mirror of {@code SINKS.isEmpty()} as a plain volatile read.
     *
     * <p>{@code ConcurrentHashMap.isEmpty()} is cheap but not free, and this is consulted on every
     * state change for every player. A volatile int is the cheapest correct answer.</p>
     */
    private static volatile int active = 0;

    private QuestTrace() {
    }

    /** True when at least one player is being traced. Check this before building an {@link Event}. */
    public static boolean isActive() {
        return active > 0;
    }

    /** True when this specific player is being traced. */
    public static boolean isTracing(UUID playerId) {
        return active > 0 && SINKS.containsKey(playerId);
    }

    /** Begin tracing a player, replacing any existing sink for them. */
    public static void subscribe(UUID playerId, Consumer<Event> sink) {
        SINKS.put(playerId, sink);
        active = SINKS.size();
    }

    /** Stop tracing a player. @return true if a trace was running for them. */
    public static boolean unsubscribe(UUID playerId) {
        boolean had = SINKS.remove(playerId) != null;
        active = SINKS.size();
        return had;
    }

    /** Stop every trace. Called on plugin disable so a sink cannot outlive its command sender. */
    public static void clear() {
        SINKS.clear();
        active = 0;
    }

    /** Players currently traced. */
    public static java.util.Set<UUID> traced() {
        return java.util.Set.copyOf(SINKS.keySet());
    }

    /**
     * Emit a decision. Cheap no-op when this player is not traced.
     *
     * <p>A throwing sink is swallowed: a debug readout must never be able to break the state
     * machine it is observing.</p>
     */
    public static void emit(UUID playerId, String questId, QuestState from, QuestState to,
                            Decision decision, String detail, String checkpoint) {
        if (active == 0) return;
        Consumer<Event> sink = SINKS.get(playerId);
        if (sink == null) return;
        try {
            sink.accept(new Event(playerId, questId, from, to, decision, detail, checkpoint));
        } catch (Exception ignored) {
            // Observing must not perturb. A broken sink is the tracer's problem, not the quest's.
        }
    }
}
