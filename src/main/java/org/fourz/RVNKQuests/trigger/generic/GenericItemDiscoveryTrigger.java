package org.fourz.RVNKQuests.trigger.generic;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Map;

/**
 * Generic item discovery trigger — triggers when player picks up or interacts with a specific item.
 * Generalizes ListenerProphecyDiscovery.
 *
 * <h3>Config keys:</h3>
 * <ul>
 *   <li>{@code item_type} — Material name (e.g., "WRITTEN_BOOK")</li>
 *   <li>{@code item_name} — Display name to match (optional)</li>
 *   <li>{@code world} — World name restriction (optional)</li>
 *   <li>{@code advance_state} — State to advance to (default: "TRIGGER_FOUND")</li>
 * </ul>
 */
public class GenericItemDiscoveryTrigger implements Listener {

    private final DataDrivenQuest quest;
    private final LogManager logger;

    private final Material itemType;
    private final String itemName;
    private final String worldName;
    private final QuestState advanceState;

    public GenericItemDiscoveryTrigger(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config) {
        this.quest = quest;
        this.logger = LogManager.getInstance(plugin, "GenericItemDiscoveryTrigger");

        this.itemType = parseMaterial(QuestComponentFactory.getStringConfig(config, "item_type", "WRITTEN_BOOK"));
        this.itemName = QuestComponentFactory.getStringConfig(config, "item_name", null);
        this.worldName = QuestComponentFactory.getStringConfig(config, "world", null);
        this.advanceState = parseState(QuestComponentFactory.getStringConfig(config, "advance_state", "TRIGGER_FOUND"));
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (quest.getStateForPlayer(player) != QuestState.NOT_STARTED) return;

        if (worldName != null && !player.getWorld().getName().equalsIgnoreCase(worldName)) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != itemType) return;

        // Check item name if configured
        if (itemName != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String displayName = item.getItemMeta().getDisplayName();
            if (!displayName.contains(itemName)) return;
        } else if (itemName != null) {
            return; // Item name required but item has no display name
        }

        quest.advanceStateForPlayer(player.getUniqueId(), advanceState);
        logger.debug("Item discovery trigger fired for " + player.getName() + " with " + itemType);
    }

    private Material parseMaterial(String name) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.WRITTEN_BOOK;
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
