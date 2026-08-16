package org.fourz.RVNKQuests.trigger.generic;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The {@code STRUCTURE_INTERACT} runtime gate — the half of #1894 that "it compiles and loads"
 * never proved.
 *
 * <p>#1894's core defect was that a trigger declaring {@code x}/{@code y}/{@code z} was in fact
 * world-wide: right-clicking <b>any</b> lectern anywhere in {@code alphac} started
 * {@code tfah_ch1_journey}. The fix landed in 1.1.22, but its verification was parked four times
 * because the only test anyone could name needed a human to right-click two lecterns in a live
 * world — and the trigger's silence on the off-site block is indistinguishable from a click that
 * never happened (#1930).</p>
 *
 * <p>The gate itself does not need a world. It is a pure decision over
 * (world, block type, distance, state), so it can be asserted here and the tester session reduced
 * to what genuinely needs Bukkit: that a real {@code PlayerInteractEvent} reaches this listener at
 * all.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("STRUCTURE_INTERACT gate (#1894)")
class StructureInteractGateTest {

    @Mock private RVNKQuests plugin;
    @Mock private DataDrivenQuest quest;
    @Mock private World alphac;
    @Mock private World elsewhere;

    private UUID playerId;
    private Player player;

    /** The real siting of {@code tfah_ch1_journey}'s start lectern. */
    private static final int SITE_X = -315;
    private static final int SITE_Y = 118;
    private static final int SITE_Z = 446;

    @BeforeEach
    void setUp() {
        playerId = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Tester");
        when(player.getWorld()).thenReturn(alphac);
        when(alphac.getName()).thenReturn("alphac");
        when(elsewhere.getName()).thenReturn("zeal");

        when(quest.getId()).thenReturn("tfah_ch1_journey");
        when(quest.getStateForPlayer(any(Player.class))).thenReturn(QuestState.NOT_STARTED);
        when(quest.advanceStateForPlayer(any(UUID.class), any(QuestState.class)))
            .thenReturn(CompletableFuture.completedFuture(null));
        // #1986: the trigger now fires through the party-aware overload.
        when(quest.advanceStateForPlayer(any(UUID.class), any(QuestState.class),
                any(org.fourz.RVNKQuests.party.PartyBeatContext.class)))
            .thenReturn(CompletableFuture.completedFuture(null));
    }

    /** The config {@code tfah_ch1_journey} actually ships, minus whatever a case overrides. */
    private static Map<String, Object> sitedConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("type", "STRUCTURE_INTERACT");
        config.put("block_type", "LECTERN");
        config.put("world", "alphac");
        config.put("x", SITE_X);
        config.put("y", SITE_Y);
        config.put("z", SITE_Z);
        config.put("required_state", "NOT_STARTED");
        config.put("advance_state", "TRIGGER_FOUND");
        return config;
    }

    private GenericStructureInteractTrigger trigger(Map<String, Object> config) {
        return new GenericStructureInteractTrigger(plugin, quest, config);
    }

    private Block blockAt(Material type, int x, int y, int z) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(type);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        // #1986 wired the party fan-out into this trigger, which reads the clicked block's
        // Location to build the beat checkpoint. Without this the trigger NPEs on every fire —
        // which is how these tests caught the wiring in the first place.
        when(block.getLocation()).thenReturn(new org.bukkit.Location(alphac, x, y, z));
        return block;
    }

    private PlayerInteractEvent clickOn(Block block) {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getClickedBlock()).thenReturn(block);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    private void assertAdvanced() {
        verify(quest).advanceStateForPlayer(eq(playerId), eq(QuestState.TRIGGER_FOUND),
            any(org.fourz.RVNKQuests.party.PartyBeatContext.class));
    }

    /** Covers both overloads — the gate must refuse regardless of which path a future edit uses. */
    private void assertNotAdvanced() {
        verify(quest, never()).advanceStateForPlayer(any(UUID.class), any(QuestState.class));
        verify(quest, never()).advanceStateForPlayer(any(UUID.class), any(QuestState.class),
            any(org.fourz.RVNKQuests.party.PartyBeatContext.class));
    }

    /** The party checkpoint the trigger published, for asserting where the beat was sited. */
    private org.fourz.RVNKQuests.party.PartyBeatContext capturedContext() {
        var captor = org.mockito.ArgumentCaptor.forClass(
            org.fourz.RVNKQuests.party.PartyBeatContext.class);
        verify(quest).advanceStateForPlayer(any(UUID.class), any(QuestState.class), captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("coordinates are honoured — the #1894 defect")
    class Siting {

        @Test
        @DisplayName("a lectern 40 blocks away does NOT fire the trigger")
        void offSiteLecternDoesNotFire() {
            // The exact scenario in the issue: right block type, right world, wrong place.
            // Before the fix this started Chapter 1.
            trigger(sitedConfig())
                .onPlayerInteract(clickOn(blockAt(Material.LECTERN, SITE_X + 40, SITE_Y, SITE_Z)));

            assertNotAdvanced();
        }

        @Test
        @DisplayName("the sited lectern fires the trigger")
        void onSiteLecternFires() {
            trigger(sitedConfig())
                .onPlayerInteract(clickOn(blockAt(Material.LECTERN, SITE_X, SITE_Y, SITE_Z)));

            assertAdvanced();
        }

        @Test
        @DisplayName("2.24 blocks off still fires — the live margin the quest has been running on")
        void withinDefaultRadiusFires() {
            // Measured on Event: the real lectern sits ~2.24 blocks from the declared coords and
            // the quest works. Default radius is 3.0, so this is 0.76 blocks of margin — the
            // situation recorded on this issue 2026-08-02. Pinning it means a radius change cannot
            // silently break a live quest.
            trigger(sitedConfig())
                .onPlayerInteract(clickOn(blockAt(Material.LECTERN, SITE_X + 2, SITE_Y + 1, SITE_Z)));

            assertAdvanced();
        }

        @Test
        @DisplayName("4 blocks off does not fire — the radius has an edge")
        void outsideDefaultRadiusDoesNotFire() {
            trigger(sitedConfig())
                .onPlayerInteract(clickOn(blockAt(Material.LECTERN, SITE_X + 4, SITE_Y, SITE_Z)));

            assertNotAdvanced();
        }

        @Test
        @DisplayName("the party checkpoint is the CLICKED block, not the configured coordinate")
        void partyCheckpointIsTheClickedBlock() {
            // #1986: an unsited trigger has no configured coordinate at all, and even a sited one
            // can be clicked up to `radius` away. Sharing from the clicked block measures member
            // presence from where the beat actually happened.
            trigger(sitedConfig())
                .onPlayerInteract(clickOn(blockAt(Material.LECTERN, SITE_X + 2, SITE_Y, SITE_Z)));

            var ctx = capturedContext();
            assertEquals(SITE_X + 2, ctx.x(), 0.001, "checkpoint x is the clicked block, not config");
            assertEquals("alphac", ctx.worldName());
            assertEquals(QuestState.NOT_STARTED, ctx.requiredState(),
                "the in-step gate must carry this trigger's own required_state");
        }

        @Test
        @DisplayName("an explicit radius widens the match")
        void explicitRadiusIsRead() {
            Map<String, Object> config = sitedConfig();
            config.put("radius", 10.0);

            trigger(config)
                .onPlayerInteract(clickOn(blockAt(Material.LECTERN, SITE_X + 8, SITE_Y, SITE_Z)));

            assertAdvanced();
        }
    }

    @Nested
    @DisplayName("un-sited triggers keep the old world-wide behaviour")
    class Unsited {

        @Test
        @DisplayName("no coordinates means any block of the type in the world fires")
        void noCoordinatesIsWorldWide() {
            Map<String, Object> config = sitedConfig();
            config.remove("x");
            config.remove("y");
            config.remove("z");

            trigger(config)
                .onPlayerInteract(clickOn(blockAt(Material.LECTERN, 9999, 64, -9999)));

            assertAdvanced();
        }

        @Test
        @DisplayName("a partial coordinate falls back to world-wide rather than half-siting")
        void partialCoordinatesFallBack() {
            Map<String, Object> config = sitedConfig();
            config.remove("y");

            trigger(config)
                .onPlayerInteract(clickOn(blockAt(Material.LECTERN, 9999, 64, -9999)));

            assertAdvanced();
        }
    }

    @Nested
    @DisplayName("the other gates still hold")
    class OtherGates {

        @Test
        @DisplayName("a lectern in the wrong world does not fire")
        void wrongWorldDoesNotFire() {
            when(player.getWorld()).thenReturn(elsewhere);

            trigger(sitedConfig())
                .onPlayerInteract(clickOn(blockAt(Material.LECTERN, SITE_X, SITE_Y, SITE_Z)));

            assertNotAdvanced();
        }

        @Test
        @DisplayName("the wrong block type on the exact site does not fire")
        void wrongBlockTypeDoesNotFire() {
            trigger(sitedConfig())
                .onPlayerInteract(clickOn(blockAt(Material.DIRT, SITE_X, SITE_Y, SITE_Z)));

            assertNotAdvanced();
        }

        @Test
        @DisplayName("a null clicked block is ignored — right-clicking air")
        void nullBlockIsIgnored() {
            trigger(sitedConfig()).onPlayerInteract(clickOn(null));

            assertNotAdvanced();
        }
    }

    @Nested
    @DisplayName("required_state is read, not hardcoded — the second half of #1894")
    class RequiredState {

        @Test
        @DisplayName("the wrong state does not advance, even on the exact block")
        void wrongStateDoesNotAdvance() {
            when(quest.getStateForPlayer(any(Player.class))).thenReturn(QuestState.TRIGGER_FOUND);

            trigger(sitedConfig())
                .onPlayerInteract(clickOn(blockAt(Material.LECTERN, SITE_X, SITE_Y, SITE_Z)));

            assertNotAdvanced();
        }

        @Test
        @DisplayName("a non-default required_state is honoured instead of NOT_STARTED")
        void nonDefaultRequiredStateIsHonoured() {
            // The silently-wrong case: before the fix this was hardcoded to NOT_STARTED, so a
            // quest asking for a later beat got a gate it never declared.
            Map<String, Object> config = sitedConfig();
            config.put("required_state", "TRIGGER_FOUND");
            config.put("advance_state", "QUEST_ACTIVE");
            when(quest.getStateForPlayer(any(Player.class))).thenReturn(QuestState.TRIGGER_FOUND);

            trigger(config)
                .onPlayerInteract(clickOn(blockAt(Material.LECTERN, SITE_X, SITE_Y, SITE_Z)));

            verify(quest).advanceStateForPlayer(eq(playerId), eq(QuestState.QUEST_ACTIVE),
                any(org.fourz.RVNKQuests.party.PartyBeatContext.class));
        }

        @Test
        @DisplayName("a non-default required_state also refuses NOT_STARTED")
        void nonDefaultRequiredStateRefusesTheOldHardcode() {
            Map<String, Object> config = sitedConfig();
            config.put("required_state", "TRIGGER_FOUND");
            when(quest.getStateForPlayer(any(Player.class))).thenReturn(QuestState.NOT_STARTED);

            trigger(config)
                .onPlayerInteract(clickOn(blockAt(Material.LECTERN, SITE_X, SITE_Y, SITE_Z)));

            assertNotAdvanced();
        }

        @Test
        @DisplayName("an unparseable required_state falls back rather than throwing")
        void unparseableStateFallsBack() {
            Map<String, Object> config = sitedConfig();
            config.put("required_state", "NOT_A_REAL_STATE");

            trigger(config)
                .onPlayerInteract(clickOn(blockAt(Material.LECTERN, SITE_X, SITE_Y, SITE_Z)));

            assertAdvanced();
        }
    }

    @Nested
    @DisplayName("ordering")
    class Ordering {

        @Test
        @DisplayName("an off-site block never reaches the state check")
        void offSiteShortCircuitsBeforeState() {
            // The gate order is world -> type -> site -> state. If site were checked after state,
            // an off-site click on a player at the required state would still advance. Asserting
            // the state was never consulted pins the order, not just the outcome.
            trigger(sitedConfig())
                .onPlayerInteract(clickOn(blockAt(Material.LECTERN, SITE_X + 40, SITE_Y, SITE_Z)));

            verify(quest, never()).getStateForPlayer(any(Player.class));
            assertNotAdvanced();
        }
    }
}
