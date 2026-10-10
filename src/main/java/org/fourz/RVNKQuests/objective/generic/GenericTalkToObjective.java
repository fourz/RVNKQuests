package org.fourz.RVNKQuests.objective.generic;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.npc.NpcClickFilter;
import org.fourz.RVNKQuests.npc.NpcInteractionCoordinator;
import org.fourz.RVNKQuests.npc.NpcKeyRules;
import org.fourz.RVNKQuests.npc.NpcQuestComponent;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * TALK_TO objective: click the NPC with this RVNK key while the objective is active (#2214).
 *
 * <p>Completes the way every generic objective completes: it advances the quest state with
 * {@code advanceStateForPlayer}. Rewards, the completion notice and {@code QuestCompleteEvent}
 * fire inside {@code AbstractQuest} when that advance reaches COMPLETED, never from here.</p>
 *
 * <p>This is the #2214 subset of #1018: one click, one advance. Dialogue trees, choices and
 * {@code /quest reply} stay in #1018.</p>
 *
 * <h3>Config keys</h3>
 * <ul>
 *   <li>{@code npc_key} — the RVNK NPC key (required, {@code [a-z0-9_-]{1,48}}, any case)</li>
 *   <li>{@code click} — {@code right} (default), {@code left} or {@code any}</li>
 *   <li>{@code required_state} — default {@code QUEST_ACTIVE}; the state_mapping bucket overrides it</li>
 *   <li>{@code advance_state} — default {@code OBJECTIVE_FOUND}; use {@code COMPLETED} to finish the quest</li>
 *   <li>{@code requires_path} / {@code sets_path} — branching, as on INTERACT (optional)</li>
 *   <li>{@code description} — text for {@code %rvnkquests_active_objective%} (optional; default
 *       {@code Talk to <npc_key>})</li>
 * </ul>
 */
public class GenericTalkToObjective implements Listener, NpcQuestComponent {

    public static final String TYPE_NAME = "TALK_TO";

    private static final Set<String> KNOWN_KEYS = Set.of(
        "objective_type", "npc_key", "click", "required_state", "advance_state",
        "requires_path", "sets_path", "description",
        org.fourz.RVNKQuests.reward.OnAdvanceRewards.CONFIG_KEY);

    private final RVNKQuests plugin;
    private final DataDrivenQuest quest;
    private final LogManager logger;

    private final String npcKey;
    private final String rawKey;
    private final NpcClickFilter click;
    private final QuestState requiredState;
    private final QuestState advanceState;
    private final String requiresPath;
    private final String setsPath;

    public GenericTalkToObjective(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config) {
        this.plugin = plugin;
        this.quest = quest;
        this.logger = LogManager.getInstance(plugin, "GenericTalkToObjective");

        this.rawKey = QuestComponentFactory.getStringConfig(config, "npc_key", null);
        if (NpcKeyRules.isValid(rawKey)) {
            this.npcKey = NpcKeyRules.normalize(rawKey);
        } else {
            this.npcKey = null;
            logger.warning("Quest '" + questId() + "': " + TYPE_NAME + " npc_key "
                + (rawKey == null ? "is missing" : "'" + rawKey + "' is invalid")
                + " (needs " + NpcKeyRules.KEY_FORMAT + ") - this objective can never complete");
        }

        String clickRaw = QuestComponentFactory.getStringConfig(config, "click", null);
        NpcClickFilter parsed = NpcClickFilter.parse(clickRaw);
        if (parsed == null) {
            logger.warning("Quest '" + questId() + "': " + TYPE_NAME + " click '" + clickRaw
                + "' is not any/right/left - using right");
            parsed = NpcClickFilter.RIGHT;
        }
        this.click = parsed;

        this.requiredState = parseState(
            QuestComponentFactory.getStringConfig(config, "required_state", "QUEST_ACTIVE"), QuestState.QUEST_ACTIVE);
        this.advanceState = parseState(
            QuestComponentFactory.getStringConfig(config, "advance_state", "OBJECTIVE_FOUND"), QuestState.OBJECTIVE_FOUND);
        this.requiresPath = QuestComponentFactory.getStringConfig(config, "requires_path", null);
        this.setsPath = QuestComponentFactory.getStringConfig(config, "sets_path", null);

        for (String key : config.keySet()) {
            if (!KNOWN_KEYS.contains(key)) {
                logger.warning("Quest '" + questId() + "': " + TYPE_NAME + " ignores unknown config key '"
                    + key + "' - it will have no effect");
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onNpcInteract(RvnkNpcInteractEvent event) {
        if (!matches(event)) return;

        Player player = event.getPlayer();
        if (player == null) return;

        // Path restriction ahead of the state gate, as on INTERACT: a player on the other branch
        // is not out of order, they are elsewhere.
        if (requiresPath != null && !requiresPath.equals(quest.getPathChoiceCached(player))) return;

        QuestState current = quest.getStateForPlayer(player);
        if (current != requiredState) {
            logger.debug("TALK_TO gate: " + player.getName() + " quest=" + questId() + " npc=" + npcKey
                + " current=" + current + " required=" + requiredState + " - not completed");
            return;
        }

        if (setsPath != null) {
            quest.setPathChoice(player, setsPath);
        }
        Location checkpoint = event.getLocation() != null ? event.getLocation() : player.getLocation();
        CompletableFuture<Void> advance = org.fourz.RVNKQuests.quest.ComponentAdvance.advance(quest, this, player.getUniqueId(), advanceState,
            checkpoint != null
                ? org.fourz.RVNKQuests.party.PartyBeatContext.of(checkpoint, 0.0, requiredState)
                : null);

        NpcInteractionCoordinator coordinator = plugin.getNpcCoordinator();
        if (coordinator != null) {
            coordinator.recordAdvance(event, this, advanceState, advance);
        }
        logger.debug(player.getName() + " completed TALK_TO '" + npcKey + "' for quest " + questId()
            + " -> " + advanceState);
    }

    // ==================== NpcQuestComponent ====================

    @Override
    public DataDrivenQuest getQuest() {
        return quest;
    }

    @Override
    public String getNpcKey() {
        return npcKey;
    }

    @Override
    public boolean isTrigger() {
        return false;
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    public boolean acceptsClick(RvnkNpcInteractEvent.ClickType clickType) {
        return click.accepts(clickType);
    }

    public String getRawKey() {
        return rawKey;
    }

    public QuestState getRequiredState() {
        return requiredState;
    }

    public QuestState getAdvanceState() {
        return advanceState;
    }

    private String questId() {
        return quest != null ? quest.getId() : "?";
    }

    private QuestState parseState(String name, QuestState fallback) {
        try {
            return QuestState.valueOf(name);
        } catch (IllegalArgumentException e) {
            logger.warning("Quest '" + questId() + "': unknown quest state '" + name
                + "' - falling back to " + fallback);
            return fallback;
        }
    }
}
