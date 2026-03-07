package org.fourz.RVNKQuests.data.dto;

import org.fourz.RVNKQuests.quest.QuestState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QuestProgressDTO Java Record.
 * Tests validation, immutability, factory methods, and equality.
 */
@DisplayName("QuestProgressDTO Tests")
class QuestProgressDTOTest {

    private static final UUID TEST_PLAYER_UUID = UUID.fromString("550e8400-e29b-12d3-a456-426614174000");
    private static final String TEST_QUEST_ID = "quest_lost_piglin";

    @Nested
    @DisplayName("Compact Constructor Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should reject null playerUuid")
        void shouldRejectNullPlayerUuid() {
            NullPointerException ex = assertThrows(NullPointerException.class, () ->
                new QuestProgressDTO(null, TEST_QUEST_ID, QuestState.QUEST_ACTIVE, 
                    null, null, null, null)
            );
            assertTrue(ex.getMessage().contains("playerUuid"));
        }

        @Test
        @DisplayName("Should reject null questId")
        void shouldRejectNullQuestId() {
            NullPointerException ex = assertThrows(NullPointerException.class, () ->
                new QuestProgressDTO(TEST_PLAYER_UUID, null, QuestState.QUEST_ACTIVE, 
                    null, null, null, null)
            );
            assertTrue(ex.getMessage().contains("questId"));
        }

        @Test
        @DisplayName("Should default null state to NOT_STARTED")
        void shouldDefaultNullState() {
            QuestProgressDTO dto = new QuestProgressDTO(TEST_PLAYER_UUID, TEST_QUEST_ID,
                null, null, null, null, null);
            assertEquals(QuestState.NOT_STARTED, dto.state());
        }

        @Test
        @DisplayName("Should default null metadata to empty map")
        void shouldDefaultNullMetadata() {
            QuestProgressDTO dto = new QuestProgressDTO(TEST_PLAYER_UUID, TEST_QUEST_ID,
                QuestState.QUEST_ACTIVE, null, null, null, null);
            assertNotNull(dto.metadata());
            assertTrue(dto.metadata().isEmpty());
        }

        @Test
        @DisplayName("Should create defensive copy of metadata")
        void shouldCreateDefensiveCopyOfMetadata() {
            Map<String, Object> mutableMap = new HashMap<>();
            mutableMap.put("key", "value");
            
            QuestProgressDTO dto = new QuestProgressDTO(TEST_PLAYER_UUID, TEST_QUEST_ID,
                QuestState.QUEST_ACTIVE, null, null, null, mutableMap);
            
            // Modify original map
            mutableMap.put("newKey", "newValue");
            
            // DTO should not be affected
            assertFalse(dto.metadata().containsKey("newKey"));
        }

