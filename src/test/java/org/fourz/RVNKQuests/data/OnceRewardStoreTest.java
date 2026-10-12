package org.fourz.RVNKQuests.data;

import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.data.dto.RewardType;
import org.fourz.RVNKQuests.service.OnceRewards;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Run-once world rewards (#2268): the claim is atomic, so two simultaneous completions fire a
 * {@code once: server} reward exactly once.
 */
@DisplayName("once: server rewards (#2268)")
class OnceRewardStoreTest {

    private static final String TABLE = "quest_once_rewards";
    private static final String QUEST = "tfah_cavern";

    private String url;
    private Connection keepAlive;
    private ExecutorService executor;
    private OnceRewardRepositoryImpl store;

    @BeforeEach
    void setUp() throws Exception {
        url = "jdbc:h2:mem:once_" + UUID.randomUUID().toString().replace("-", "")
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        keepAlive = DriverManager.getConnection(url);
        try (Statement st = keepAlive.createStatement()) {
            st.execute("CREATE TABLE " + TABLE + " (quest_id VARCHAR(100) NOT NULL, reward_id VARCHAR(200) NOT NULL,"
                + " fired_by VARCHAR(36) NOT NULL, fired_at BIGINT NOT NULL, PRIMARY KEY (quest_id, reward_id))");
        }
        executor = Executors.newFixedThreadPool(4);
        store = new OnceRewardRepositoryImpl(() -> DriverManager.getConnection(url), TABLE, true, executor);
    }

    @AfterEach
    void tearDown() throws Exception {
        executor.shutdownNow();
        keepAlive.close();
    }

    private int rows() throws Exception {
        try (Statement st = keepAlive.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + TABLE)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static RewardDTO once(String id, String command) {
        return new RewardDTO(id, RewardType.COMMAND, command, 1, null, Map.of("once", "server"));
    }

    @Test
    @DisplayName("the first claim inserts, the second finds the row")
    void claimOnce() throws Exception {
        UUID a = UUID.randomUUID();
        assertTrue(store.tryClaim(QUEST, "cavern_wake", a).get(5, TimeUnit.SECONDS));
        assertFalse(store.tryClaim(QUEST, "cavern_wake", UUID.randomUUID()).get(5, TimeUnit.SECONDS));
        assertTrue(store.tryClaim(QUEST, "other", a).get(5, TimeUnit.SECONDS));
        assertTrue(store.tryClaim("another_quest", "cavern_wake", a).get(5, TimeUnit.SECONDS));
        assertEquals(3, rows());

        List<IOnceRewardStore.OnceRecord> records = store.list(QUEST).get(5, TimeUnit.SECONDS);
        assertEquals(List.of("cavern_wake", "other"), records.stream().map(IOnceRewardStore.OnceRecord::rewardKey).toList());
        assertEquals(a, records.get(0).firedBy());
    }

    @Test
    @DisplayName("racing claims on one key: exactly one wins, every time")
    void racingClaimsHaveOneWinner() throws Exception {
        for (int round = 0; round < 25; round++) {
            String key = "race_" + round;
            CountDownLatch start = new CountDownLatch(1);
            List<CompletableFuture<Boolean>> claims = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                claims.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return store.tryClaim(QUEST, key, UUID.randomUUID()).join();
                }));
            }
            start.countDown();
            long winners = 0;
            for (CompletableFuture<Boolean> c : claims) {
                if (c.get(10, TimeUnit.SECONDS)) winners++;
            }
            assertEquals(1, winners, "round " + round);
        }
        assertEquals(25, rows());
    }

    @Test
    @DisplayName("two simultaneous completions: the once reward fires for one, the plain reward for both")
    void twoCompletionsFireOnce() throws Exception {
        RewardDTO plain = RewardDTO.create("cavern_xp", RewardType.EXPERIENCE, "50", 50);
        RewardDTO wake = once("cavern_wake", "setblock 10 60 10 minecraft:air");
        List<RewardDTO> rewards = List.of(wake, plain);
        List<String> warnings = new CopyOnWriteArrayList<>();

        for (int round = 0; round < 10; round++) {
            store.reset(QUEST, null).get(5, TimeUnit.SECONDS);
            CountDownLatch start = new CountDownLatch(1);
            List<CompletableFuture<List<RewardDTO>>> completions = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                UUID player = UUID.randomUUID();
                completions.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return OnceRewards.claimAndFilter(store, QUEST, null, player, rewards, warnings::add).join();
                }));
            }
            start.countDown();
            List<RewardDTO> first = completions.get(0).get(10, TimeUnit.SECONDS);
            List<RewardDTO> second = completions.get(1).get(10, TimeUnit.SECONDS);

            int wakes = (first.contains(wake) ? 1 : 0) + (second.contains(wake) ? 1 : 0);
            assertEquals(1, wakes, "round " + round + ": the world change must fire exactly once");
            assertTrue(first.contains(plain) && second.contains(plain), "plain rewards fire for every completion");
            List<RewardDTO> winner = first.contains(wake) ? first : second;
            assertEquals(List.of(wake, plain), winner, "the surviving rewards keep their order");
        }
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    @DisplayName("reset lets one reward, or every reward of the quest, fire again")
    void resetReArms() throws Exception {
        UUID p = UUID.randomUUID();
        store.tryClaim(QUEST, "a", p).get(5, TimeUnit.SECONDS);
        store.tryClaim(QUEST, "b", p).get(5, TimeUnit.SECONDS);
        store.tryClaim("keep_me", "a", p).get(5, TimeUnit.SECONDS);

        assertEquals(1, store.reset(QUEST, "a").get(5, TimeUnit.SECONDS));
        assertEquals(0, store.reset(QUEST, "a").get(5, TimeUnit.SECONDS));
        assertTrue(store.tryClaim(QUEST, "a", p).get(5, TimeUnit.SECONDS));

        assertEquals(2, store.reset(QUEST, null).get(5, TimeUnit.SECONDS));
        assertEquals(1, rows(), "another quest's record is untouched");
    }

    @Test
    @DisplayName("no store, or a failing store: the once reward is dropped and named, the rest still fire")
    void failsClosed() throws Exception {
        RewardDTO plain = RewardDTO.create("xp", RewardType.EXPERIENCE, "5", 5);
        RewardDTO wake = once("wake", "say wake");
        List<String> warnings = new ArrayList<>();

        List<RewardDTO> out = OnceRewards.claimAndFilter(null, QUEST, null, UUID.randomUUID(),
            List.of(wake, plain), warnings::add).get(5, TimeUnit.SECONDS);
        assertEquals(List.of(plain), out);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("wake"), warnings.get(0));

        IOnceRewardStore broken = new OnceRewardRepositoryImpl(() -> {
            throw new java.sql.SQLException("pool closed");
        }, TABLE, true, executor);
        warnings.clear();
        out = OnceRewards.claimAndFilter(broken, QUEST, null, UUID.randomUUID(),
            List.of(wake, plain), warnings::add).get(5, TimeUnit.SECONDS);
        assertEquals(List.of(plain), out);
        assertTrue(warnings.get(0).contains("pool closed"), warnings.toString());
    }

    @Test
    @DisplayName("on_advance entries are keyed <component>/<reward_id>, apart from completion rewards")
    void onAdvanceKeysAreNamespaced() throws Exception {
        RewardDTO wake = once("wake", "say wake");
        UUID p = UUID.randomUUID();
        assertEquals(List.of(wake), OnceRewards.claimAndFilter(store, QUEST, null, p, List.of(wake), s -> { })
            .get(5, TimeUnit.SECONDS));
        assertEquals(List.of(wake), OnceRewards.claimAndFilter(store, QUEST, "obj_well", p, List.of(wake), s -> { })
            .get(5, TimeUnit.SECONDS));
        assertEquals(List.of("obj_well/wake", "wake"), store.list(QUEST).get(5, TimeUnit.SECONDS).stream()
            .map(IOnceRewardStore.OnceRecord::rewardKey).toList());
    }

    @Test
    @DisplayName("the YAML-mode store claims once, survives a reload, and resets")
    void yamlStore(@TempDir Path dir) throws Exception {
        java.io.File file = dir.resolve("once_rewards.yml").toFile();
        OnceRewardYamlStore yaml = new OnceRewardYamlStore(file);
        UUID p = UUID.randomUUID();
        assertTrue(yaml.tryClaim(QUEST, "cavern.wake", p).get());
        assertFalse(new OnceRewardYamlStore(file).tryClaim(QUEST, "cavern.wake", p).get(), "persisted to disk");
        assertTrue(yaml.tryClaim(QUEST, "obj_well/wake", p).get());
        assertEquals(2, yaml.list(QUEST).get().size());
        assertEquals(1, yaml.reset(QUEST, "cavern.wake").get());
        assertTrue(yaml.tryClaim(QUEST, "cavern.wake", p).get());
        assertEquals(2, yaml.reset(QUEST, null).get());
        assertTrue(yaml.list(QUEST).get().isEmpty());
    }
}
