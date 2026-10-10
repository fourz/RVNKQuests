package org.fourz.RVNKQuests.trigger.generic;

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
 * NPC_INTERACT trigger: clicking the NPC that carries an RVNK key offers (starts) the quest (#2214).
 *
 * <p>Listens to RVNKCore's {@link RvnkNpcInteractEvent}, so RVNKQuests never touches Citizens. The
 * quest references the NPC by its RVNK key, never by a Citizens numeric id.</p>
 *
 * <h3>Config keys</h3>
 * <ul>
 *   <li>{@code npc_key} — the RVNK NPC key (required, {@code [a-z0-9_-]{1,48}}, any case)</li>
 *   <li>{@code click} — {@code right} (default), {@code left} or {@code any}</li>
 *   <li>{@code required_state} — state the player must be in (default {@code NOT_STARTED}; the
 *       state_mapping bucket overrides it, #1764)</li>
 *   <li>{@code advance_state} — state to advance to (default {@code TRIGGER_FOUND})</li>
 *   <li>{@code description} — text for {@code %rvnkquests_active_objective%} (optional)</li>
 * </ul>
 *
 * <h3>Eligibility</h3>
 * <p>The same as every other trigger: the state gate here, then {@code advanceStateForPlayer},
 * which applies the monotonic guard and the prerequisite gate on NOT_STARTED to TRIGGER_FOUND. A
 * repeatable quest in its cooldown stays COMPLETED, so the NOT_STARTED gate refuses it.</p>
 *
 * <h3>Dialogue</h3>
 * <p>This class sends no chat. It reports its advance to {@link NpcInteractionCoordinator},
 * which sends at most one lore line per click across all quests.</p>
 *
 * <h3>Invalid key</h3>
 * <p>A missing or malformed {@code npc_key} logs a warning naming the quest and leaves the
 * component inert. The quest and its other components still load.</p>
 */
public class GenericNpcInteractTrigger implements Listener, NpcQuestComponent {

    public static final String TYPE_NAME = "NPC_INTERACT";

    private static final Set<String> KNOWN_KEYS = Set.of(
        "type", "npc_key", "click", "required_state", "advance_state", "description",
        org.fourz.RVNKQuests.reward.OnAdvanceRewards.CONFIG_KEY);

    private final RVNKQuests plugin;
    private final DataDrivenQuest quest;
    private final LogManager logger;

    private final String npcKey;
    private final String rawKey;
    private final NpcClickFilter click;
    private final QuestState requiredState;
    private final QuestState advanceState;

    public GenericNpcInteractTrigger(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config) {
        this.plugin = plugin;
        this.quest = quest;
        this.logger = LogManager.getInstance(plugin, "GenericNpcInteractTrigger");

        this.rawKey = QuestComponentFactory.getStringConfig(config, "npc_key", null);
        if (NpcKeyRules.isValid(rawKey)) {
            this.npcKey = NpcKeyRules.normalize(rawKey);
        } else {
            this.npcKey = null;
            logger.warning("Quest '" + questId() + "': " + TYPE_NAME + " npc_key "
                + (rawKey == null ? "is missing" : "'" + rawKey + "' is invalid")
                + " (needs " + NpcKeyRules.KEY_FORMAT + ") - this trigger will never fire");
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
            QuestComponentFactory.getStringConfig(config, "required_state", "NOT_STARTED"), QuestState.NOT_STARTED);
        this.advanceState = parseState(
            QuestComponentFactory.getStringConfig(config, "advance_state", "TRIGGER_FOUND"), QuestState.TRIGGER_FOUND);

        for (String key : config.keySet()) {
            if (!KNOWN_KEYS.contains(key)) {
                logger.warning("Quest '" + questId() + "': " + TYPE_NAME + " ignores unknown config key '"
                    + key + "' - it will have no effect");
            }
        }

        logger.debug(TYPE_NAME + " for '" + questId() + "': npc_key=" + npcKey + " click=" + click
            + " " + requiredState + " -> " + advanceState);
    }

    @EventHandler(ignoreCancelled = true)
    public void onNpcInteract(RvnkNpcInteractEvent event) {
        if (!matches(event)) return;

        Player player = event.getPlayer();
        if (player == null) return;

        QuestState current = quest.getStateForPlayer(player);
        if (current != requiredState) {
            logger.debug("NPC_INTERACT gate: " + player.getName() + " quest=" + questId() + " npc=" + npcKey
                + " current=" + current + " required=" + requiredState + " - not fired");
            return;
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
        logger.debug("NPC_INTERACT fired for " + player.getName() + " on npc '" + npcKey
            + "' (quest: " + questId() + ") -> " + advanceState);
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
        return true;
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    public boolean acceptsClick(RvnkNpcInteractEvent.ClickType clickType) {
        return click.accepts(clickType);
    }

    /** The configured key as written, for validation messages. */
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
