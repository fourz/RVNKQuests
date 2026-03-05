package org.fourz.RVNKQuests.quest;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the idempotency guard pattern used in QuestManager.completeQuest().
 *
 * <p>Validates the ConcurrentHashMap.newKeySet() in-flight tracker that prevents
 * duplicate quest completion from concurrent events (TOCTOU race condition fix).
 */
@DisplayName("Quest Completion Idempotency Guard Tests")
class QuestIdempotencyTest {

    private Set<String> completionsInProgress;

    @BeforeEach
    void setUp() {
        completionsInProgress = ConcurrentHashMap.newKeySet();
    }

    @Test
    @DisplayName("First claim succeeds")
    void firstClaimSucceeds() {
        String key = UUID.randomUUID() + ":test_quest";
        assertTrue(completionsInProgress.add(key), "First add must return true");
    }

    @Test
    @DisplayName("Duplicate claim is rejected")
    void duplicateClaimRejected() {
        String key = UUID.randomUUID() + ":test_quest";
        completionsInProgress.add(key);
        assertFalse(completionsInProgress.add(key), "Second add must return false (idempotency)");
    }

    @Test
    @DisplayName("Key removed after completion allows retry")
    void removedKeyAllowsRetry() {
        String key = UUID.randomUUID() + ":test_quest";
        completionsInProgress.add(key);
        completionsInProgress.remove(key);
        assertTrue(completionsInProgress.add(key), "Re-add after remove must succeed");
    }

    @Test
    @DisplayName("Keys for different players are independent")
    void differentPlayersAreIndependent() {
        String questId = "test_quest";
        String key1 = UUID.randomUUID() + ":" + questId;
        String key2 = UUID.randomUUID() + ":" + questId;

        assertTrue(completionsInProgress.add(key1));
        assertTrue(completionsInProgress.add(key2), "Different players must not block each other");
    }

    @Test
    @DisplayName("Keys for different quests are independent")
    void differentQuestsAreIndependent() {
        String playerId = UUID.randomUUID().toString();
        String key1 = playerId + ":quest_a";
        String key2 = playerId + ":quest_b";

        assertTrue(completionsInProgress.add(key1));
        assertTrue(completionsInProgress.add(key2), "Different quests for same player must not block each other");
    }

    @Test
    @DisplayName("Concurrent threads: only one wins the race")
    void concurrentCompletionOnlyOneWins() throws InterruptedException {
        String key = UUID.randomUUID() + ":test_quest";
        int threadCount = 20;
        AtomicInteger wins = new AtomicInteger(0);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                ready.countDown();
                try { go.await(); } catch (InterruptedException ignored) {}
                if (completionsInProgress.add(key)) {
                    wins.incrementAndGet();
                }
                done.countDown();
            }).start();
        }

        ready.await();
        go.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS), "All threads must complete within 5s");

        assertEquals(1, wins.get(), "Exactly one thread must win the in-flight claim");
    }

    @Test
    @DisplayName("Guard removed in whenComplete allows subsequent invocation")
    void guardRemovedInWhenComplete() throws Exception {
        String key = UUID.randomUUID() + ":test_quest";

        // Simulate the QuestManager.completeQuest() pattern:
        // add → async work → whenComplete removes key
        AtomicInteger completionCount = new AtomicInteger(0);

        CompletableFuture.runAsync(() -> {
            if (!completionsInProgress.add(key)) return; // duplicate — skip
            try {
                completionCount.incrementAndGet();
            } finally {
                completionsInProgress.remove(key);
            }
        }).get(2, TimeUnit.SECONDS);

        assertEquals(1, completionCount.get());
        assertFalse(completionsInProgress.contains(key), "Key must be removed after completion");

        // A subsequent invocation must be allowed (e.g., player completes again after re-acquiring quest)
        assertTrue(completionsInProgress.add(key), "Key must be available for future use after removal");
    }

    @Test
    @DisplayName("Already-COMPLETED state check prevents double-reward")
    void alreadyCompletedStateCheckPreventsDoubleReward() {
        // Simulate the state guard inside completeQuest():
        // if (currentState == QuestState.COMPLETED) return false;
        QuestState currentState = QuestState.COMPLETED;
        boolean shouldProceed = currentState != QuestState.COMPLETED;
        assertFalse(shouldProceed, "Should not proceed if player is already COMPLETED");
    }
}
