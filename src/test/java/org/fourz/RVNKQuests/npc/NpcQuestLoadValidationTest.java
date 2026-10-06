package org.fourz.RVNKQuests.npc;

import org.bukkit.event.Listener;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.QuestYamlRepository;
import org.fourz.RVNKQuests.data.dto.QuestDTO;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.objective.generic.GenericTalkToObjective;
import org.fourz.RVNKQuests.placeholder.QuestStepModel;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.trigger.generic.GenericNpcInteractTrigger;
import org.fourz.rvnkcore.api.model.NpcRef;
import org.fourz.rvnkcore.api.service.INpcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Quest-load validation of {@code npc_key}, and the shipped sample quest (#2214).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NPC quest-load validation (#2214)")
class NpcQuestLoadValidationTest {

    @Mock private RVNKQuests plugin;
    @Mock private DataDrivenQuest quest;

    @BeforeEach
    void setUp() {
        when(quest.getId()).thenReturn("npc_bad_quest");
    }

    private static QuestDTO definition(String id, Map<String, Object> metadata) {
        return new QuestDTO(id, "Quest " + id, "", null, false, 0, null, null, null, null, metadata);
    }

    private static Map<String, Object> component(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    @Test
    @DisplayName("an invalid npc_key logs a warning naming the quest, and the quest still loads")
    void invalidKeyWarnsAndQuestLoads() {
        Map<String, Object> components = new HashMap<>();
        components.put("trig_bad", component("type", "NPC_INTERACT", "npc_key", "Bad Key!"));
        components.put("trig_ok", component("type", "NPC_INTERACT", "npc_key", "archivist"));
        Map<String, Object> metadata = Map.of(
            "components", components,
            "state_mapping", Map.of("NOT_STARTED", List.of("trig_bad", "trig_ok")));

        QuestComponentFactory factory = new QuestComponentFactory(plugin, quest);
        List<Listener> listeners;
        try (NpcTestSupport.WarningCapture warnings = new NpcTestSupport.WarningCapture()) {
            listeners = assertDoesNotThrow(() ->
                factory.createListenersForState(QuestState.NOT_STARTED, definition("npc_bad_quest", metadata)));
            assertTrue(warnings.messages().stream().anyMatch(m ->
                    m.contains("npc_bad_quest") && m.contains("NPC_INTERACT") && m.contains("Bad Key!")),
                "expected a warning naming the quest and the bad key, got " + warnings.messages());
        }

        assertEquals(2, listeners.size(), "the bad component is inert, the good one is untouched");
        assertTrue(factory.getComponentFailures().isEmpty(), "an invalid key is not a construction crash");
        long valid = listeners.stream()
            .filter(l -> l instanceof NpcQuestComponent c && c.hasValidKey()).count();
        assertEquals(1, valid);
    }

    @Test
    @DisplayName("quest component add rejects a bad npc_key at author time")
    void validateComponentConfigRejectsBadKey() {
        QuestComponentFactory factory = new QuestComponentFactory(plugin, quest);

        assertNotNull(factory.validateComponentConfig("t", component("type", "NPC_INTERACT")));
        assertNotNull(factory.validateComponentConfig("o",
            component("objective_type", "TALK_TO", "npc_key", "has space")));
        assertNull(factory.validateComponentConfig("t",
            component("type", "NPC_INTERACT", "npc_key", "archivist")));
        assertNull(factory.validateComponentConfig("o",
            component("objective_type", "TALK_TO", "npc_key", "Courier")));
    }

    @Test
    @DisplayName("a key no NPC carries gets one warning naming the quest")
    void unknownKeyWarning() {
        GenericNpcInteractTrigger known = new GenericNpcInteractTrigger(plugin, quest,
            component("type", "NPC_INTERACT", "npc_key", "archivist"));
        GenericTalkToObjective unknown = new GenericTalkToObjective(plugin, quest,
            component("objective_type", "TALK_TO", "npc_key", "courier"));
        INpcService service = mock(INpcService.class);
        when(service.isAvailable()).thenReturn(true);
        when(service.findByKey(anyString())).thenReturn(Optional.empty());
        when(service.findByKey("archivist")).thenReturn(Optional.of(mock(NpcRef.class)));

        List<String> warnings = NpcInteractionCoordinator.unknownKeyWarnings(List.of(known, unknown), service);

        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("npc_bad_quest"));
        assertTrue(warnings.get(0).contains("courier"));
    }

    @Test
    @DisplayName("no NPC service: ONE warning for the plugin's lifetime, not one per component")
    void unavailableWarnsOnce() {
        GenericNpcInteractTrigger a = new GenericNpcInteractTrigger(plugin, quest,
            component("type", "NPC_INTERACT", "npc_key", "archivist"));
        GenericTalkToObjective b = new GenericTalkToObjective(plugin, quest,
            component("objective_type", "TALK_TO", "npc_key", "courier"));
        List<String> warned = new ArrayList<>();
        INpcService unavailable = mock(INpcService.class);
        when(unavailable.isAvailable()).thenReturn(false);

        NpcInteractionCoordinator coordinator = new NpcInteractionCoordinator(
            () -> List.of(a, b), () -> unavailable,
            (k, c) -> CompletableFuture.completedFuture(Optional.empty()),
            Runnable::run, Runnable::run, warned::add, m -> { });

        coordinator.requestKeyValidation();
        coordinator.requestKeyValidation();
        coordinator.validateKeysNow();

        assertEquals(1, warned.size(), "got " + warned);
        assertTrue(warned.get(0).contains("npc_bad_quest"));
    }

    @Test
    @DisplayName("a null NPC service (RVNKCore not ready) is treated as unavailable")
    void nullServiceIsUnavailable() {
        GenericNpcInteractTrigger a = new GenericNpcInteractTrigger(plugin, quest,
            component("type", "NPC_INTERACT", "npc_key", "archivist"));
        List<String> warned = new ArrayList<>();
        NpcInteractionCoordinator coordinator = new NpcInteractionCoordinator(
            () -> List.of(a), () -> null,
            (k, c) -> CompletableFuture.completedFuture(Optional.empty()),
            Runnable::run, Runnable::run, warned::add, m -> { });

        assertDoesNotThrow(coordinator::validateKeysNow);
        assertEquals(1, warned.size());
    }

    @Test
    @DisplayName("the shipped sample quest loads through the YAML repository with valid NPC components")
    void sampleQuestLoads(@TempDir Path dataFolder) throws Exception {
        Path questsDir = Files.createDirectories(dataFolder.resolve("quests"));
        Files.copy(Path.of("quests", "npc_courier_errand.yml"), questsDir.resolve("npc_courier_errand.yml"));
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());

        QuestDTO def = new QuestYamlRepository(plugin).findById("npc_courier_errand").join().orElseThrow();
        when(quest.getId()).thenReturn(def.questId());

        QuestComponentFactory factory = new QuestComponentFactory(plugin, quest);
        List<Listener> start = factory.createListenersForState(QuestState.NOT_STARTED, def);
        List<Listener> active = factory.createListenersForState(QuestState.QUEST_ACTIVE, def);

        assertEquals(1, start.size());
        GenericNpcInteractTrigger archivist = assertInstanceOf(GenericNpcInteractTrigger.class, start.get(0));
        assertEquals("archivist", archivist.getNpcKey());
        assertEquals(QuestState.QUEST_ACTIVE, archivist.getAdvanceState());

        assertEquals(1, active.size());
        GenericTalkToObjective courier = assertInstanceOf(GenericTalkToObjective.class, active.get(0));
        assertEquals("courier", courier.getNpcKey());
        assertEquals(QuestState.COMPLETED, courier.getAdvanceState());
        assertEquals(QuestState.QUEST_ACTIVE, courier.getRequiredState(), "the bucket sets required_state");

        QuestStepModel model = QuestStepModel.from(def);
        assertEquals(1, model.totalSteps());
        assertEquals("0/1", model.stepsDone(QuestState.QUEST_ACTIVE) + "/" + model.totalSteps());
        assertEquals("Find the courier and pass on the archivist's message",
            model.objectiveFor(QuestState.QUEST_ACTIVE));
    }
}