        @Test
        @DisplayName("Metadata should be unmodifiable")
        void metadataShouldBeUnmodifiable() {
            QuestProgressDTO dto = new QuestProgressDTO(TEST_PLAYER_UUID, TEST_QUEST_ID,
                QuestState.QUEST_ACTIVE, null, null, null, Map.of("key", "value"));
            
            assertThrows(UnsupportedOperationException.class, () ->
                dto.metadata().put("newKey", "newValue")
            );
        }
    }

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("createNew() should create NOT_STARTED progress")
        void createNewShouldCreateNotStartedProgress() {
            QuestProgressDTO dto = QuestProgressDTO.createNew(TEST_PLAYER_UUID, TEST_QUEST_ID);
            
            assertEquals(TEST_PLAYER_UUID, dto.playerUuid());
            assertEquals(TEST_QUEST_ID, dto.questId());
            assertEquals(QuestState.NOT_STARTED, dto.state());
            assertNull(dto.pathChoice());
            assertNull(dto.startedAt());
            assertNull(dto.completedAt());
            assertTrue(dto.metadata().isEmpty());
        }
    }

    @Nested
    @DisplayName("Immutable Transformation Tests")
    class TransformationTests {

        @Test
        @DisplayName("withState() should set startedAt when transitioning from NOT_STARTED")
        void withStateShouldSetStartedAt() {
            QuestProgressDTO original = QuestProgressDTO.createNew(TEST_PLAYER_UUID, TEST_QUEST_ID);
            
            QuestProgressDTO updated = original.withState(QuestState.QUEST_ACTIVE);
            
            assertEquals(QuestState.QUEST_ACTIVE, updated.state());
            assertNotNull(updated.startedAt());
            assertNull(updated.completedAt());
            // Original unchanged
            assertEquals(QuestState.NOT_STARTED, original.state());
        }

        @Test
        @DisplayName("withState() should set completedAt when transitioning to COMPLETED")
        void withStateShouldSetCompletedAt() {
            Instant before = Instant.now();
            QuestProgressDTO started = QuestProgressDTO.createNew(TEST_PLAYER_UUID, TEST_QUEST_ID)
                .withState(QuestState.QUEST_ACTIVE);
            
            QuestProgressDTO completed = started.withState(QuestState.COMPLETED);
            
            assertEquals(QuestState.COMPLETED, completed.state());
            assertNotNull(completed.completedAt());
            assertTrue(completed.completedAt().isAfter(before) || completed.completedAt().equals(before));
        }

        @Test
        @DisplayName("withState() should not overwrite existing startedAt")
        void withStateShouldNotOverwriteStartedAt() {
            Instant fixedStart = Instant.parse("2025-01-01T00:00:00Z");
            QuestProgressDTO dto = new QuestProgressDTO(TEST_PLAYER_UUID, TEST_QUEST_ID,
                QuestState.QUEST_ACTIVE, null, fixedStart, null, Map.of());
            
            QuestProgressDTO updated = dto.withState(QuestState.COMPLETED);
            
            assertEquals(fixedStart, updated.startedAt());
        }

        @Test
        @DisplayName("withPathChoice() should update path choice preserving other fields")
        void withPathChoiceShouldPreserveOtherFields() {
            Instant fixedStart = Instant.parse("2025-01-01T00:00:00Z");
            QuestProgressDTO original = new QuestProgressDTO(TEST_PLAYER_UUID, TEST_QUEST_ID,
                QuestState.QUEST_ACTIVE, null, fixedStart, null, Map.of("key", "value"));
            
            QuestProgressDTO updated = original.withPathChoice("peaceful_path");
            
            assertEquals("peaceful_path", updated.pathChoice());
            assertEquals(TEST_PLAYER_UUID, updated.playerUuid());
            assertEquals(TEST_QUEST_ID, updated.questId());
            assertEquals(QuestState.QUEST_ACTIVE, updated.state());
            assertEquals(fixedStart, updated.startedAt());
            assertEquals(Map.of("key", "value"), updated.metadata());
        }
    }

    @Nested
    @DisplayName("Equals and HashCode Tests")
    class EqualsHashCodeTests {

        @Test
        @DisplayName("Equal DTOs should have same hashCode")
        void equalDTOsShouldHaveSameHashCode() {
            QuestProgressDTO dto1 = QuestProgressDTO.createNew(TEST_PLAYER_UUID, TEST_QUEST_ID);
            QuestProgressDTO dto2 = QuestProgressDTO.createNew(TEST_PLAYER_UUID, TEST_QUEST_ID);
            
            assertEquals(dto1, dto2);
            assertEquals(dto1.hashCode(), dto2.hashCode());
        }

        @Test
        @DisplayName("Different DTOs should not be equal")
        void differentDTOsShouldNotBeEqual() {
            QuestProgressDTO dto1 = QuestProgressDTO.createNew(TEST_PLAYER_UUID, TEST_QUEST_ID);
            QuestProgressDTO dto2 = QuestProgressDTO.createNew(TEST_PLAYER_UUID, "different_quest");
            
            assertNotEquals(dto1, dto2);
        }
    }
}
