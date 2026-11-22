package org.fourz.RVNKQuests.quest;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.dto.QuestDTO;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * #2418: a party member whose state moved with the firer hears the beat's line once; the firer is not sent it
 * twice.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("party member advance_message (#2418)")
class MemberFeedbackTest {

    @Mock private RVNKQuests plugin;
    @Mock private Server server;
    @Mock private org.bukkit.scheduler.BukkitScheduler scheduler;
    @Mock private Player firer;
    @Mock private Player member;

    private final UUID firerId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(any(org.bukkit.plugin.Plugin.class), any(Runnable.class)))
            .thenAnswer(inv -> { inv.getArgument(1, Runnable.class).run(); return null; });
        when(server.getPlayer(firerId)).thenReturn(firer);
        when(server.getPlayer(memberId)).thenReturn(member);
    }

    private DataDrivenQuest quest() {
        Map<String, Object> graveyard = new HashMap<>();
        graveyard.put("type", "NPC_INTERACT");
        graveyard.put("npc_key", "sexton");
        graveyard.put("advance_message", "&7[Hallow] Every stone has a name.");
        graveyard.put("advance_sound", "none");
        Map<String, Object> silent = new HashMap<>();
        silent.put("type", "NPC_INTERACT");
        silent.put("npc_key", "porter");
        Map<String, Object> components = new HashMap<>();
        components.put("obj_graveyard", graveyard);
        components.put("obj_silent", silent);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("components", components);
        metadata.put("state_mapping", Map.of("NOT_STARTED", List.of("obj_graveyard", "obj_silent")));
        return new DataDrivenQuest(plugin, new QuestDTO("hallow_town", "Hallow Town", "", null, false, 0,
            null, null, null, null, metadata));
    }

    @Test
    @DisplayName("the member gets the line once, the firer gets nothing from this hook")
    void memberHearsTheLine() {
        DataDrivenQuest quest = quest();
        Consumer<UUID> hook = quest.memberFeedbackHook("OBJ_GRAVEYARD", firerId);
        assertNotNull(hook);
        hook.accept(firerId);
        hook.accept(memberId);
        verify(member, times(1)).sendMessage("§7[Hallow] Every stone has a name.");
        verify(firer, never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("a component without advance_message has no member hook")
    void silentComponentHasNoHook() {
        DataDrivenQuest quest = quest();
        assertNull(quest.memberFeedbackHook("obj_silent", firerId));
        assertNull(quest.memberFeedbackHook(mock(Listener.class), firerId));
    }

    @Test
    @DisplayName("both() runs the second hook even when the first throws")
    void bothRunsEach() {
        List<String> ran = new ArrayList<>();
        Consumer<UUID> first = u -> { ran.add("first"); throw new IllegalStateException("boom"); };
        Consumer<UUID> second = u -> ran.add("second");
        assertSame(second, ComponentAdvance.both(null, second));
        assertSame(first, ComponentAdvance.both(first, null));
        assertThrows(IllegalStateException.class, () -> ComponentAdvance.both(first, second).accept(memberId));
        assertEquals(List.of("first", "second"), ran);
    }
}
