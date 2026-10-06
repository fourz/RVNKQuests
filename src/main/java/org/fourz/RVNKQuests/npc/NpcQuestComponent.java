package org.fourz.RVNKQuests.npc;

import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent;

/**
 * A quest component bound to an RVNK NPC key: {@code NPC_INTERACT} or {@code TALK_TO} (#2214).
 *
 * <p>{@link NpcInteractionCoordinator} finds these by walking the live quest list, the same way
 * {@code WorldEventScheduler} finds WORLD_EVENT triggers, so there is no registry to go stale on
 * a quest reload.</p>
 */
public interface NpcQuestComponent {

    /** The quest this component belongs to. */
    DataDrivenQuest getQuest();

    /** The normalised NPC key, or null when the configured key was invalid. */
    String getNpcKey();

    /** True when the configured key passed {@link NpcKeyRules#isValid}. */
    default boolean hasValidKey() {
        return getNpcKey() != null;
    }

    /** True for NPC_INTERACT (offers a quest), false for TALK_TO (an objective). */
    boolean isTrigger();

    /** The config type name, for warnings: {@code NPC_INTERACT} or {@code TALK_TO}. */
    String getTypeName();

    /** True when this component reacts to the click type. */
    boolean acceptsClick(RvnkNpcInteractEvent.ClickType click);

    /** True when the event is for this component's key and click type. */
    default boolean matches(RvnkNpcInteractEvent event) {
        return event != null && hasValidKey()
            && NpcKeyRules.matches(getNpcKey(), event.getNpcKey())
            && acceptsClick(event.getClickType());
    }
}
