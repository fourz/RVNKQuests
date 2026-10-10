package org.fourz.RVNKQuests.placeholder;

import org.fourz.RVNKQuests.data.dto.QuestProgressDTO;
import org.fourz.RVNKQuests.quest.QuestState;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Resolves {@code %rvnkquests_*%} keys for one player (#2214).
 *
 * <p>Holds no PlaceholderAPI types, so it loads and tests without PlaceholderAPI. It reads only
 * what the caller passes: the player's in-memory progress and a quest lookup. It never touches
 * the database.</p>
 *
 * <h2>Keys</h2>
 * <ul>
 *   <li>{@code active_name} (alias {@code active}) — name of the player's active quest</li>
 *   <li>{@code active_progress} — {@code n/m} objective steps of the active quest</li>
 *   <li>{@code active_objective} — the current objective text of the active quest</li>
 *   <li>{@code completed_count} — number of quests the player has completed</li>
 * </ul>
 *
 * <h2>Which quest is "active"</h2>
 *
 * <p>RVNKQuests has no "tracked quest" setting, so the active quest is the <b>most recently
 * started</b> quest that is in progress (TRIGGER_FOUND, QUEST_ACTIVE or OBJECTIVE_FOUND) and still
 * registered on this server. Most recent = latest {@code startedAt}; a row with no start time
 * ranks below any row with one; ties go to the lower quest id.</p>
 *
 * <p>Every key returns {@link #NONE} when there is no data: no player, progress not loaded, no
 * active quest, a quest without steps, or an unknown key. Nothing here returns null or throws.</p>
 */
public final class QuestPlaceholderResolver {

    /** Returned for no player, no data or an unknown key. */
    public static final String NONE = "-";

    private QuestPlaceholderResolver() {}

    /**
     * @param params   the key after {@code %rvnkquests_}
     * @param progress the player's cached progress rows, or null when not loaded (or no player)
     * @param quests   quest id to model; returns null for a quest that is not registered
     * @return the value, never null
     */
    public static String resolve(String params, Collection<QuestProgressDTO> progress,
                                 Function<String, QuestStepModel> quests) {
        try {
            if (params == null || progress == null || quests == null) return NONE;
            String key = params.trim().toLowerCase(Locale.ROOT);
            switch (key) {
                case "completed_count":
                    return String.valueOf(progress.stream()
                        .filter(p -> p != null && p.state() == QuestState.COMPLETED)
                        .count());
                case "active":
                case "active_name": {
                    Optional<Active> active = selectActive(progress, quests);
                    return active.map(a -> text(a.model().name())).orElse(NONE);
                }
                case "active_progress": {
                    Optional<Active> active = selectActive(progress, quests);
                    if (active.isEmpty() || active.get().model().totalSteps() == 0) return NONE;
                    QuestStepModel model = active.get().model();
                    return model.stepsDone(active.get().state()) + "/" + model.totalSteps();
                }
                case "active_objective": {
                    Optional<Active> active = selectActive(progress, quests);
                    return active.map(a -> text(a.model().objectiveFor(a.state()))).orElse(NONE);
                }
                default:
                    return NONE;
            }
        } catch (RuntimeException e) {
            return NONE;
        }
    }

    /** The active quest and the player's state in it. */
    record Active(String questId, QuestState state, QuestStepModel model) {}

    /** The most recently started in-progress quest that is registered; see the class note. */
    static Optional<Active> selectActive(Collection<QuestProgressDTO> progress,
                                         Function<String, QuestStepModel> quests) {
        Comparator<QuestProgressDTO> byStart = Comparator.comparing(
            QuestProgressDTO::startedAt, Comparator.nullsFirst(Comparator.<Instant>naturalOrder()));
        Comparator<QuestProgressDTO> byIdDesc = Comparator.comparing(QuestProgressDTO::questId).reversed();
        return progress.stream()
            .filter(Objects::nonNull)
            .filter(p -> isInProgress(p.state()))
            .filter(p -> quests.apply(p.questId()) != null)
            .max(byStart.thenComparing(byIdDesc))
            .map(p -> new Active(p.questId(), p.state(), quests.apply(p.questId())));
    }

    static boolean isInProgress(QuestState state) {
        return state == QuestState.TRIGGER_FOUND
            || state == QuestState.QUEST_ACTIVE
            || state == QuestState.OBJECTIVE_FOUND;
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? NONE : value;
    }
}
