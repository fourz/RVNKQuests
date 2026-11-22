package org.fourz.RVNKQuests.objective.generic;

import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.npc.NpcTestSupport;
import org.fourz.RVNKQuests.party.PartyBeatContext;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.fourz.RVNKQuests.npc.NpcTestSupport.config;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** BLOCK_STATE: every check must match at once; parsing, validation and path gating (#2389). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BLOCK_STATE objective (#2389)")
class BlockStateObjectiveTest {

    private static final String ON = "minecraft:lever[face=wall,facing=north,powered=true]";
    private static final String OFF = "minecraft:lever[face=wall,facing=north,powered=false]";

    private static final Executor DIRECT = Runnable::run;

    @Mock private RVNKQuests plugin;

    private Player player;
    private AtomicReference<QuestState> state;
    private DataDrivenQuest quest;

    @BeforeEach
    void setUp() {
        player = NpcTestSupport.player("Tester");
        state = new AtomicReference<>(QuestState.QUEST_ACTIVE);
        quest = NpcTestSupport.quest("lever_puzzle", state, true);
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    private static Map<String, Object> lever(int x, boolean powered) {
        return map("world", "world", "x", x, "y", 64, "z", 10, "block", "LEVER", "state", map("powered", powered));
    }

    /** Three levers: left on, middle off, right on. */
    private static List<Object> threeLevers() {
        return List.of(lever(1, true), lever(2, false), lever(3, true));
    }

    private GenericBlockStateObjective objective(Object... kv) {
        Map<String, Object> cfg = config("objective_type", "BLOCK_STATE", kv);
        cfg.putIfAbsent("checks", threeLevers());
        return new GenericBlockStateObjective(plugin, quest, cfg, DIRECT, DIRECT);
    }

    /** Block data by x coordinate. */
    private static Function<BlockStateCheck, String> world(String atX1, String atX2, String atX3) {
        Map<Integer, String> m = new HashMap<>();
        m.put(1, atX1);
        m.put(2, atX2);
        m.put(3, atX3);
        return c -> m.get(c.x());
    }

    private void assertNotAdvanced() {
        verify(quest, never()).tryAdvanceStateForPlayer(any(UUID.class), any(QuestState.class), any());
    }

    // ==================== Property parsing ====================

    @Test
    @DisplayName("BlockData.getAsString() splits into key and properties")
    void parsesBlockData() {
        BlockStateCheck.Parsed p = BlockStateCheck.parseBlockData(ON);
        assertEquals("minecraft:lever", p.key());
        assertEquals(Map.of("face", "wall", "facing", "north", "powered", "true"), p.properties());

        BlockStateCheck.Parsed bare = BlockStateCheck.parseBlockData("minecraft:stone");
        assertEquals("minecraft:stone", bare.key());
        assertTrue(bare.properties().isEmpty());
    }

    @Test
    @DisplayName("YAML booleans, numbers and mixed case match the lower-case BlockData values")
    void normalisesExpectedValues() {
        BlockStateCheck door = BlockStateCheck.parse(
            map("world", "w", "x", 0, "y", 0, "z", 0, "state", map("OPEN", true, "Facing", "NORTH")), null, 1);
        assertTrue(door.matches("minecraft:oak_door[facing=north,half=lower,hinge=left,open=true,powered=false]"));
        assertFalse(door.matches("minecraft:oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]"));

        BlockStateCheck wire = BlockStateCheck.parse(
            map("world", "w", "x", 0, "y", 0, "z", 0, "state", map("power", 15)), null, 1);
        assertTrue(wire.matches("minecraft:redstone_wire[east=side,north=none,power=15,south=none,west=side]"));
    }

    @Test
    @DisplayName("a property the block does not have, or a different block, is a mismatch")
    void missingPropertyAndWrongBlock() {
        BlockStateCheck typo = BlockStateCheck.parse(
            map("world", "w", "x", 0, "y", 0, "z", 0, "state", map("powerd", true)), null, 1);
        assertFalse(typo.matches(ON));

        BlockStateCheck button = BlockStateCheck.parse(
            map("world", "w", "x", 0, "y", 0, "z", 0, "block", "STONE_BUTTON", "state", map("powered", true)), null, 1);
        assertEquals("minecraft:stone_button", button.blockKey());
        assertFalse(button.matches(ON));
        assertFalse(button.matches(null));
    }

    @Test
    @DisplayName("coordinates as YAML ints, Gson doubles or numeric strings; world from the component")
    void coordinateForms() {
        BlockStateCheck a = BlockStateCheck.parse(map("x", 5, "y", 64, "z", -3, "state", map("lit", true)), "outpost", 1);
        BlockStateCheck b = BlockStateCheck.parse(map("x", 5.0, "y", 64.0, "z", -3.0, "state", map("lit", true)), "outpost", 1);
        BlockStateCheck c = BlockStateCheck.parse(map("x", "5", "y", " 64 ", "z", "-3", "state", map("lit", true)), "outpost", 1);
        assertEquals(a, b);
        assertEquals(a, c);
        assertEquals("outpost", a.world());
    }

    // ==================== Validation ====================

    @Test
    @DisplayName("a check with a missing coordinate is rejected at construction")
    void missingCoordRejected() {
        Map<String, Object> noY = map("world", "world", "x", 1, "z", 10, "state", map("powered", true));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> objective("checks", List.of(lever(1, true), noY)));
        assertTrue(e.getMessage().contains("check #2") && e.getMessage().contains("'y'"), e.getMessage());
    }

    @Test
    @DisplayName("quest component add / validate path reports the missing coordinate")
    void factoryRejectsMissingCoord() {
        QuestComponentFactory factory = new QuestComponentFactory(plugin, quest);
        Map<String, Object> cfg = config("objective_type", "BLOCK_STATE",
            "checks", List.of(map("world", "world", "y", 64, "z", 10, "state", map("powered", true))));
        String problem = factory.validateComponentConfig("obj_levers", cfg);
        assertNotNull(problem);
        assertTrue(problem.contains("'x'"), problem);

        Map<String, Object> good = config("objective_type", "BLOCK_STATE", "checks", threeLevers());
        assertNull(factory.validateComponentConfig("obj_levers", good));
    }

    @Test
    @DisplayName("no checks, no world, an unknown block, or neither block nor state is rejected")
    void otherInvalidConfigs() {
        assertThrows(IllegalArgumentException.class, () -> BlockStateCheck.parseAll(null, null));
        assertThrows(IllegalArgumentException.class, () -> BlockStateCheck.parseAll(List.of(), null));
        assertThrows(IllegalArgumentException.class,
            () -> BlockStateCheck.parseAll(List.of(map("x", 1, "y", 2, "z", 3, "state", map("open", true))), null));
        assertThrows(IllegalArgumentException.class,
            () -> BlockStateCheck.parseAll(List.of(map("world", "w", "x", 1, "y", 2, "z", 3, "block", "lever!")), null));
        assertThrows(IllegalArgumentException.class,
            () -> BlockStateCheck.parseAll(List.of(map("world", "w", "x", 1, "y", 2, "z", 3, "block", "NOT_A_BLOCK")), null));
        assertThrows(IllegalArgumentException.class,
            () -> BlockStateCheck.parseAll(List.of(map("world", "w", "x", 1, "y", 2, "z", 3)), null));
    }

    // ==================== Matching ====================

    @Test
    @DisplayName("all three levers right: matches and advances the player")
    void allChecksMatch() {
        GenericBlockStateObjective o = objective("advance_state", "COMPLETED");
        assertTrue(o.allMatch(world(ON, OFF, ON)));
        assertEquals(1, o.tryComplete(List.of(player), world(ON, OFF, ON)));
        verify(quest).tryAdvanceStateForPlayer(eq(player.getUniqueId()), eq(QuestState.COMPLETED),
            any(PartyBeatContext.class));
        assertEquals(QuestState.COMPLETED, state.get());
    }

    @Test
    @DisplayName("one lever wrong: no match, no advance")
    void oneWrongCheck() {
        GenericBlockStateObjective o = objective();
        assertFalse(o.allMatch(world(ON, ON, ON)));
        assertFalse(o.allMatch(world(OFF, OFF, ON)));
        assertFalse(o.allMatch(world(ON, OFF, null)));   // unloaded chunk
        assertEquals(0, o.tryComplete(List.of(player), world(ON, ON, ON)));
        assertNotAdvanced();
        assertEquals(QuestState.QUEST_ACTIVE, state.get());
    }

    @Test
    @DisplayName("the right combination at the wrong beat does nothing")
    void wrongStateDoesNothing() {
        state.set(QuestState.TRIGGER_FOUND);
        assertEquals(0, objective().tryComplete(List.of(player), world(ON, OFF, ON)));
        assertNotAdvanced();
    }

    @Test
    @DisplayName("requires_path gates the objective to its branch; sets_path is written on success")
    void pathGating() {
        when(quest.getPathChoiceCached(any(Player.class))).thenReturn("left");
        assertEquals(0, objective("requires_path", "right").tryComplete(List.of(player), world(ON, OFF, ON)));
        assertNotAdvanced();

        assertEquals(1, objective("requires_path", "left", "sets_path", "levers_done")
            .tryComplete(List.of(player), world(ON, OFF, ON)));
        verify(quest).setPathChoice(player, "levers_done");
    }

    @Test
    @DisplayName("events in one tick share one evaluation")
    void coalescesEvents() {
        List<Runnable> queued = new ArrayList<>();
        Map<String, Object> cfg = config("objective_type", "BLOCK_STATE", "checks", threeLevers());
        GenericBlockStateObjective o = new GenericBlockStateObjective(plugin, quest, cfg, queued::add, DIRECT);
        o.schedule(player, false);
        o.schedule(null, true);
        o.schedule(player, false);
        assertEquals(1, queued.size());
    }

    @Test
    @DisplayName("radius defaults to 16 and is configurable")
    void radiusDefault() {
        assertEquals(16.0, objective().getRadius());
        assertEquals(8.0, objective("radius", 8).getRadius());
    }
}
