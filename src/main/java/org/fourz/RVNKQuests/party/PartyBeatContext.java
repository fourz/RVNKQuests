package org.fourz.RVNKQuests.party;

import org.bukkit.Location;
import org.fourz.RVNKQuests.quest.QuestState;

/**
 * The checkpoint a quest beat fired at, carried from the firing component into the party fan-out.
 *
 * <p>{@code AbstractQuest} cannot apply the presence rule on its own — it has no idea where the
 * beat happened or how big its trigger radius is. Only the component knows, so the component
 * builds this at fire time and passes it through the new
 * {@code advanceStateForPlayer(uuid, state, ctx)} overload. A null context means "no fan-out",
 * which is also what makes fan-out recursion structurally impossible: member advances always go
 * through the context-less path.</p>
 *
 * <p>{@code requiredState} is the component's own {@code required_state} — the beat's expected
 * starting point. The fan-out uses it as the <b>in-step gate</b>: a member whose state is not
 * exactly there is skipped, which is what turns "missed a beat" into "fall behind, catch up solo"
 * instead of leapfrogging, and what keeps the prerequisite gate airtight (a NOT_STARTED member can
 * only ever be fan-out-advanced through the NOT_STARTED&rarr;TRIGGER_FOUND edge — exactly the edge
 * the gate covers).</p>
 *
 * @param worldName     world the checkpoint is in (compared case-insensitively)
 * @param x             checkpoint x
 * @param y             checkpoint y
 * @param z             checkpoint z
 * @param baseRadius    the trigger's own radius; 0 is legal for radius-less beats (kills) — the
 *                      service applies its configured floor before multiplying
 * @param requiredState the state the firing component required — the in-step gate for members
 * @since 1.1.41 (#1982)
 */
public record PartyBeatContext(String worldName, double x, double y, double z,
                               double baseRadius, QuestState requiredState) {

    /** Convenience for components that already hold a resolved {@link Location}. */
    public static PartyBeatContext of(Location checkpoint, double baseRadius, QuestState requiredState) {
        return new PartyBeatContext(
                checkpoint.getWorld() != null ? checkpoint.getWorld().getName() : "",
                checkpoint.getX(), checkpoint.getY(), checkpoint.getZ(),
                baseRadius, requiredState);
    }
}
