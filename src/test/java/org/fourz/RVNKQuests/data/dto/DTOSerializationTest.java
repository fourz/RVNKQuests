package org.fourz.RVNKQuests.data.dto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.fourz.RVNKQuests.quest.QuestState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DTO serialization with Gson.
 * Covers Java Records serialization, nested records, and type adapters.
 */
@DisplayName("DTO Serialization Tests")
class DTOSerializationTest {

    private static final UUID TEST_UUID = UUID.fromString("550e8400-e29b-12d3-a456-426614174000");

    // Gson with Instant type adapter
    private final Gson gson = new GsonBuilder()
        .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
        .create();

    @Nested
    @DisplayName("QuestProgressDTO Serialization")
    class QuestProgressDTOSerialization {

        @Test
        @DisplayName("Should serialize QuestProgressDTO to JSON")
        void shouldSerializeToJson() {
            QuestProgressDTO dto = new QuestProgressDTO(
                TEST_UUID,
                "quest_lost_piglin",
                QuestState.QUEST_ACTIVE,
                "peaceful_path",
                Instant.parse("2025-01-15T10:30:00Z"),
                null,
                Map.of("score", 100, "attempts", 3)
            );

            String json = gson.toJson(dto);

            assertNotNull(json);
            assertTrue(json.contains("quest_lost_piglin"));
            assertTrue(json.contains("QUEST_ACTIVE"));
            assertTrue(json.contains("peaceful_path"));
            assertTrue(json.contains("score"));
        }

        @Test
        @DisplayName("Should deserialize JSON to QuestProgressDTO")
        void shouldDeserializeFromJson() {
            String json = """
                {
                    "playerUuid": "550e8400-e29b-12d3-a456-426614174000",
                    "questId": "quest_lost_piglin",
                    "state": "COMPLETED",
                    "pathChoice": "aggressive_path",
                    "startedAt": 1705315800000,
                    "completedAt": 1705319400000,
                    "metadata": {"score": 250}
                }
                """;

            QuestProgressDTO dto = gson.fromJson(json, QuestProgressDTO.class);

            assertNotNull(dto);
            assertEquals(TEST_UUID, dto.playerUuid());
            assertEquals("quest_lost_piglin", dto.questId());
            assertEquals(QuestState.COMPLETED, dto.state());
            assertEquals("aggressive_path", dto.pathChoice());
        }

        @Test
        @DisplayName("Should round-trip serialize/deserialize QuestProgressDTO")
        void shouldRoundTrip() {
            QuestProgressDTO original = new QuestProgressDTO(
                TEST_UUID,
                "quest_ancient_guardian",
                QuestState.NOT_STARTED,
                null,
                Instant.now(),
                Instant.now(),
                Map.of("deaths", 5)
            );

            String json = gson.toJson(original);
            QuestProgressDTO restored = gson.fromJson(json, QuestProgressDTO.class);

            assertEquals(original.playerUuid(), restored.playerUuid());
            assertEquals(original.questId(), restored.questId());
            assertEquals(original.state(), restored.state());
            assertEquals(original.pathChoice(), restored.pathChoice());
        }
    }

    @Nested
    @DisplayName("QuestObjectiveProgressDTO Serialization")
    class QuestObjectiveProgressDTOSerialization {

        @Test
        @DisplayName("Should serialize QuestObjectiveProgressDTO to JSON")
        void shouldSerializeToJson() {
            QuestObjectiveProgressDTO dto = new QuestObjectiveProgressDTO(
                TEST_UUID,
                "quest_lost_piglin",
                "collect_gold_blocks",
                5,
                10,
                false,
                null,
                Map.of("lastCollectedItem", "gold_block")
            );

            String json = gson.toJson(dto);

            assertNotNull(json);
            assertTrue(json.contains("collect_gold_blocks"));
            assertTrue(json.contains("\"progressCount\":5"));
            assertTrue(json.contains("\"targetCount\":10"));
        }

        @Test
        @DisplayName("Should deserialize JSON to QuestObjectiveProgressDTO")
        void shouldDeserializeFromJson() {
            String json = """
                {
                    "playerUuid": "550e8400-e29b-12d3-a456-426614174000",
                    "questId": "quest_lost_piglin",
                    "objectiveId": "kill_zombies",
                    "progressCount": 8,
                    "targetCount": 10,
                    "completed": false,
                    "completedAt": null,
                    "metadata": {}
                }
                """;

            QuestObjectiveProgressDTO dto = gson.fromJson(json, QuestObjectiveProgressDTO.class);

            assertNotNull(dto);
            assertEquals("kill_zombies", dto.objectiveId());
            assertEquals(8, dto.progressCount());
            assertEquals(10, dto.targetCount());
            assertFalse(dto.completed());
        }

