package org.fourz.RVNKQuests.util;

import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.quest.QuestState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tells a player that a deliberate interaction landed on the wrong quest beat.
 *
 * <p>Components gate on {@code required_state} and then {@code return}. That is correct behaviour
 * and terrible feedback: a player who reaches beat three while standing on beat one right-clicks
 * the lectern, gets nothing at all, and reasonably concludes the quest is broken. Several sessions
 * have been spent chasing "the trigger doesn't fire" reports that were the state gate working
 * exactly as designed.</p>
 *
 * <h3>Deliberate interactions only</h3>
 * <p>This belongs on right-clicks and item uses — things a player chose to do at a specific block
 * or entity. It must <b>not</b> be wired into movement-driven components
 * ({@code LOCATION_PROXIMITY}, {@code REACH}, {@code COLLECT}, {@code ESCORT}), which evaluate on
 * every {@code PlayerMoveEvent} and would turn this into a message every tick.</p>
 *
 * <h3>Silence for undiscovered content</h3>
 * <p>A player at {@code NOT_STARTED} is told nothing, ever. They have not engaged with this quest,
 * so a "not yet" message would announce the existence of hidden content and hand out a map of
 * every unfound trigger on the server to anyone right-clicking their way around. The point here is
 * to stop a quest a player is <i>already inside</i> from reading as broken — not to advertise.</p>
 *
 * @since 1.1.29
 */
public final class OutOfOrderFeedback {

    /** Per-player gap between messages, so holding right-click does not spam. */
    private static final long DEFAULT_COOLDOWN_MS = 20_000L;

    private static final String DEFAULT_TOO_EARLY =
        "&7Nothing here answers you — not yet.";
    private static final String DEFAULT_ALREADY_PAST =
        "&7Whatever was here, you have already taken it.";

    private final boolean enabled;
    private final String tooEarlyMessage;
    private final String alreadyPastMessage;
    private final long cooldownMs;

    private final Map<UUID, Long> lastSpoken = new ConcurrentHashMap<>();

    private OutOfOrderFeedback(boolean enabled, String tooEarly, String alreadyPast, long cooldownMs) {
        this.enabled = enabled;
        this.tooEarlyMessage = tooEarly;
        this.alreadyPastMessage = alreadyPast;
        this.cooldownMs = cooldownMs;
    }

    /**
     * Builds the feedback for one component from its config block.
     *
     * <p>Config keys — all optional:</p>
     * <ul>
     *   <li>{@code out_of_order_feedback} — false to stay silent (default: true)</li>
     *   <li>{@code out_of_order_message} — shown when the player has not reached this beat</li>
     *   <li>{@code already_past_message} — shown when the player is past it</li>
     *   <li>{@code out_of_order_cooldown_ms} — per-player throttle
     *       (default: {@value #DEFAULT_COOLDOWN_MS})</li>
     * </ul>
     *
     * @param config The component's config map; null yields defaults
     * @return A configured instance, never null
     */
    public static OutOfOrderFeedback from(Map<String, Object> config) {
        if (config == null) {
            return new OutOfOrderFeedback(true, DEFAULT_TOO_EARLY, DEFAULT_ALREADY_PAST,
                DEFAULT_COOLDOWN_MS);
        }
        return new OutOfOrderFeedback(
            QuestComponentFactory.getBoolConfig(config, "out_of_order_feedback", true),
            QuestComponentFactory.getStringConfig(config, "out_of_order_message", DEFAULT_TOO_EARLY),
            QuestComponentFactory.getStringConfig(config, "already_past_message", DEFAULT_ALREADY_PAST),
            (long) QuestComponentFactory.getDoubleConfig(config, "out_of_order_cooldown_ms",
                DEFAULT_COOLDOWN_MS));
    }

    /**
     * Config keys this helper consumes, so components that validate their own keys can accept them
     * without reporting them as unknown.
     *
     * @return The recognised key names
     */
    public static java.util.Set<String> configKeys() {
        return java.util.Set.of("out_of_order_feedback", "out_of_order_message",
            "already_past_message", "out_of_order_cooldown_ms");
    }

