package org.fourz.RVNKQuests.placeholder;

import org.fourz.RVNKQuests.data.dto.QuestDTO;
import org.fourz.RVNKQuests.data.dto.QuestProgressDTO;
import org.fourz.RVNKQuests.quest.QuestState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** {@code %rvnkquests_*%} values and fallbacks (#2214). */
@DisplayName("%rvnkquests_*% placeholders (#2214)")
class QuestPlaceholderResolverTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final String NONE = QuestPlaceholderResolver.NONE;

    /** The sample quest's shape: archivist starts it, TALK_TO courier completes it. */
    private static QuestDTO courierQuest() {
        Map<String, Object> components = new HashMap<>();
        components.put("trig_archivist", Map.of("type", "NPC_INTERACT", "npc_key", "archivist",
            "advance_state", "QUEST_ACTIVE"));
        components.put("obj_courier", Map.of("objective_type", "TALK_TO", "npc_key", "courier",
            "advance_state", "COMPLETED"));
        Map<String, Object> metadata = Map.of("components", components, "state_mapping", Map.of(
            "NOT_STARTED", List.of("trig_archivist"),
            "QUEST_ACTIVE", List.of("obj_courier")));
        return new QuestDTO("npc_courier_errand", "The Archivist's Errand", "", null, false, 0,
            null, null, null, null, metadata);
    }

    /** survey_trail's shape: three objective buckets with descriptions on two. */
    private static QuestDTO threeStepQuest() {
        Map<String, Object> components = new HashMap<>();
        components.put("trig", Map.of("type", "LOCATION_PROXIMITY"));
        components.put("a", Map.of("objective_type", "REACH", "description", "Cross to the dome"));
        components.put("b", Map.of("type", "LECTERN_BOOK_ON"));
        components.put("c", Map.of("objective_type", "REACH", "description", "Report back"));
        Map<String, Object> metadata = Map.of("components", components, "state_mapping", Map.of(
            "NOT_STARTED", List.of("trig"),
            "TRIGGER_FOUND", List.of("a"),
            "QUEST_ACTIVE", List.of("b"),
            "OBJECTIVE_FOUND", List.of("c")));
        return new QuestDTO("survey_trail", "The Surveyor's Trail", "", null, false, 0,
            null, null, null, null, metadata);
    }

    private static QuestProgressDTO row(String questId, QuestState state, Instant started) {
        return new QuestProgressDTO(PLAYER, questId, state, null, started, null, Map.of());
    }

    private final Map<String, QuestStepModel> registered = new HashMap<>(Map.of(
        "npc_courier_errand", QuestStepModel.from(courierQuest()),
        "survey_trail", QuestStepModel.from(threeStepQuest()),
        "legacy", QuestStepModel.nameOnly("Piglin Far From Home")));
    private final Function<String, QuestStepModel> quests = registered::get;

    private String resolve(String key, Collection<QuestProgressDTO> progress) {
        return QuestPlaceholderResolver.resolve(key, progress, quests);
    }

    @Test
    @DisplayName("active quest: name, n/m progress and the TALK_TO default objective text")
    void activeQuestValues() {
        List<QuestProgressDTO> progress = List.of(
            row("npc_courier_errand", QuestState.QUEST_ACTIVE, Instant.parse("2026-10-06T10:00:00Z")));

        assertEquals("The Archivist's Errand", resolve("active_name", progress));
        assertEquals("The Archivist's Errand", resolve("active", progress), "alias from the #2214 acceptance");
        assertEquals("0/1", resolve("active_progress", progress));
        assertEquals("Talk to courier", resolve("active_objective", progress));
    }

    @Test
    @DisplayName("n/m counts state_mapping buckets passed; description is read per state")
    void stepsAcrossBuckets() {
        assertEquals("0/3", resolve("active_progress", List.of(row("survey_trail", QuestState.TRIGGER_FOUND, Instant.now()))));
        assertEquals("1/3", resolve("active_progress", List.of(row("survey_trail", QuestState.QUEST_ACTIVE, Instant.now()))));
        assertEquals("2/3", resolve("active_progress", List.of(row("survey_trail", QuestState.OBJECTIVE_FOUND, Instant.now()))));
        assertEquals("Cross to the dome",
            resolve("active_objective", List.of(row("survey_trail", QuestState.TRIGGER_FOUND, Instant.now()))));
        assertEquals(NONE,
            resolve("active_objective", List.of(row("survey_trail", QuestState.QUEST_ACTIVE, Instant.now()))),
            "a bucket with no description gives -");
    }

    @Test
    @DisplayName("the most recently started in-progress quest is the active one")
    void mostRecentlyStartedWins() {
        List<QuestProgressDTO> progress = List.of(
            row("survey_trail", QuestState.QUEST_ACTIVE, Instant.parse("2026-10-01T00:00:00Z")),
            row("npc_courier_errand", QuestState.QUEST_ACTIVE, Instant.parse("2026-10-05T00:00:00Z")),
            row("legacy", QuestState.COMPLETED, Instant.parse("2026-10-06T00:00:00Z")));

        assertEquals("The Archivist's Errand", resolve("active_name", progress));
    }

    @Test
    @DisplayName("a row with no start time ranks below one with a start time; ties go to the lower id")
    void startTimeTieBreaks() {
        assertEquals("The Surveyor's Trail", resolve("active_name", List.of(
            row("npc_courier_errand", QuestState.QUEST_ACTIVE, null),
            row("survey_trail", QuestState.QUEST_ACTIVE, Instant.EPOCH))));
        Instant same = Instant.parse("2026-10-05T00:00:00Z");
        assertEquals("The Archivist's Errand", resolve("active_name", List.of(
            row("survey_trail", QuestState.QUEST_ACTIVE, same),
            row("npc_courier_errand", QuestState.QUEST_ACTIVE, same))));
    }

    @Test
    @DisplayName("progress on a quest not registered here is ignored")
    void unregisteredQuestIgnored() {
        assertEquals(NONE, resolve("active_name",
            List.of(row("removed_quest", QuestState.QUEST_ACTIVE, Instant.now()))));
    }

    @Test
    @DisplayName("completed_count counts COMPLETED rows; 0 when loaded with none")
    void completedCount() {
        assertEquals("2", resolve("completed_count", List.of(
            row("a", QuestState.COMPLETED, Instant.now()),
            row("b", QuestState.COMPLETED, Instant.now()),
            row("survey_trail", QuestState.QUEST_ACTIVE, Instant.now()))));
        assertEquals("0", resolve("completed_count", List.of()));
    }

    @Test
    @DisplayName("every key falls back to - with no data")
    void fallbacks() {
        for (String key : List.of("active", "active_name", "active_progress", "active_objective", "completed_count")) {
            assertEquals(NONE, resolve(key, null), key + " with progress not loaded");
        }
        for (String key : List.of("active", "active_name", "active_progress", "active_objective")) {
            assertEquals(NONE, resolve(key, List.of()), key + " with no active quest");
        }
        List<QuestProgressDTO> done = List.of(row("npc_courier_errand", QuestState.COMPLETED, Instant.now()));
        assertEquals(NONE, resolve("active_name", done), "a completed quest is not active");
        assertEquals(NONE, resolve("unknown_key", done));
        assertEquals(NONE, resolve(null, done));
        assertEquals(NONE, QuestPlaceholderResolver.resolve("active_name", done, null));
    }

    @Test
    @DisplayName("a quest with no state_mapping steps has a name but no n/m")
    void legacyQuestHasNoProgress() {
        List<QuestProgressDTO> progress = List.of(row("legacy", QuestState.QUEST_ACTIVE, Instant.now()));
        assertEquals("Piglin Far From Home", resolve("active_name", progress));
        assertEquals(NONE, resolve("active_progress", progress));
        assertEquals(NONE, resolve("active_objective", progress));
    }

    @Test
    @DisplayName("null rows and odd keys never throw")
    void nullSafety() {
        List<QuestProgressDTO> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(row("survey_trail", QuestState.QUEST_ACTIVE, Instant.now()));
        assertEquals("The Surveyor's Trail", resolve(" ACTIVE_NAME ", withNull));
        assertEquals("1", resolve("completed_count", List.of(row("x", QuestState.COMPLETED, null))));
    }

    @Test
    @DisplayName("source: a null player (Citizens names and holograms) gives -")
    void nullPlayerGivesDash() {
        QuestPlaceholderSource source = new QuestPlaceholderSource(
            uuid -> { throw new AssertionError("must not read progress for a null player"); }, quests);
        assertEquals(NONE, source.resolve(null, "active_name"));
        assertEquals(NONE, source.resolve(null, "completed_count"));
    }

    @Test
    @DisplayName("source: a player whose progress is not in memory gives -, and a failing source is contained")
    void sourceFallbacks() {
        QuestPlaceholderSource notLoaded = new QuestPlaceholderSource(uuid -> null, quests);
        assertEquals(NONE, notLoaded.resolve(PLAYER, "completed_count"));

        QuestPlaceholderSource failing = new QuestPlaceholderSource(
            uuid -> { throw new IllegalStateException("boom"); }, quests);
        assertEquals(NONE, failing.resolve(PLAYER, "active_name"));
    }
}
