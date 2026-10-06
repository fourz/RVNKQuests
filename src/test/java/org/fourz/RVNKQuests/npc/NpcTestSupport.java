package org.fourz.RVNKQuests.npc;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.party.PartyBeatContext;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Shared fixtures for the #2214 NPC tests. */
public final class NpcTestSupport {

    private NpcTestSupport() {}

    public static Player player(String name) {
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn(name);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        return player;
    }

    public static RvnkNpcInteractEvent click(Player player, String key, RvnkNpcInteractEvent.ClickType type) {
        World world = player.getWorld();
        return new RvnkNpcInteractEvent(player, key, "Npc " + key, type, new Location(world, 10, 64, 10));
    }

    public static RvnkNpcInteractEvent rightClick(Player player, String key) {
        return click(player, key, RvnkNpcInteractEvent.ClickType.RIGHT);
    }

    /**
     * A quest mock whose state is held in {@code state}. When {@code commits} is true an advance
     * moves the state to its target, as a committed advance does; when false it is refused (the
     * prerequisite gate, for example) and the state stays.
     */
    public static DataDrivenQuest quest(String id, AtomicReference<QuestState> state, boolean commits) {
        DataDrivenQuest quest = mock(DataDrivenQuest.class);
        when(quest.getId()).thenReturn(id);
        when(quest.getName()).thenReturn("Quest " + id);
        when(quest.getStateForPlayer(any(Player.class))).thenAnswer(inv -> state.get());
        when(quest.advanceStateForPlayer(any(UUID.class), any(QuestState.class), any(PartyBeatContext.class)))
            .thenAnswer(inv -> {
                if (commits) state.set(inv.getArgument(1, QuestState.class));
                return CompletableFuture.completedFuture(null);
            });
        return quest;
    }

    public static Map<String, Object> config(String typeKey, String typeValue, Object... kv) {
        Map<String, Object> config = new java.util.HashMap<>();
        config.put(typeKey, typeValue);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            config.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return config;
    }

    /** Captures WARNING records on the {@code RVNKCore} logger, where LogManager writes for a mock plugin. */
    public static final class WarningCapture implements AutoCloseable {
        private final Logger logger = Logger.getLogger("RVNKCore");
        private final List<String> messages = new ArrayList<>();
        private final Handler handler = new Handler() {
            @Override public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    synchronized (messages) { messages.add(record.getMessage()); }
                }
            }
            @Override public void flush() { }
            @Override public void close() { }
        };

        public WarningCapture() {
            logger.addHandler(handler);
        }

        public List<String> messages() {
            synchronized (messages) { return List.copyOf(messages); }
        }

        @Override
        public void close() {
            logger.removeHandler(handler);
        }
    }
}
