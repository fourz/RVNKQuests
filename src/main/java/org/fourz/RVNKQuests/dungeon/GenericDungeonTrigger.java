package org.fourz.RVNKQuests.dungeon;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.util.AdvanceFeedback;
import org.fourz.rvnkcore.api.event.DungeonEnterEvent;
import org.fourz.rvnkcore.api.event.DungeonLeaveEvent;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * DUNGEON_ENTER / DUNGEON_LEAVE triggers (#2371): advance when a player crosses into or out of a
 * resettable dungeon's box (RVNKWorlds, contract #2368).
 *
 * <h3>Config keys</h3>
 * <ul>
 *   <li>{@code dungeon} — the dungeon id (required)</li>
 *   <li>{@code required_state} — default {@code NOT_STARTED} for ENTER, {@code QUEST_ACTIVE} for LEAVE</li>
 *   <li>{@code advance_state} — default {@code TRIGGER_FOUND} for ENTER, {@code OBJECTIVE_FOUND} for LEAVE</li>
 *   <li>{@code description}, {@code advance_message}, {@code on_advance} — as for other components</li>
 * </ul>
 *
 * <p>Built only when {@link DungeonApi#isPresent()} is true; see the factory.</p>
 */
public class GenericDungeonTrigger implements Listener {

    public enum Mode { ENTER, LEAVE }

    private final DataDrivenQuest quest;
    private final LogManager logger;
    private final Mode mode;
    private final String dungeonId;
    private final QuestState requiredState;
    private final QuestState advanceState;
    private final AdvanceFeedback feedback;
    private final Executor mainThread;

    public GenericDungeonTrigger(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config, Mode mode) {
        this.quest = quest;
        this.mode = mode;
        this.logger = LogManager.getInstance(plugin, "GenericDungeonTrigger");
        String raw = QuestComponentFactory.getStringConfig(config, "dungeon", null);
        this.dungeonId = raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
        if (dungeonId == null || dungeonId.isEmpty()) {
            logger.warning("Quest '" + quest.getId() + "': DUNGEON_" + mode + " has no 'dungeon' - it will never fire");
        }
        boolean enter = mode == Mode.ENTER;
        this.requiredState = parse(QuestComponentFactory.getStringConfig(config, "required_state",
                enter ? "NOT_STARTED" : "QUEST_ACTIVE"), enter ? QuestState.NOT_STARTED : QuestState.QUEST_ACTIVE);
        this.advanceState = parse(QuestComponentFactory.getStringConfig(config, "advance_state",
                enter ? "TRIGGER_FOUND" : "OBJECTIVE_FOUND"), enter ? QuestState.TRIGGER_FOUND : QuestState.OBJECTIVE_FOUND);
        this.feedback = AdvanceFeedback.from(config);
        this.mainThread = AdvanceFeedback.mainThread(plugin);

        Set<String> known = new HashSet<>(Set.of("type", "dungeon", "required_state", "advance_state", "description",
                org.fourz.RVNKQuests.reward.OnAdvanceRewards.CONFIG_KEY, "waypoint"));
        known.addAll(AdvanceFeedback.configKeys());
        for (String key : config.keySet()) {
            if (!known.contains(key)) {
                logger.warning("Quest '" + quest.getId() + "': DUNGEON_" + mode + " ignores unknown config key '" + key + "'");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnter(DungeonEnterEvent e) {
        if (mode == Mode.ENTER && matches(e.getDungeonId())) fire(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeave(DungeonLeaveEvent e) {
        if (mode == Mode.LEAVE && matches(e.getDungeonId())) fire(e.getPlayer());
    }

    private boolean matches(String id) {
        return dungeonId != null && dungeonId.equals(id);
    }

    private void fire(Player p) {
        if (quest.getStateForPlayer(p) != requiredState) return;
        feedback.notifyIfCommitted(p,
                org.fourz.RVNKQuests.quest.ComponentAdvance.tryAdvance(quest, this, p.getUniqueId(), advanceState,
                        org.fourz.RVNKQuests.party.PartyBeatContext.of(p.getLocation(), 0.0, requiredState)),
                mainThread);
        logger.debug("DUNGEON_" + mode + " fired for " + p.getName() + " (" + dungeonId + ", quest " + quest.getId()
                + ") -> " + advanceState);
    }

    public String getDungeonId() {
        return dungeonId;
    }

    public Mode getMode() {
        return mode;
    }

    private static QuestState parse(String s, QuestState fallback) {
        try {
            return QuestState.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
