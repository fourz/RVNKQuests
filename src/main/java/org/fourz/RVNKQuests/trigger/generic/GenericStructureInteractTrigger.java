package org.fourz.RVNKQuests.trigger.generic;

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

/**
 * Generic structure interaction trigger — triggers when player interacts with a block type.
 * Generalizes ListenerQuestPillarStart.
 *
 * <h3>Config keys:</h3>
 * <ul>
 *   <li>{@code block_type} — Material name (e.g., "LECTERN")</li>
 *   <li>{@code world} — World name restriction (optional)</li>
 *   <li>{@code advance_state} — State to advance to (default: "TRIGGER_FOUND")</li>
 * </ul>
 */
public class GenericStructureInteractTrigger implements Listener {

    private final RVNKQuests plugin;
    private final DataDrivenQuest quest;
    private final LogManager logger;

    private final Material blockType;
    private final String worldName;
    private final QuestState advanceState;

    public GenericStructureInteractTrigger(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config) {
        this.plugin = plugin;
        this.quest = quest;
        this.logger = LogManager.getInstance(plugin, "GenericStructureInteractTrigger");

        this.blockType = parseMaterial(QuestComponentFactory.getStringConfig(config, "block_type", "LECTERN"));
        this.worldName = QuestComponentFactory.getStringConfig(config, "world", null);
        this.advanceState = parseState(QuestComponentFactory.getStringConfig(config, "advance_state", "TRIGGER_FOUND"));
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();

        if (quest.getStateForPlayer(player) != QuestState.NOT_STARTED) return;

        if (worldName != null && !player.getWorld().getName().equalsIgnoreCase(worldName)) return;

        if (block.getType() != blockType) return;

        quest.advanceStateForPlayer(player.getUniqueId(), advanceState);
        logger.debug("Structure interact trigger fired for " + player.getName() + " on " + blockType);
    }

    private Material parseMaterial(String name) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown material: " + name + ", defaulting to LECTERN");
            return Material.LECTERN;
        }
    }

    private QuestState parseState(String name) {
        try {
            return QuestState.valueOf(name);
        } catch (IllegalArgumentException e) {
            return QuestState.TRIGGER_FOUND;
        }
    }
}
