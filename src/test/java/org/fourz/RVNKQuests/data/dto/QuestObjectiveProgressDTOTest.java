package org.fourz.RVNKQuests.data.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QuestObjectiveProgressDTO Java Record.
 * Tests validation, immutability, factory methods, and equality.
 */
@DisplayName("QuestObjectiveProgressDTO Tests")
class QuestObjectiveProgressDTOTest {

    private static final UUID TEST_PLAYER_UUID = UUID.fromString("550e8400-e29b-12d3-a456-426614174000");
    private static final String TEST_QUEST_ID = "quest_lost_piglin";
    private static final String TEST_OBJECTIVE_ID = "collect_gold_blocks";

    @Nested
    @DisplayName("Compact Constructor Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should reject null playerUuid")
        void shouldRejectNullPlayerUuid() {
            NullPointerException ex = assertThrows(NullPointerException.class, () ->
                new QuestObjectiveProgressDTO(null, TEST_QUEST_ID, TEST_OBJECTIVE_ID,
                    0, 10, false, null, null)
            );
            assertTrue(ex.getMessage().contains("playerUuid"));
        }

        @Test
        @DisplayName("Should reject null questId")
        void shouldRejectNullQuestId() {
            NullPointerException ex = assertThrows(NullPointerException.class, () ->
                new QuestObjectiveProgressDTO(TEST_PLAYER_UUID, null, TEST_OBJECTIVE_ID,
                    0, 10, false, null, null)
            );
            assertTrue(ex.getMessage().contains("questId"));
        }

        @Test
        @DisplayName("Should reject null objectiveId")
        void shouldRejectNullObjectiveId() {
            NullPointerException ex = assertThrows(NullPointerException.class, () ->
                new QuestObjectiveProgressDTO(TEST_PLAYER_UUID, TEST_QUEST_ID, null,
                    0, 10, false, null, null)
            );
            assertTrue(ex.getMessage().contains("objectiveId"));
        }

        @Test
        @DisplayName("Should clamp negative progressCount to zero")
        void shouldClampNegativeProgressCount() {
            QuestObjectiveProgressDTO dto = new QuestObjectiveProgressDTO(
                TEST_PLAYER_UUID, TEST_QUEST_ID, TEST_OBJECTIVE_ID,
                -5, 10, false, null, null
            );
            assertEquals(0, dto.progressCount());
        }

        @Test
        @DisplayName("Should clamp zero targetCount to one")
        void shouldClampZeroTargetCount() {
            QuestObjectiveProgressDTO dto = new QuestObjectiveProgressDTO(
                TEST_PLAYER_UUID, TEST_QUEST_ID, TEST_OBJECTIVE_ID,
                0, 0, false, null, null
            );
            assertEquals(1, dto.targetCount());
        }

        @Test
        @DisplayName("Should clamp negative targetCount to one")
        void shouldClampNegativeTargetCount() {
            QuestObjectiveProgressDTO dto = new QuestObjectiveProgressDTO(
                TEST_PLAYER_UUID, TEST_QUEST_ID, TEST_OBJECTIVE_ID,
                0, -10, false, null, null
            );
            assertEquals(1, dto.targetCount());
        }

        @Test
        @DisplayName("Should default null metadata to empty map")
        void shouldDefaultNullMetadata() {
            QuestObjectiveProgressDTO dto = new QuestObjectiveProgressDTO(
                TEST_PLAYER_UUID, TEST_QUEST_ID, TEST_OBJECTIVE_ID,
                0, 10, false, null, null
            );
            assertNotNull(dto.metadata());
            assertTrue(dto.metadata().isEmpty());
        }

        @Test
        @DisplayName("Should create defensive copy of metadata")
        void shouldCreateDefensiveCopyOfMetadata() {
            Map<String, Object> mutableMap = new HashMap<>();
            mutableMap.put("key", "value");
            
            QuestObjectiveProgressDTO dto = new QuestObjectiveProgressDTO(
                TEST_PLAYER_UUID, TEST_QUEST_ID, TEST_OBJECTIVE_ID,
                5, 10, false, null, mutableMap
            );
            
            // Modify original
            mutableMap.put("newKey", "newValue");
            
            // DTO should not be affected
            assertFalse(dto.metadata().containsKey("newKey"));
        }