    /**
     * Explains a state-gated no-op to the player, if there is anything worth saying.
     *
     * <p>Call this only after every <i>other</i> gate has passed — world, block type, coordinates.
     * A player clicking an unrelated lectern in another world must hear nothing; the message is
     * about being on the right object at the wrong time.</p>
     *
     * @param player   The interacting player
     * @param current  Their current state for this quest
     * @param required The state this component needs
     */
    public Outcome notifyWrongBeat(Player player, QuestState current, QuestState required) {
        if (!enabled || player == null || current == null || required == null) {
            return Outcome.SUPPRESSED_DISABLED;
        }
        if (current == required) return Outcome.SUPPRESSED_SAME_STATE;

        // Undiscovered: the leak guard. Reported separately from the other suppressions because
        // "stayed silent for a player who has not found this quest" is the behaviour most worth
        // being able to confirm, and it is indistinguishable from "nothing happened" without it.
        if (current == QuestState.NOT_STARTED) return Outcome.SUPPRESSED_NOT_STARTED;

        // Abandoned or admin-paused: off the track, not this mechanism's business.
        if (current == QuestState.ABANDONED || current == QuestState.PAUSED) {
            return Outcome.SUPPRESSED_OFF_TRACK;
        }

        boolean tooEarly = isBefore(current, required);
        String message = tooEarly ? tooEarlyMessage : alreadyPastMessage;
        if (message == null || message.isBlank()) return Outcome.SUPPRESSED_BLANK_MESSAGE;

        long now = System.currentTimeMillis();
        Long last = lastSpoken.get(player.getUniqueId());
        if (last != null && (now - last) < cooldownMs) return Outcome.SUPPRESSED_THROTTLED;
        lastSpoken.put(player.getUniqueId(), now);

        player.sendMessage(message.replace('&', '§'));
        return tooEarly ? Outcome.SENT_TOO_EARLY : Outcome.SENT_ALREADY_PAST;
    }

    /**
     * What {@link #notifyWrongBeat} decided, so the calling component can log it (#1930).
     *
     * <p>The message itself is {@code player.sendMessage} — a direct server-to-player send. It is
     * not an {@code AsyncPlayerChatEvent}, so the chat relay never sees it, it appears in no log,
     * and nothing about it is observable from console. That made the correct behaviour
     * unverifiable: a player at {@code NOT_STARTED} must be told <em>nothing</em>, and "no message"
     * and "no click" produce identical evidence. Three separate live QA attempts failed on exactly
     * that. Returning the decision lets the caller emit one debug line per interaction, which turns
     * silence into something you can read.</p>
     */
    public enum Outcome {
        /** Message sent: the player has not reached this beat. */
        SENT_TOO_EARLY,
        /** Message sent: the player is past this beat. */
        SENT_ALREADY_PAST,
        /** Feedback switched off, or a null argument. */
        SUPPRESSED_DISABLED,
        /** The player is exactly at the required state — the component itself should have fired. */
        SUPPRESSED_SAME_STATE,
        /** The leak guard: the player has not discovered this quest and is told nothing, ever. */
        SUPPRESSED_NOT_STARTED,
        /** Abandoned or paused — off the linear track. */
        SUPPRESSED_OFF_TRACK,
        /** The configured message for this direction is empty. */
        SUPPRESSED_BLANK_MESSAGE,
        /** Within the per-player cooldown; held right-click does not spam. */
        SUPPRESSED_THROTTLED
    }

    /**
     * Orders the two states along the quest track.
     *
     * <p>Uses an explicit rank rather than {@link Enum#ordinal()}: {@code ABANDONED} and
     * {@code PAUSED} sit after {@code COMPLETED} in the enum but are not later beats, and an
     * ordinal comparison would quietly call an abandoned quest "past" every gate on the track.
     * They are filtered out above; the rank keeps that from mattering if the filter ever moves.</p>
     */
    private static boolean isBefore(QuestState current, QuestState required) {
        return rank(current) < rank(required);
    }

    private static int rank(QuestState state) {
        return switch (state) {
            case NOT_STARTED -> 0;
            case TRIGGER_FOUND -> 1;
            case QUEST_ACTIVE -> 2;
            case OBJECTIVE_FOUND -> 3;
            case COMPLETED -> 4;
            // Off-track. Ranked last so they never read as "too early" for anything.
            case ABANDONED, PAUSED -> Integer.MAX_VALUE;
        };
    }
}
