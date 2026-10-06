package org.fourz.RVNKQuests.placeholder;

import org.fourz.RVNKQuests.data.dto.QuestDTO;
import org.fourz.RVNKQuests.quest.QuestState;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What the placeholders need to know about one quest definition (#2214): its name, its
 * objective steps and the text of each step.
 *
 * <h2>What an "objective" is here</h2>
 *
 * <p>A data-driven quest has no objective list; its beats are the components in
 * {@code state_mapping}. Each non-empty bucket for {@code TRIGGER_FOUND}, {@code QUEST_ACTIVE} and
 * {@code OBJECTIVE_FOUND} is one objective step: it is what the player must do next while in that
 * state. The {@code NOT_STARTED} bucket holds the start trigger and is not an objective.</p>
 *
 * <ul>
 *   <li><b>total</b> = the number of those non-empty buckets.</li>
 *   <li><b>done</b> for a state = the number of those buckets that come before the state; all of
 *       them for COMPLETED.</li>
 *   <li><b>objective text</b> for a state = the {@code description} of the first component in that
 *       state's bucket that has one; a TALK_TO component without one reads
 *       {@code Talk to <npc_key>}.</li>
 * </ul>
 *
 * <p>Immutable once built, so it is safe to read from any thread. No Bukkit types.</p>
 */
public final class QuestStepModel {

    /** The objective states, in progression order. */
    static final List<QuestState> STEP_STATES =
        List.of(QuestState.TRIGGER_FOUND, QuestState.QUEST_ACTIVE, QuestState.OBJECTIVE_FOUND);

    private final String name;
    private final List<QuestState> steps;
    private final Map<QuestState, String> objectiveText;

    private QuestStepModel(String name, List<QuestState> steps, Map<QuestState, String> objectiveText) {
        this.name = name;
        this.steps = List.copyOf(steps);
        this.objectiveText = Map.copyOf(objectiveText);
    }

    /** A model with a name and no steps, for quests that are not data-driven. */
    public static QuestStepModel nameOnly(String name) {
        return new QuestStepModel(name, List.of(), Map.of());
    }

    /** Builds the model from a definition's {@code state_mapping} and {@code components}. */
    public static QuestStepModel from(QuestDTO definition) {
        if (definition == null) return nameOnly(null);
        Map<String, Object> metadata = definition.metadata();
        Object mappingObj = metadata != null ? metadata.get("state_mapping") : null;
        Object componentsObj = metadata != null ? metadata.get("components") : null;
        if (!(mappingObj instanceof Map<?, ?> mapping)) {
            return nameOnly(definition.name());
        }
        Map<?, ?> components = componentsObj instanceof Map<?, ?> m ? m : Map.of();

        java.util.ArrayList<QuestState> steps = new java.util.ArrayList<>();
        Map<QuestState, String> texts = new EnumMap<>(QuestState.class);
        for (QuestState state : STEP_STATES) {
            Object idsObj = mapping.get(state.name());
            if (!(idsObj instanceof List<?> ids) || ids.isEmpty()) continue;
            steps.add(state);
            String text = firstDescription(ids, components);
            if (text != null) texts.put(state, text);
        }
        return new QuestStepModel(definition.name(), steps, texts);
    }

    private static String firstDescription(List<?> componentIds, Map<?, ?> components) {
        String fallback = null;
        for (Object id : componentIds) {
            if (!(components.get(String.valueOf(id)) instanceof Map<?, ?> config)) continue;
            Object description = config.get("description");
            if (description != null && !String.valueOf(description).isBlank()) {
                return String.valueOf(description);
            }
            if (fallback == null && "TALK_TO".equals(String.valueOf(config.get("objective_type")))
                    && config.get("npc_key") != null) {
                fallback = "Talk to " + String.valueOf(config.get("npc_key")).trim();
            }
        }
        return fallback;
    }

    public String name() {
        return name;
    }

    /** Number of objective steps; 0 when the quest has no state_mapping steps. */
    public int totalSteps() {
        return steps.size();
    }

    /** Steps finished for a player in {@code state}. */
    public int stepsDone(QuestState state) {
        if (state == null) return 0;
        if (state == QuestState.COMPLETED) return steps.size();
        int rank = STEP_STATES.indexOf(state);
        if (rank < 0) return 0;
        int done = 0;
        for (QuestState step : steps) {
            if (STEP_STATES.indexOf(step) < rank) done++;
        }
        return done;
    }

    /** The objective text for a player in {@code state}, or null. */
    public String objectiveFor(QuestState state) {
        return state == null ? null : objectiveText.get(state);
    }
}
