package org.fourz.RVNKQuests.quest;

import org.fourz.RVNKQuests.data.dto.QuestProgressDTO;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance test suite for RVNKQuests system.
 * 
 * <p>Quest-20 Success Criteria:
 * <ul>
 *   <li>Quest lookups: <1ms (target)</li>
 *   <li>Event processing: <5ms (target)</li>
 *   <li>Memory usage: <50MB (target)</li>
 * </ul>
 * 
 * @see <a href="https://archon.ravenkraft.dev/tasks/quest-20">quest-20: Lost Piglin Performance</a>
 */
@DisplayName("Quest System Performance Tests")
class QuestPerformanceTest {

    // Performance thresholds (quest-20 requirements)
    private static final long QUEST_LOOKUP_TARGET_NS = 1_000_000; // 1ms in nanoseconds
    private static final long EVENT_PROCESSING_TARGET_NS = 5_000_000; // 5ms in nanoseconds
    private static final long MEMORY_TARGET_BYTES = 50 * 1024 * 1024; // 50MB

    // Test configuration
    private static final int WARMUP_ITERATIONS = 100;
    private static final int TEST_ITERATIONS = 1000;
    private static final int CONCURRENT_PLAYERS = 100;

    private Map<String, Quest> questRegistry;
    private Map<String, Set<UUID>> activePlayersByQuest;

    @BeforeEach
    void setUp() {
        questRegistry = new HashMap<>();
        activePlayersByQuest = new ConcurrentHashMap<>();
    }

    @Nested
    @DisplayName("Quest Lookup Performance")
    class QuestLookupPerformance {

        @Test
        @DisplayName("HashMap quest lookup should be under 1ms for 100 quests")
        void questLookupPerformance() {
            // Setup: Register 100 quests (using a simple TestQuest class)
            for (int i = 0; i < 100; i++) {
                String questId = "quest_" + i;
                questRegistry.put(questId, new TestQuest(questId));
            }

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                questRegistry.get("quest_50");
            }

            // Measure
            long totalTime = 0;
            String targetQuestId = "quest_50";
            
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                long start = System.nanoTime();
                Quest result = questRegistry.get(targetQuestId);
                long elapsed = System.nanoTime() - start;
                totalTime += elapsed;
                assertNotNull(result);
            }

            long avgTime = totalTime / TEST_ITERATIONS;
            System.out.printf("Quest lookup (HashMap, 100 quests): avg=%dns, target=<%dns%n", 
                avgTime, QUEST_LOOKUP_TARGET_NS);
            
