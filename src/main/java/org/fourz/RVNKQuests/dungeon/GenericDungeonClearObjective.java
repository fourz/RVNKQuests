package org.fourz.RVNKQuests.dungeon;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.util.AdvanceFeedback;
import org.fourz.rvnkcore.api.event.DungeonClearedEvent;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * DUNGEON_CLEAR objective (#2371): advances every online participant of a dungeon (everyone who
 * entered since its last reset) when the last roster mob dies.
 *
 * <h3>Config keys</h3>
 * <ul>
 *   <li>{@code dungeon} — the dungeon id (required)</li>
 *   <li>{@code required_state} — default {@code QUEST_ACTIVE}</li>
 *   <li>{@code advance_state} — default {@code COMPLETED}</li>
 *   <li>{@code description}, {@code advance_message}, {@code on_advance}, {@code waypoint}</li>
 * </ul>
 *
 * <p>Players who are offline at the clear are not advanced; they were participants but are not
 * there to see it. Built only when {@link DungeonApi#isPresent()} is true.</p>
 */
public class GenericDungeonClearObjective implements Listener {

    private final DataDrivenQuest quest;
    private final LogManager logger;
    private final String dungeonId;
    private final QuestState requiredState;
    private final QuestState advanceState;
    private final AdvanceFeedback feedback;
    private final Executor mainThread;

    public GenericDungeonClearObjective(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config) {
        this.quest = quest;
        this.logger = LogManager.getInstance(plugin, "GenericDungeonClearObjective");
        String raw = QuestComponentFactory.getStringConfig(config, "dungeon", null);
        this.dungeonId = raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
        if (dungeonId == null || dungeonId.isEmpty()) {
            logger.warning("Quest '" + quest.getId() + "': DUNGEON_CLEAR has no 'dungeon' - it will never complete");
        }
        this.requiredState = parse(QuestComponentFactory.getStringConfig(config, "required_state", "QUEST_ACTIVE"),
                QuestState.QUEST_ACTIVE);
        this.advanceState = parse(QuestComponentFactory.getStringConfig(config, "advance_state", "COMPLETED"),
                QuestState.COMPLETED);
        this.feedback = AdvanceFeedback.from(config);
        this.mainThread = AdvanceFeedback.mainThread(plugin);

        Set<String> known = new HashSet<>(Set.of("objective_type", "dungeon", "required_state", "advance_state",
                "description", org.fourz.RVNKQuests.reward.OnAdvanceRewards.CONFIG_KEY, "waypoint"));
        known.addAll(AdvanceFeedback.configKeys());
        for (String key : config.keySet()) {
            if (!known.contains(key)) {
                logger.warning("Quest '" + quest.getId() + "': DUNGEON_CLEAR ignores unknown config key '" + key + "'");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCleared(DungeonClearedEvent e) {
        if (dungeonId == null || !dungeonId.equals(e.getDungeonId())) return;
        for (UUID u : e.getParticipants()) {
            Player p = Bukkit.getPlayer(u);
            if (p == null || quest.getStateForPlayer(p) != requiredState) continue;
            feedback.notifyIfCommitted(p,
                    org.fourz.RVNKQuests.quest.ComponentAdvance.tryAdvance(quest, this, u, advanceState,
                            org.fourz.RVNKQuests.party.PartyBeatContext.of(p.getLocation(), 0.0, requiredState)),
                    mainThread);
            logger.debug("DUNGEON_CLEAR fired for " + p.getName() + " (" + dungeonId + ", quest " + quest.getId()
                    + ") -> " + advanceState);
        }
    }

    public String getDungeonId() {
        return dungeonId;
    }

    private static QuestState parse(String s, QuestState fallback) {
        try {
            return QuestState.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
