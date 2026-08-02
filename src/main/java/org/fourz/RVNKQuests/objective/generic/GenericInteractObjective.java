package org.fourz.RVNKQuests.objective.generic;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic interact objective — interact with a block or item type N times.
 *
 * <h3>Config keys:</h3>
 * <ul>
 *   <li>{@code block_type} — Material name for block interaction</li>
 *   <li>{@code item_type} — Material name for item interaction</li>
 *   <li>{@code required_count} — Number of interactions required (default: 1)</li>
 *   <li>{@code world} — World restriction (optional)</li>
 *   <li>{@code required_state} — QuestState player must be in (default: "QUEST_ACTIVE")</li>
 *   <li>{@code advance_state} — State to advance to (default: "OBJECTIVE_FOUND")</li>
 *   <li>{@code requires_path} — Only active when player's pathChoice matches (optional)</li>
 *   <li>{@code sets_path} — Sets pathChoice on completion (optional)</li>
 * </ul>
 */
public class GenericInteractObjective implements Listener {

    private final DataDrivenQuest quest;
    private final LogManager logger;

    private final Material blockType;
    private final Material itemType;
    private final int requiredCount;
    private final String worldName;
    private final QuestState requiredState;
    private final QuestState advanceState;
    private final String requiresPath;
    private final String setsPath;

    private final Map<UUID, Integer> interactCounts = new ConcurrentHashMap<>();

    /** Explains an interaction with the right target at the wrong beat. */
    private final org.fourz.RVNKQuests.util.OutOfOrderFeedback feedback;

    public GenericInteractObjective(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config) {
        this.quest = quest;
        this.logger = LogManager.getInstance(plugin, "GenericInteractObjective");

        this.blockType = parseMaterial(QuestComponentFactory.getStringConfig(config, "block_type", null));
        this.itemType = parseMaterial(QuestComponentFactory.getStringConfig(config, "item_type", null));
        this.requiredCount = QuestComponentFactory.getIntConfig(config, "required_count", 1);
        this.worldName = QuestComponentFactory.getStringConfig(config, "world", null);
        this.requiredState = parseState(QuestComponentFactory.getStringConfig(config, "required_state", "QUEST_ACTIVE"));
        this.advanceState = parseState(QuestComponentFactory.getStringConfig(config, "advance_state", "OBJECTIVE_FOUND"));
        this.requiresPath = QuestComponentFactory.getStringConfig(config, "requires_path", null);
        this.setsPath = QuestComponentFactory.getStringConfig(config, "sets_path", null);
        this.feedback = org.fourz.RVNKQuests.util.OutOfOrderFeedback.from(config);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Path restriction stays ahead of the state gate: a player on the other branch of the quest
        // is not out of order, they are somewhere else entirely, and telling them otherwise would
        // leak the existence of the branch they did not take.
        // Check path restriction
        if (requiresPath != null) {
            String playerPath = quest.getPathChoiceCached(player);
            if (!requiresPath.equals(playerPath)) return;
        }

        if (worldName != null && !player.getWorld().getName().equalsIgnoreCase(worldName)) return;

        boolean matched = false;

        // Check block interaction
        if (blockType != null) {
            Block block = event.getClickedBlock();
            if (block != null && block.getType() == blockType) {
                matched = true;
            }
        }

        // Check item interaction
        if (!matched && itemType != null && event.getItem() != null) {
            if (event.getItem().getType() == itemType) {
                matched = true;
            }
        }

        if (!matched) return;

        QuestState currentState = quest.getStateForPlayer(player);
        if (currentState != requiredState) {
            feedback.notifyWrongBeat(player, currentState, requiredState);
            return;
        }

        UUID playerId = player.getUniqueId();
        int count = interactCounts.merge(playerId, 1, Integer::sum);

        if (count >= requiredCount) {
            interactCounts.remove(playerId);
            if (setsPath != null) {
                quest.setPathChoice(player, setsPath);
            }
            quest.advanceStateForPlayer(playerId, advanceState);
            logger.debug(player.getName() + " completed interact objective for quest " + quest.getId());
        }
    }

    private Material parseMaterial(String name) {
        if (name == null) return null;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private QuestState parseState(String name) {
        try {
            return QuestState.valueOf(name);
        } catch (IllegalArgumentException e) {
            return QuestState.QUEST_ACTIVE;
        }
    }
}
