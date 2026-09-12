package org.fourz.RVNKQuests.trigger.generic;

import org.bukkit.event.Listener;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;

import java.util.Map;

/**
 * {@code COMMAND} trigger component — activation is command-driven, not listener-driven (#1017).
 *
 * <h2>Why a class with no event handlers</h2>
 *
 * <p>This registers no {@code @EventHandler}. That is the point: a {@code COMMAND} component is
 * fired by {@code /quest trigger <player> <quest>}, so there is no Bukkit event to listen for.
 * Bukkit's {@code registerEvents} on a listener with no handlers is a harmless no-op, and
 * {@code HandlerList.unregisterAll} on it likewise.</p>
 *
 * <p>The class exists because {@code QuestComponentFactory} previously returned {@code null} for
 * {@code COMMAND}, and a null component has consequences well beyond "the trigger does nothing":</p>
 *
 * <ul>
 *   <li>{@code state_mapping} could not reference it — {@code preflight} reports a component in no
 *       state as a blocker, and a component that is in a state but constructs to null is counted as
 *       a state with zero listeners, which is the "loads but does nothing" warning.</li>
 *   <li>{@code quest validate} could not distinguish "misconfigured" from "not implemented".</li>
 *   <li>{@code /quest debug coords} and {@code fire} had nothing to enumerate.</li>
 * </ul>
 *
 * <p>So the value here is that a quest can <b>declare</b> a command-driven beat and have every tool
 * in the chain see it, rather than the beat existing only as an operator convention.</p>
 *
 * <h2>Relationship to {@code /quest trigger}</h2>
 *
 * <p>The command surface — {@code <player>}, {@code --all}, {@code --world}, {@code --force},
 * gated on {@code rvnkquests.admin.trigger} — already shipped and is unchanged. It advances any
 * quest whether or not a {@code COMMAND} component is declared, and this class does not intercept
 * it. The declared {@code required_state} and {@code advance_state} are held here so tooling can
 * report the intended edge; {@code TriggerCommand} keeps its own documented behaviour
 * ({@code TRIGGER_FOUND}, or {@code QUEST_ACTIVE} under {@code --force}).</p>
 *
 * <h3>Config keys</h3>
 * <ul>
 *   <li>{@code required_state} — state the beat expects (default {@code NOT_STARTED}). Overridden
 *       at runtime by the {@code state_mapping} bucket, as for every component (#1764).</li>
 *   <li>{@code advance_state} — state the beat moves to (default {@code QUEST_ACTIVE})</li>
 * </ul>
 */
public class GenericCommandTrigger implements Listener {

    private final DataDrivenQuest quest;
    private final QuestState requiredState;
    private final QuestState advanceState;

    public GenericCommandTrigger(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config) {
        this.quest = quest;
        this.requiredState = parseState(
                QuestComponentFactory.getStringConfig(config, "required_state", "NOT_STARTED"),
                QuestState.NOT_STARTED);
        // QUEST_ACTIVE rather than TRIGGER_FOUND: a command trigger is an explicit operator
        // activation, and the spec's own example config advances to QUEST_ACTIVE.
        this.advanceState = parseState(
                QuestComponentFactory.getStringConfig(config, "advance_state", "QUEST_ACTIVE"),
                QuestState.QUEST_ACTIVE);
    }

    /** The state this beat expects, for tooling that reports the intended edge. */
    public QuestState getRequiredState() {
        return requiredState;
    }

    /** The state this beat advances to, for tooling that reports the intended edge. */
    public QuestState getAdvanceState() {
        return advanceState;
    }

    /** The quest this component belongs to. */
    public DataDrivenQuest getQuest() {
        return quest;
    }

    private static QuestState parseState(String name, QuestState fallback) {
        if (name == null) return fallback;
        for (QuestState state : QuestState.values()) {
            if (state.name().equalsIgnoreCase(name)) return state;
        }
        return fallback;
    }
}