        @Test
        @DisplayName("Metadata should be unmodifiable")
        void metadataShouldBeUnmodifiable() {
            QuestObjectiveProgressDTO dto = new QuestObjectiveProgressDTO(
                TEST_PLAYER_UUID, TEST_QUEST_ID, TEST_OBJECTIVE_ID,
                5, 10, false, null, Map.of("key", "value")
            );
            
            assertThrows(UnsupportedOperationException.class, () ->
                dto.metadata().put("newKey", "newValue")
            );
        }
    }

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("createNew() should create zero-progress objective")
        void createNewShouldCreateZeroProgress() {
            QuestObjectiveProgressDTO dto = QuestObjectiveProgressDTO.createNew(
                TEST_PLAYER_UUID, TEST_QUEST_ID, TEST_OBJECTIVE_ID, 10
            );
            
            assertEquals(TEST_PLAYER_UUID, dto.playerUuid());
            assertEquals(TEST_QUEST_ID, dto.questId());
            assertEquals(TEST_OBJECTIVE_ID, dto.objectiveId());
            assertEquals(0, dto.progressCount());
            assertEquals(10, dto.targetCount());
            assertFalse(dto.completed());
            assertNull(dto.completedAt());
            assertTrue(dto.metadata().isEmpty());
        }
    }

    @Nested
    @DisplayName("Immutable Transformation Tests")
    class TransformationTests {

        @Test
        @DisplayName("incrementProgress() should increase progressCount")
        void incrementProgressShouldIncreaseCount() {
            QuestObjectiveProgressDTO original = QuestObjectiveProgressDTO.createNew(
                TEST_PLAYER_UUID, TEST_QUEST_ID, TEST_OBJECTIVE_ID, 10
            );
            
            QuestObjectiveProgressDTO updated = original.incrementProgress(3);
            
            assertEquals(3, updated.progressCount());
            assertFalse(updated.completed());
            // Original unchanged
            assertEquals(0, original.progressCount());
        }

        @Test
        @DisplayName("incrementProgress() should cap at targetCount")
        void incrementProgressShouldCapAtTarget() {
            QuestObjectiveProgressDTO original = QuestObjectiveProgressDTO.createNew(
                TEST_PLAYER_UUID, TEST_QUEST_ID, TEST_OBJECTIVE_ID, 10
            );
            
            QuestObjectiveProgressDTO updated = original.incrementProgress(15);
            
            assertEquals(10, updated.progressCount());
        }

        @Test
        @DisplayName("incrementProgress() should mark completed when reaching target")
        void incrementProgressShouldMarkCompleted() {
            QuestObjectiveProgressDTO original = QuestObjectiveProgressDTO.createNew(
                TEST_PLAYER_UUID, TEST_QUEST_ID, TEST_OBJECTIVE_ID, 10
            );
            
            QuestObjectiveProgressDTO updated = original.incrementProgress(10);
            
            assertTrue(updated.completed());
            assertNotNull(updated.completedAt());
        }

        @Test
        @DisplayName("incrementProgress() should not overwrite completedAt")
        void incrementProgressShouldNotOverwriteCompletedAt() {
            Instant fixedCompletion = Instant.parse("2025-01-01T00:00:00Z");
            QuestObjectiveProgressDTO original = new QuestObjectiveProgressDTO(
                TEST_PLAYER_UUID, TEST_QUEST_ID, TEST_OBJECTIVE_ID,
                10, 10, true, fixedCompletion, Map.of()
            );
            
            QuestObjectiveProgressDTO updated = original.incrementProgress(5);
            
            assertEquals(fixedCompletion, updated.completedAt());
        }
    }

    @Nested
    @DisplayName("Calculation Tests")
    class CalculationTests {

        @Test
        @DisplayName("Completion percentage should be calculated correctly")
        void completionPercentageShouldBeCorrect() {
            QuestObjectiveProgressDTO dto = new QuestObjectiveProgressDTO(
                TEST_PLAYER_UUID, TEST_QUEST_ID, TEST_OBJECTIVE_ID,
                5, 10, false, null, Map.of()
            );
            
            // 5/10 = 50%
            double percentage = (dto.progressCount() * 100.0) / dto.targetCount();
            assertEquals(50.0, percentage, 0.001);
        }

        @Test
        @DisplayName("Zero progress should be 0%")
        void zeroProgressShouldBeZeroPercent() {
            QuestObjectiveProgressDTO dto = QuestObjectiveProgressDTO.createNew(
                TEST_PLAYER_UUID, TEST_QUEST_ID, TEST_OBJECTIVE_ID, 10
            );
            
            double percentage = (dto.progressCount() * 100.0) / dto.targetCount();
            assertEquals(0.0, percentage, 0.001);
        }

        @Test
        @DisplayName("Full progress should be 100%")
        void fullProgressShouldBe100Percent() {
            QuestObjectiveProgressDTO dto = new QuestObjectiveProgressDTO(
                TEST_PLAYER_UUID, TEST_QUEST_ID, TEST_OBJECTIVE_ID,
                10, 10, true, Instant.now(), Map.of()
            );
            
            double percentage = (dto.progressCount() * 100.0) / dto.targetCount();
            assertEquals(100.0, percentage, 0.001);
        }
    }

    @Nested
    @DisplayName("Equals and HashCode Tests")
    class EqualsHashCodeTests {

        @Test
        @DisplayName("Equal DTOs should have same hashCode")
        void equalDTOsShouldHaveSameHashCode() {
            QuestObjectiveProgressDTO dto1 = QuestObjectiveProgressDTO.createNew(
                TEST_PLAYER_UUID, TEST_QUEST_ID, TEST_OBJECTIVE_ID, 10
            );
            QuestObjectiveProgressDTO dto2 = QuestObjectiveProgressDTO.createNew(
                TEST_PLAYER_UUID, TEST_QUEST_ID, TEST_OBJECTIVE_ID, 10
            );
            
            assertEquals(dto1, dto2);
            assertEquals(dto1.hashCode(), dto2.hashCode());
        }

        @Test
        @DisplayName("Different DTOs should not be equal")
        void differentDTOsShouldNotBeEqual() {
            QuestObjectiveProgressDTO dto1 = QuestObjectiveProgressDTO.createNew(
                TEST_PLAYER_UUID, TEST_QUEST_ID, TEST_OBJECTIVE_ID, 10
            );
            QuestObjectiveProgressDTO dto2 = QuestObjectiveProgressDTO.createNew(
                TEST_PLAYER_UUID, TEST_QUEST_ID, "different_objective", 10
            );
            
            assertNotEquals(dto1, dto2);
        }
    }
}
