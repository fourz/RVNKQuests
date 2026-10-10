package org.fourz.RVNKQuests.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.dto.QuestDTO;
import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.quest.QuestNotifyPolicy;
import org.fourz.RVNKQuests.reward.OnAdvanceRewards;
import org.fourz.RVNKQuests.service.OnceRewards;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@code notify}, {@code on_advance} and {@code once} survive import (YAML to DTO), the database
 * (metadata JSON), and export (DTO to YAML) unchanged (#2266-#2268).
 */
@DisplayName("YAML round trip for notify, on_advance and once (#2266-#2268)")
class QuestYamlRoundTripTest {

    private static final String YAML = """
        quest_id: tfah_cavern
        name: The Cavern Wakes
        description: Wake the well.
        repeatable: false
        cooldown_minutes: 0
        metadata:
          notify:
            start_popup: true
            complete_popup: true
            broadcast: false
          components:
            trig_well:
              type: NPC_INTERACT
              npc_key: well_keeper
              advance_state: QUEST_ACTIVE
              on_advance:
              - reward_id: b_xp
                type: EXPERIENCE
                amount: 25
              - reward_id: a_key
                type: COMMAND
                value: lore item give %player% Lodestone Key
              - reward_id: c_bell
                type: COMMAND
                value: playsound minecraft:block.bell.use master @a 0 64 0
                once: server
            obj_well:
              objective_type: TALK_TO
              npc_key: well_keeper
              advance_state: COMPLETED
          state_mapping:
            NOT_STARTED:
            - trig_well
            QUEST_ACTIVE:
            - obj_well
        rewards:
          cavern_wake:
            type: COMMAND
            value: setblock 10 60 10 minecraft:air
            amount: 1
            once: server
          cavern_xp:
            type: EXPERIENCE
            value: points
            amount: 50
        """;

    private static QuestDTO load(Path dataFolder) {
        RVNKQuests plugin = mock(RVNKQuests.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        return new QuestYamlRepository(plugin).findById("tfah_cavern").join().orElseThrow();
    }

    private static QuestDTO throughDatabase(QuestDTO quest) {
        // What QuestRepositoryImpl does: metadata and reward metadata are stored as JSON with Gson
        // and read back with TypeToken<Map<String, Object>> / <Map<String, String>>.
        Gson gson = new Gson();
        Type objMap = new TypeToken<Map<String, Object>>() { }.getType();
        Type strMap = new TypeToken<Map<String, String>>() { }.getType();
        Map<String, Object> metadata = gson.fromJson(gson.toJson(quest.metadata()), objMap);
        List<RewardDTO> rewards = quest.rewards().stream()
            .map(r -> new RewardDTO(r.rewardId(), r.type(), r.value(), r.amount(), r.description(),
                r.metadata().isEmpty() ? Map.of() : gson.fromJson(gson.toJson(r.metadata()), strMap)))
            .toList();
        return new QuestDTO(quest.questId(), quest.name(), quest.description(), quest.category(),
            quest.repeatable(), quest.cooldownMinutes(), quest.objectives(), rewards, quest.prerequisites(),
            null, metadata);
    }

    @SuppressWarnings("unchecked")
    private static List<RewardDTO> onAdvance(QuestDTO quest) {
        Map<String, Object> components = (Map<String, Object>) quest.metadata().get("components");
        Map<String, Object> well = (Map<String, Object>) components.get("trig_well");
        return OnAdvanceRewards.parse("trig_well", well.get("on_advance"), null);
    }

    private static RewardDTO reward(QuestDTO quest, String id) {
        return quest.rewards().stream().filter(r -> r.rewardId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("import reads the new keys")
    void importReadsNewKeys(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("quests"));
        Files.writeString(dir.resolve("quests/tfah_cavern.yml"), YAML);
        QuestDTO quest = load(dir);

        assertEquals(new QuestNotifyPolicy(true, true, false), QuestNotifyPolicy.fromMetadata(quest.metadata()));
        List<RewardDTO> gives = onAdvance(quest);
        assertEquals(List.of("a_key", "b_xp", "c_bell"), gives.stream().map(RewardDTO::rewardId).toList());
        assertTrue(OnceRewards.isOnceServer(gives.get(2)));
        assertTrue(OnceRewards.isOnceServer(reward(quest, "cavern_wake")));
        assertFalse(OnceRewards.isOnceServer(reward(quest, "cavern_xp")));
    }

    @Test
    @DisplayName("import -> database -> export -> import gives the same quest")
    void roundTripIsSymmetric(@TempDir Path source, @TempDir Path exported) throws Exception {
        Files.createDirectories(source.resolve("quests"));
        Files.writeString(source.resolve("quests/tfah_cavern.yml"), YAML);
        QuestDTO imported = load(source);
        QuestDTO stored = throughDatabase(imported);

        // Export: the exporter writes the DTO the database returned.
        RVNKQuests plugin = mock(RVNKQuests.class);
        when(plugin.getDataFolder()).thenReturn(exported.toFile());
        assertTrue(new QuestYamlRepository(plugin).save(stored).join());
        String text = Files.readString(exported.resolve("quests/tfah_cavern.yml"));

        // once: server goes back on the reward itself, not under metadata.
        assertTrue(text.contains("once: server"), text);
        assertFalse(text.contains("metadata:\n      once"), text);

        QuestDTO reimported = load(exported);
        assertEquals(QuestNotifyPolicy.fromMetadata(imported.metadata()),
            QuestNotifyPolicy.fromMetadata(reimported.metadata()));
        assertEquals(onAdvance(imported), onAdvance(reimported));
        assertEquals(reward(imported, "cavern_wake"), reward(reimported, "cavern_wake"));
        assertEquals(reward(imported, "cavern_xp"), reward(reimported, "cavern_xp"));

        // And a second export of the re-import is byte-identical: the format is a fixed point.
        when(plugin.getDataFolder()).thenReturn(exported.toFile());
        assertTrue(new QuestYamlRepository(plugin).save(throughDatabase(reimported)).join());
        assertEquals(text, Files.readString(exported.resolve("quests/tfah_cavern.yml")));
    }
}
