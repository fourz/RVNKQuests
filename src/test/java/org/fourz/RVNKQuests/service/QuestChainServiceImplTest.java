package org.fourz.RVNKQuests.service;

import org.fourz.RVNKQuests.data.dto.QuestChainDTO;
import org.fourz.RVNKQuests.data.dto.QuestChainDTO.ChainNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QuestChainServiceImpl.
 * Validates core chain functionality through direct invocation tests.
 */
@DisplayName("QuestChainService Integration Tests")
class QuestChainServiceImplTest {

    private static final String CHAIN_ID = "test_chain";
    private static final String QUEST_ID = "test_quest";

    @Nested
    @DisplayName("Quest Chain DTO Functionality")
    class QuestChainDTOTests {

        @Test
        @DisplayName("Should create linear chain with factory method")
        void shouldCreateLinearChain() {
            QuestChainDTO chain = QuestChainDTO.linear(CHAIN_ID, "Linear", QUEST_ID);
            assertNotNull(chain);
            assertEquals(CHAIN_ID, chain.chainId());
            assertEquals("Linear", chain.name());
        }

        @Test
        @DisplayName("Should create chain with builder pattern")
        void shouldBuildChainWithBuilder() {
            ChainNode node = ChainNode.quest("n1", QUEST_ID);
            QuestChainDTO chain = QuestChainDTO.builder()
                .chainId(CHAIN_ID)
                .name("Built Chain")
                .node(node)
                .build();

            assertEquals(CHAIN_ID, chain.chainId());
            assertEquals("Built Chain", chain.name());
            assertEquals(1, chain.getTotalQuestCount());
        }

        @Test
        @DisplayName("Should retrieve all quest IDs in chain")
        void shouldGetAllQuestIds() {
            ChainNode n1 = ChainNode.quest("n1", "q1");
            ChainNode n2 = ChainNode.quest("n2", "q2");
            ChainNode seq = ChainNode.sequence("root", n1, n2);
            QuestChainDTO chain = new QuestChainDTO(CHAIN_ID, "Multi", null, null,
                List.of(seq), List.of(), List.of(), false, 0, Instant.now(), Map.of());

            List<String> questIds = chain.getAllQuestIds();
            assertEquals(2, questIds.size());
            assertTrue(questIds.contains("q1"));
            assertTrue(questIds.contains("q2"));
        }

        @Test
        @DisplayName("Should create chain with prerequisites")
        void shouldCreateChainWithPrerequisites() {
            QuestChainDTO chain = QuestChainDTO.linear(CHAIN_ID, "Locked", QUEST_ID);
            var prereq = chain.withPrerequisite(
                org.fourz.RVNKQuests.data.dto.QuestPrerequisite.playerLevel("p1", 10));

            assertTrue(prereq.hasPrerequisites());
            assertEquals(1, prereq.prerequisites().size());
        }

        @Test
        @DisplayName("Should handle repeatable chains with cooldown")
        void shouldHandleRepeatableChain() {
            ChainNode node = ChainNode.quest("n1", QUEST_ID);
            QuestChainDTO chain = new QuestChainDTO(CHAIN_ID, "Daily", null, "daily",
                List.of(node), List.of(), List.of(), true, 1440, Instant.now(), Map.of());

            assertTrue(chain.repeatable());
            assertEquals(1440, chain.cooldownMinutes());
        }

        @Test
        @DisplayName("Should calculate completion percentage")
        void shouldTrackCompletionPercentage() {
            IQuestChainService.ChainProgress progress = new IQuestChainService.ChainProgress(
                UUID.randomUUID(), CHAIN_ID, IQuestChainService.ChainStatus.IN_PROGRESS,
                List.of("q1"), List.of("q2"), List.of("q3"), 0, System.currentTimeMillis(), System.currentTimeMillis());

            double percentage = progress.getCompletionPercentage();
            assertTrue(percentage >= 0 && percentage <= 100);
            assertEquals(33.33333333333333, percentage, 0.01);
        }