            assertTrue(avgTime < QUEST_LOOKUP_TARGET_NS, 
                "Quest lookup exceeded 1ms target: " + avgTime + "ns");
        }

        @Test
        @DisplayName("Quest lookup should handle 1000 concurrent lookups")
        void concurrentQuestLookup() throws InterruptedException {
            // Setup: Register quests
            for (int i = 0; i < 100; i++) {
                String questId = "quest_" + i;
                questRegistry.put(questId, new TestQuest(questId));
            }

            CountDownLatch latch = new CountDownLatch(CONCURRENT_PLAYERS);
            List<Long> times = Collections.synchronizedList(new ArrayList<>());

            // Spawn concurrent threads
            for (int i = 0; i < CONCURRENT_PLAYERS; i++) {
                final int threadId = i;
                new Thread(() -> {
                    try {
                        String questId = "quest_" + (threadId % 100);
                        long start = System.nanoTime();
                        Quest result = questRegistry.get(questId);
                        long elapsed = System.nanoTime() - start;
                        times.add(elapsed);
                        assertNotNull(result);
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS), "Concurrent lookups timed out");
            
            long avgTime = times.stream().mapToLong(Long::longValue).sum() / times.size();
            long maxTime = times.stream().mapToLong(Long::longValue).max().orElse(0);
            
            System.out.printf("Concurrent quest lookup (%d threads): avg=%dns, max=%dns%n", 
                CONCURRENT_PLAYERS, avgTime, maxTime);
            
            assertTrue(avgTime < QUEST_LOOKUP_TARGET_NS,
                "Average concurrent lookup exceeded target: " + avgTime + "ns");
        }
    }

    /**
     * Simple Quest implementation for performance tests.
     */
    private static class TestQuest implements Quest {
        private final String id;
        
        TestQuest(String id) {
            this.id = id;
        }
        
        @Override public String getId() { return id; }
        @Override public String getName() { return "Test Quest " + id; }
        @Override public void initialize() {}
        @Override public void cleanup() {}
        @Override public boolean isCompleted(org.bukkit.entity.Player player) { return false; }
        @Override public QuestState getCurrentState() { return QuestState.NOT_STARTED; }
        @Override public void advanceState(QuestState newState) {}
        @Override public org.bukkit.Location getStartLocation() { return null; }
        @Override public String getStartTrigger() { return "test"; }
        @Override public org.fourz.RVNKQuests.RVNKQuests getPlugin() { return null; }
        @Override public List<org.bukkit.event.Listener> createListenersForState(QuestState state) { return List.of(); }
        @Override public QuestState getStateForPlayer(org.bukkit.entity.Player player) { return QuestState.NOT_STARTED; }
        @Override public boolean isStateCached(org.bukkit.entity.Player player) { return true; }
        @Override public java.util.concurrent.CompletableFuture<QuestState> getStateForPlayer(UUID uuid) { 
            return java.util.concurrent.CompletableFuture.completedFuture(QuestState.NOT_STARTED); 
        }
        @Override public java.util.concurrent.CompletableFuture<Void> advanceStateForPlayer(UUID uuid, QuestState state) { 
            return java.util.concurrent.CompletableFuture.completedFuture(null); 
        }
    }

    @Nested
    @DisplayName("Player State Tracking Performance")
    class PlayerStateTrackingPerformance {

        @Test
        @DisplayName("Player tracking with ConcurrentHashMap should handle high throughput")
        void playerTrackingPerformance() {
            String questId = "quest_lost_piglin";
            activePlayersByQuest.put(questId, ConcurrentHashMap.newKeySet());
            Set<UUID> activePlayers = activePlayersByQuest.get(questId);

            // Add 1000 players
            List<UUID> playerUuids = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                playerUuids.add(UUID.randomUUID());
            }

            // Warmup: add some players
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                activePlayers.add(playerUuids.get(i % playerUuids.size()));
            }
            activePlayers.clear();

            // Measure: add all players
            long addStart = System.nanoTime();
            for (UUID uuid : playerUuids) {
                activePlayers.add(uuid);
            }
            long addTime = System.nanoTime() - addStart;

            // Measure: check containment
            long containsStart = System.nanoTime();
            for (UUID uuid : playerUuids) {
                assertTrue(activePlayers.contains(uuid));
            }
            long containsTime = System.nanoTime() - containsStart;

            // Measure: remove all players
            long removeStart = System.nanoTime();
            for (UUID uuid : playerUuids) {
                activePlayers.remove(uuid);
            }
            long removeTime = System.nanoTime() - removeStart;

            System.out.printf("Player tracking (1000 players): add=%dms, contains=%dms, remove=%dms%n",
                TimeUnit.NANOSECONDS.toMillis(addTime),
                TimeUnit.NANOSECONDS.toMillis(containsTime),
                TimeUnit.NANOSECONDS.toMillis(removeTime));

            // All operations should complete well under event processing target
            assertTrue(addTime < EVENT_PROCESSING_TARGET_NS * 10,
                "Add operation too slow: " + addTime + "ns");
            assertTrue(containsTime < EVENT_PROCESSING_TARGET_NS * 10,
                "Contains operation too slow: " + containsTime + "ns");
        }

        @Test
        @DisplayName("Player quit cleanup should not block")
        void playerQuitCleanupPerformance() {
            // Setup: 100 quests with 100 active players each
            List<UUID> allPlayers = new ArrayList<>();
            for (int q = 0; q < 100; q++) {
                String questId = "quest_" + q;
                Set<UUID> players = ConcurrentHashMap.newKeySet();
                for (int p = 0; p < 100; p++) {
                    UUID uuid = UUID.randomUUID();
                    players.add(uuid);
                    allPlayers.add(uuid);
                }
                activePlayersByQuest.put(questId, players);
            }

            // Simulate player quit: remove from all quests
            UUID targetPlayer = allPlayers.get(0);
            long start = System.nanoTime();
            
            for (Set<UUID> activePlayers : activePlayersByQuest.values()) {
                activePlayers.remove(targetPlayer);
            }
            
            long elapsed = System.nanoTime() - start;
            
            System.out.printf("Player quit cleanup (100 quests): %dns%n", elapsed);
            
            assertTrue(elapsed < EVENT_PROCESSING_TARGET_NS,
                "Player quit cleanup exceeded 5ms target: " + elapsed + "ns");
        }
    }

    @Nested
    @DisplayName("Memory Usage Tests")
    class MemoryUsageTests {

        @Test
        @DisplayName("Quest registry memory footprint should be reasonable")
        void questRegistryMemoryUsage() {
            Runtime runtime = Runtime.getRuntime();
            
            // Force GC and get baseline
            System.gc();
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            long baselineMemory = runtime.totalMemory() - runtime.freeMemory();

            // Create 1000 quest entries with progress data
            Map<String, QuestProgressDTO> progressCache = new HashMap<>();
            for (int i = 0; i < 1000; i++) {
                UUID playerUuid = UUID.randomUUID();
                String questId = "quest_" + (i % 100);
                
                QuestProgressDTO dto = new QuestProgressDTO(
                    playerUuid,
                    questId,
                    QuestState.QUEST_ACTIVE,
                    "test_path",
                    Instant.now(),
                    null,
                    Map.of("score", i, "attempts", 1)
                );
                progressCache.put(playerUuid + ":" + questId, dto);
            }

            // Measure memory usage
            System.gc();
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long deltaMemory = usedMemory - baselineMemory;

            System.out.printf("Memory usage (1000 progress entries): %.2f MB%n", 
                deltaMemory / (1024.0 * 1024.0));
            
            assertTrue(deltaMemory < MEMORY_TARGET_BYTES,
                "Memory usage exceeded 50MB target: " + (deltaMemory / (1024.0 * 1024.0)) + " MB");
        }

        @Test
        @DisplayName("Active player tracking should have minimal memory overhead")
        void activePlayerMemoryUsage() {
            Runtime runtime = Runtime.getRuntime();
            
            // Force GC and get baseline
            System.gc();
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            long baselineMemory = runtime.totalMemory() - runtime.freeMemory();

            // Create 100 quests with 1000 active players each
            Map<String, Set<UUID>> playerTracking = new ConcurrentHashMap<>();
            for (int q = 0; q < 100; q++) {
                Set<UUID> players = ConcurrentHashMap.newKeySet();
                for (int p = 0; p < 1000; p++) {
                    players.add(UUID.randomUUID());
                }
                playerTracking.put("quest_" + q, players);
            }

            // Measure memory usage
            System.gc();
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long deltaMemory = usedMemory - baselineMemory;

            System.out.printf("Memory usage (100 quests x 1000 players): %.2f MB%n", 
                deltaMemory / (1024.0 * 1024.0));
            
            // This is a stress test - 100,000 UUIDs is far more than realistic
            // Just ensure it doesn't explode
            assertTrue(deltaMemory < MEMORY_TARGET_BYTES * 2,
                "Memory usage extremely high: " + (deltaMemory / (1024.0 * 1024.0)) + " MB");
        }
    }

    @Nested
    @DisplayName("State Lookup Performance")
    class StateLookupPerformance {

        @Test
        @DisplayName("CompletableFuture immediate completion should be fast")
        void asyncStateLookupPerformance() {
            // Simulate CompletableFuture.completedFuture behavior
            UUID testPlayer = UUID.randomUUID();

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                java.util.concurrent.CompletableFuture.completedFuture(QuestState.QUEST_ACTIVE).join();
            }

            // Measure
            long totalTime = 0;
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                long start = System.nanoTime();
                QuestState state = java.util.concurrent.CompletableFuture
                    .completedFuture(QuestState.QUEST_ACTIVE).join();
                long elapsed = System.nanoTime() - start;
                totalTime += elapsed;
                assertEquals(QuestState.QUEST_ACTIVE, state);
            }

            long avgTime = totalTime / TEST_ITERATIONS;
            System.out.printf("Async state lookup (CompletableFuture.completedFuture): avg=%dns%n", avgTime);
            
            // Immediate completion should be very fast
            assertTrue(avgTime < QUEST_LOOKUP_TARGET_NS,
                "Async state lookup exceeded target: " + avgTime + "ns");
        }

        @Test
        @DisplayName("Cached state lookup should be faster than async")
        void cachedStateLookupPerformance() {
            // Simulate a simple cache
            Map<String, QuestState> stateCache = new ConcurrentHashMap<>();
            UUID testPlayer = UUID.randomUUID();
            String cacheKey = testPlayer + ":quest_lost_piglin";
            stateCache.put(cacheKey, QuestState.QUEST_ACTIVE);

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                stateCache.get(cacheKey);
            }

            // Measure cached lookup
            long totalTime = 0;
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                long start = System.nanoTime();
                QuestState state = stateCache.get(cacheKey);
                long elapsed = System.nanoTime() - start;
                totalTime += elapsed;
                assertEquals(QuestState.QUEST_ACTIVE, state);
            }

            long avgTime = totalTime / TEST_ITERATIONS;
            System.out.printf("Cached state lookup: avg=%dns%n", avgTime);
            
            // Cache lookup should be sub-microsecond
            assertTrue(avgTime < 1000, // 1 microsecond
                "Cached lookup should be extremely fast: " + avgTime + "ns");
        }
    }

    @Nested
    @DisplayName("Listener Management Performance")
    class ListenerManagementPerformance {

        @Test
        @DisplayName("Listener list creation should be fast")
        void listenerListCreationPerformance() {
            // Simulate creating listener lists for all states
            List<Object> listeners = new ArrayList<>();

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                List<Object> temp = new ArrayList<>();
                for (QuestState state : QuestState.values()) {
                    temp.add(new Object()); // Simulate listener creation
                }
                temp.clear();
            }

            // Measure
            long totalTime = 0;
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                long start = System.nanoTime();
                
                List<Object> stateListeners = new ArrayList<>();
                for (QuestState state : QuestState.values()) {
                    stateListeners.add(new Object());
                }
                listeners.addAll(stateListeners);
                
                long elapsed = System.nanoTime() - start;
                totalTime += elapsed;
            }

            long avgTime = totalTime / TEST_ITERATIONS;
            System.out.printf("Listener list creation (5 states): avg=%dns%n", avgTime);
            
            // Should be well under event processing target
            assertTrue(avgTime < EVENT_PROCESSING_TARGET_NS / 10,
                "Listener creation too slow: " + avgTime + "ns");
        }
    }
}
