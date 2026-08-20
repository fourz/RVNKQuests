package org.fourz.RVNKQuests.util;

import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;

import java.util.Map;

/**
 * Tells a player that a beat they just completed actually landed.
 *
 * <p>The success mirror of {@link OutOfOrderFeedback}. Before this, a quest could tell a player
 * they were <i>too early</i> for a beat but never that they had <i>finished</i> one: the only
 * player-facing messages in the whole state machine were {@code [Quest Started]} and
 * {@code [Quest Completed]}, both in {@code AbstractQuest}. A four-beat quest spoke on one beat.
 * Every advance in between was silent, and to a player that is indistinguishable from a quest
 * that is broken — a REACH beat especially, because there is no in-world event to attribute the
 * progress to. You walk over an invisible line and nothing happens. (#2024/#2025)</p>
 *
 * <h3>Silent unless authored</h3>
 * <p>With no {@code advance_message} the component behaves exactly as it did before — no text, no
 * sound, nothing. That is what lets this be added to every component at once without changing the
 * meaning of any existing quest. A beat speaks only when someone writes it a line.</p>
 *
 * <h3>Where to call it</h3>
 * <p>Only on the success path, after every gate the component owns has passed — world, block type,
 * coordinates, {@code required_state}, path choice. Firing alongside
 * {@link OutOfOrderFeedback#notifyWrongBeat} would tell a player both that they were too early and
 * that they succeeded.</p>
 *
 * <h3>Known limit</h3>
 * <p>This fires where the component <i>decides</i> to advance, which is not quite the same as the
 * write landing. {@code AbstractQuest}'s monotonic guard can still reject a change that would move
 * the quest backwards — if two components fire in the same tick, the loser is rejected silently and
 * will have spoken anyway. That race is rare and the message is cosmetic, which is why this sits at
 * the call site rather than deep in the write chain where the per-component config is not
 * reachable.</p>
 *
 * @since 1.1.47
 */
public final class AdvanceFeedback {

    /** Played when a beat lands and the author has not chosen a different cue. */
    private static final String DEFAULT_SOUND = "minecraft:entity.experience_orb.pickup";

    /** Sound name that means "text only". */
    private static final String NO_SOUND = "none";

    private static final float DEFAULT_VOLUME = 0.7f;
    private static final float DEFAULT_PITCH = 1.2f;

    private final boolean enabled;
    private final String message;
    private final String sound;
    private final float volume;
    private final float pitch;

    private AdvanceFeedback(boolean enabled, String message, String sound, float volume, float pitch) {
        this.enabled = enabled;
        this.message = message;
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
    }

    /**
     * Builds the feedback for one component from its config block.
     *
     * <p>Config keys — all optional:</p>
     * <ul>
     *   <li>{@code advance_message} — the line to send. Absent or blank means this component stays
     *       silent, which is the default and matches pre-#2025 behaviour. {@code &} colour codes.</li>
     *   <li>{@code advance_sound} — namespaced sound key, or {@code none} for text only
     *       (default: {@value #DEFAULT_SOUND})</li>
     *   <li>{@code advance_volume} / {@code advance_pitch} — cue shaping</li>
     *   <li>{@code advance_feedback} — false to silence this component outright (default: true)</li>
     * </ul>
     *
     * @param config the component's config map; null yields a silent instance
     * @return a configured instance, never null
     */
    public static AdvanceFeedback from(Map<String, Object> config) {
        if (config == null) {
            // Enabled with nothing to say, not disabled. Both are silent, but the outcome is
            // reported and "someone switched this off" is a different fact from "nobody wrote it
            // a line" — a distinction worth keeping when reading a log.
            return new AdvanceFeedback(true, null, NO_SOUND, DEFAULT_VOLUME, DEFAULT_PITCH);
        }
        return new AdvanceFeedback(
            QuestComponentFactory.getBoolConfig(config, "advance_feedback", true),
            QuestComponentFactory.getStringConfig(config, "advance_message", null),
            QuestComponentFactory.getStringConfig(config, "advance_sound", DEFAULT_SOUND),
            (float) QuestComponentFactory.getDoubleConfig(config, "advance_volume", DEFAULT_VOLUME),
            (float) QuestComponentFactory.getDoubleConfig(config, "advance_pitch", DEFAULT_PITCH));
    }

    /**
     * Config keys this helper consumes, so components that validate their own keys can accept them
     * without reporting them as unknown.
     *
     * @return the recognised key names
     */
    public static java.util.Set<String> configKeys() {
        return java.util.Set.of("advance_feedback", "advance_message", "advance_sound",
            "advance_volume", "advance_pitch");
    }

    /** True when this component has been given something to say. */
    public boolean isConfigured() {
        return enabled && message != null && !message.isBlank();
    }

    /**
     * Sends the beat's line and plays its cue.
     *
     * <p>No cooldown, deliberately. Unlike {@link OutOfOrderFeedback} — which fires on repeated
     * failed interactions and needs a throttle — an advance happens once per beat per player,
     * because the state gate that let it through no longer matches afterwards.</p>
     *
     * @param player the player who completed the beat
     * @return what was actually done, so a silent outcome is distinguishable from a call that
     *         never happened (#1930)
     */
    public Outcome notifyAdvanced(Player player) {
        if (!enabled) return Outcome.SUPPRESSED_DISABLED;
        if (player == null) return Outcome.SUPPRESSED_NO_PLAYER;
        if (message == null || message.isBlank()) return Outcome.SUPPRESSED_NO_MESSAGE;

        player.sendMessage(message.replace('&', '§'));

        if (sound != null && !sound.isBlank() && !NO_SOUND.equalsIgnoreCase(sound)) {
            try {
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (Exception e) {
                // A bad sound name must never cost the player the message they were owed.
                return Outcome.SENT_WITHOUT_SOUND;
            }
        }
        return Outcome.SENT;
    }

    /** What {@link #notifyAdvanced} did, for logging. */
    public enum Outcome {
        SENT,
        SENT_WITHOUT_SOUND,
        SUPPRESSED_DISABLED,
        SUPPRESSED_NO_PLAYER,
        SUPPRESSED_NO_MESSAGE
    }
}