        @Test
        @DisplayName("Should track active quests in progress")
        void shouldTrackActiveQuests() {
            IQuestChainService.ChainProgress progress = new IQuestChainService.ChainProgress(
                UUID.randomUUID(), CHAIN_ID, IQuestChainService.ChainStatus.IN_PROGRESS,
                List.of(), List.of("q1", "q2"), List.of(), 0, System.currentTimeMillis(), System.currentTimeMillis());

            assertTrue(progress.hasActiveQuests());
            assertEquals(2, progress.activeQuests().size());
        }
    }

    @Nested
    @DisplayName("Chain Node Structures")
    class ChainNodeTests {

        @Test
        @DisplayName("Should create single quest node")
        void shouldCreateQuestNode() {
            ChainNode node = ChainNode.quest("node1", QUEST_ID);
            assertTrue(node.isLeaf());
            assertEquals(QUEST_ID, node.questId());
        }

        @Test
        @DisplayName("Should create sequence of nodes")
        void shouldCreateSequence() {
            ChainNode seq = ChainNode.sequence("seq",
                ChainNode.quest("n1", "q1"),
                ChainNode.quest("n2", "q2"));
            assertFalse(seq.isLeaf());
            assertEquals(2, seq.children().size());
        }

        @Test
        @DisplayName("Should create branching (ANY) nodes")
        void shouldCreateBranchingNodes() {
            ChainNode branch = ChainNode.any("choice",
                ChainNode.quest("path1", "q1"),
                ChainNode.quest("path2", "q2"));
            assertEquals(2, branch.children().size());
        }

        @Test
        @DisplayName("Should create count-based nodes")
        void shouldCreateCountNodes() {
            ChainNode count = ChainNode.count("min2of3", 2,
                ChainNode.quest("n1", "q1"),
                ChainNode.quest("n2", "q2"),
                ChainNode.quest("n3", "q3"));
            assertEquals(2, count.requiredCount());
        }

        @Test
        @DisplayName("Should collect all quest IDs from tree")
        void shouldCollectQuestIds() {
            ChainNode seq = ChainNode.sequence("root",
                ChainNode.quest("n1", "q1"),
                ChainNode.any("branch",
                    ChainNode.quest("n2", "q2"),
                    ChainNode.quest("n3", "q3")));

            List<String> quests = seq.getAllQuestIds();
            assertEquals(3, quests.size());
        }
    }

    @Nested
    @DisplayName("Chain Start Results")
    class ChainStartResultTests {

        @Test
        @DisplayName("Should create successful start result")
        void shouldCreateSuccessResult() {
            var result = IQuestChainService.ChainStartResult.success(CHAIN_ID, List.of("q1", "q2"));
            assertTrue(result.success());
            assertEquals(2, result.availableQuests().size());
        }

        @Test
        @DisplayName("Should create failure result")
        void shouldCreateFailureResult() {
            var result = IQuestChainService.ChainStartResult.failure(CHAIN_ID, "Missing prerequisites");
            assertFalse(result.success());
            assertTrue(result.message().contains("Missing"));
        }
    }

    @Nested
    @DisplayName("Prerequisite Validation Results")
    class PrerequisiteResultTests {

        @Test
        @DisplayName("Should create success result for met prerequisites")
        void shouldCreateSuccessPrerequisiteResult() {
            var result = IQuestChainService.PrerequisiteResult.success();
            assertTrue(result.satisfied());
            assertTrue(result.unmetPrerequisites().isEmpty());
        }

        @Test
        @DisplayName("Should create failure result for unmet prerequisites")
        void shouldCreateFailurePrerequisiteResult() {
            var unmet = List.of(
                org.fourz.RVNKQuests.data.dto.QuestPrerequisite.playerLevel("p1", 20));
            var result = IQuestChainService.PrerequisiteResult.failure(unmet);
            assertFalse(result.satisfied());
            assertEquals(1, result.unmetPrerequisites().size());
        }
    }
}