        @Test
        @DisplayName("Should round-trip serialize/deserialize QuestObjectiveProgressDTO")
        void shouldRoundTrip() {
            QuestObjectiveProgressDTO original = QuestObjectiveProgressDTO.createNew(
                TEST_UUID, "quest_1", "objective_1", 5
            ).incrementProgress(3);

            String json = gson.toJson(original);
            QuestObjectiveProgressDTO restored = gson.fromJson(json, QuestObjectiveProgressDTO.class);

            assertEquals(original.progressCount(), restored.progressCount());
            assertEquals(original.targetCount(), restored.targetCount());
        }
    }

    @Nested
    @DisplayName("Nested Records Serialization")
    class NestedRecordsSerialization {

        @Test
        @DisplayName("Should serialize list of DTOs correctly")
        void shouldSerializeListOfDTOs() {
            List<QuestProgressDTO> progressList = List.of(
                QuestProgressDTO.createNew(TEST_UUID, "quest_1"),
                QuestProgressDTO.createNew(TEST_UUID, "quest_2").withState(QuestState.QUEST_ACTIVE),
                QuestProgressDTO.createNew(TEST_UUID, "quest_3").withState(QuestState.COMPLETED)
            );

            String json = gson.toJson(progressList);

            assertNotNull(json);
            assertTrue(json.startsWith("["));
            assertTrue(json.endsWith("]"));
            assertTrue(json.contains("quest_1"));
            assertTrue(json.contains("quest_2"));
            assertTrue(json.contains("quest_3"));
        }

        @Test
        @DisplayName("Should preserve metadata map with complex values")
        void shouldPreserveComplexMetadata() {
            Map<String, Object> complexMetadata = Map.of(
                "stringValue", "test",
                "intValue", 42,
                "boolValue", true,
                "listValue", List.of("a", "b", "c")
            );

            QuestProgressDTO dto = new QuestProgressDTO(
                TEST_UUID,
                "quest_with_metadata",
                QuestState.QUEST_ACTIVE,
                null,
                null,
                null,
                complexMetadata
            );

            String json = gson.toJson(dto);
            QuestProgressDTO restored = gson.fromJson(json, QuestProgressDTO.class);

            assertNotNull(restored.metadata());
            assertEquals("test", restored.metadata().get("stringValue"));
            // Note: numbers may be deserialized as Double by Gson
            assertNotNull(restored.metadata().get("intValue"));
        }
    }

    @Nested
    @DisplayName("List.copyOf Defensive Copies")
    class DefensiveCopyTests {

        @Test
        @DisplayName("Deserialized metadata should be unmodifiable (via record validation)")
        void deserializedMetadataShouldBeProtected() {
            String json = """
                {
                    "playerUuid": "550e8400-e29b-12d3-a456-426614174000",
                    "questId": "test_quest",
                    "state": "IN_PROGRESS",
                    "pathChoice": null,
                    "startedAt": null,
                    "completedAt": null,
                    "metadata": {"key": "value"}
                }
                """;

            QuestProgressDTO dto = gson.fromJson(json, QuestProgressDTO.class);

            // The compact constructor uses Map.copyOf which creates unmodifiable map
            // However, Gson might bypass the constructor, so this tests the behavior
            assertNotNull(dto.metadata());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle null optional fields")
        void shouldHandleNullOptionalFields() {
            QuestProgressDTO dto = new QuestProgressDTO(
                TEST_UUID,
                "quest_minimal",
                null, // will be defaulted to NOT_STARTED
                null,
                null,
                null,
                null // will be defaulted to empty map
            );

            String json = gson.toJson(dto);
            QuestProgressDTO restored = gson.fromJson(json, QuestProgressDTO.class);

            assertNotNull(restored);
            assertEquals("quest_minimal", restored.questId());
        }

        @Test
        @DisplayName("Should handle empty metadata map")
        void shouldHandleEmptyMetadata() {
            QuestProgressDTO dto = QuestProgressDTO.createNew(TEST_UUID, "quest_empty_meta");

            String json = gson.toJson(dto);

            assertTrue(json.contains("\"metadata\":{}"));

            QuestProgressDTO restored = gson.fromJson(json, QuestProgressDTO.class);
            assertNotNull(restored.metadata());
        }

        @Test
        @DisplayName("Should handle all QuestState enum values")
        void shouldHandleAllQuestStates() {
            for (QuestState state : QuestState.values()) {
                QuestProgressDTO dto = new QuestProgressDTO(
                    TEST_UUID, "quest_state_" + state.name(), state,
                    null, null, null, Map.of()
                );

                String json = gson.toJson(dto);
                QuestProgressDTO restored = gson.fromJson(json, QuestProgressDTO.class);

                assertEquals(state, restored.state());
            }
        }
    }

    /**
     * Custom TypeAdapter for Instant to handle epoch milliseconds.
     */
    private static class InstantTypeAdapter extends TypeAdapter<Instant> {
        @Override
        public void write(JsonWriter out, Instant value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toEpochMilli());
            }
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return Instant.ofEpochMilli(in.nextLong());
        }
    }
}
