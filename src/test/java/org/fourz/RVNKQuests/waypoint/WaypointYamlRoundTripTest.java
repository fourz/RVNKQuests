package org.fourz.RVNKQuests.waypoint;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.QuestYamlRepository;
import org.fourz.RVNKQuests.data.dto.QuestDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** {@code waypoint} and {@code waypoints: auto} survive import, the database and export (#2264). */
@DisplayName("YAML round trip keeps waypoint (#2264)")
class WaypointYamlRoundTripTest {

    private static final String YAML = """
        quest_id: tfah_wp
        name: The Gold Door
        description: Find the key.
        repeatable: false
        cooldown_minutes: 0
        metadata:
          waypoints: auto
          components:
            trig_rell:
              type: NPC_INTERACT
              npc_key: guide_aether
              advance_state: QUEST_ACTIVE
            obj_lodestone_key:
              objective_type: INTERACT
              block_type: LODESTONE
              advance_state: OBJECTIVE_FOUND
              waypoint:
                world: sotw_sky_0
                x: -421
                y: 98
                z: 19
                label: The gold door
                style: compass
            trig_well:
              type: LOCATION_PROXIMITY
              world: sotw_city
              x: 8
              y: 64
              z: 8
              radius: 4
              description: The collapsed well
              advance_state: COMPLETED
            trig_quiet:
              type: LOCATION_PROXIMITY
              world: sotw_city
              x: 50
              z: 50
              waypoint: off
          state_mapping:
            NOT_STARTED:
            - trig_rell
            QUEST_ACTIVE:
            - obj_lodestone_key
            - trig_quiet
            OBJECTIVE_FOUND:
            - trig_well
        """;

    private static QuestDTO load(Path dataFolder) {
        RVNKQuests plugin = mock(RVNKQuests.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        return new QuestYamlRepository(plugin).findById("tfah_wp").join().orElseThrow();
    }

    /** What QuestRepositoryImpl does to metadata: Gson to JSON and back. */
    private static QuestDTO throughDatabase(QuestDTO quest) {
        Gson gson = new Gson();
        Type objMap = new TypeToken<Map<String, Object>>() { }.getType();
        Map<String, Object> metadata = gson.fromJson(gson.toJson(quest.metadata()), objMap);
        return new QuestDTO(quest.questId(), quest.name(), quest.description(), quest.category(),
            quest.repeatable(), quest.cooldownMinutes(), quest.objectives(), quest.rewards(), quest.prerequisites(),
            null, metadata);
    }

    @Test
    @DisplayName("import reads the authored block and the auto-derived one")
    void importReads(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("quests"));
        Files.writeString(dir.resolve("quests/tfah_wp.yml"), YAML);
        QuestDTO quest = load(dir);
        List<String> problems = new ArrayList<>();
        Map<String, Waypoint> all = WaypointParser.parseAll(quest.metadata(), problems);
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(List.of("obj_lodestone_key", "trig_well"), new ArrayList<>(all.keySet()));
        assertEquals(new Waypoint("obj_lodestone_key", "sotw_sky_0", -421, 98, 19, "The gold door",
            WaypointStyle.COMPASS, false), all.get("obj_lodestone_key"));
        assertEquals(new Waypoint("trig_well", "sotw_city", 8, 64, 8, "The collapsed well",
            WaypointStyle.BOSSBAR, true), all.get("trig_well"));
        assertEquals(List.of(all.get("obj_lodestone_key")),
            WaypointResolver.activeSet(quest.metadata(), "QUEST_ACTIVE", all));
    }

    @Test
    @DisplayName("import -> database -> export -> import gives the same waypoints, and export is a fixed point")
    void roundTrip(@TempDir Path source, @TempDir Path exported) throws Exception {
        Files.createDirectories(source.resolve("quests"));
        Files.writeString(source.resolve("quests/tfah_wp.yml"), YAML);
        QuestDTO imported = load(source);

        RVNKQuests plugin = mock(RVNKQuests.class);
        when(plugin.getDataFolder()).thenReturn(exported.toFile());
        assertTrue(new QuestYamlRepository(plugin).save(throughDatabase(imported)).join());
        String text = Files.readString(exported.resolve("quests/tfah_wp.yml"));
        assertTrue(text.contains("waypoint:"), text);
        assertTrue(text.contains("waypoints: auto"), text);
        assertTrue(text.contains("style: compass"), text);

        QuestDTO reimported = load(exported);
        assertEquals(WaypointParser.parseAll(imported.metadata(), null),
            WaypointParser.parseAll(reimported.metadata(), null));

        assertTrue(new QuestYamlRepository(plugin).save(throughDatabase(reimported)).join());
        assertEquals(text, Files.readString(exported.resolve("quests/tfah_wp.yml")));
    }
}
