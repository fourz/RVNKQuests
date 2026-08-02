package org.fourz.RVNKQuests.trigger.generic;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
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
 *   <li>{@code custom_name} — Display name to match (preferred; aliases {@code item_name})</li>
 *   <li>{@code item_name} — Display name to match (legacy alias for {@code custom_name})</li>
 *   <li>{@code world} — World name restriction (optional)</li>
 *   <li>{@code required_state} — State required for this trigger to fire (default: "NOT_STARTED")</li>
 *   <li>{@code advance_state} — State to advance to (default: "TRIGGER_FOUND")</li>
 * </ul>
 */
public class GenericItemDiscoveryTrigger implements Listener {

    private final DataDrivenQuest quest;
    private final LogManager logger;

    private final Material itemType;
    private final String itemName;
    private final String worldName;
    private final QuestState requiredState;
    private final QuestState advanceState;

    /** Explains a right-click on the right item at the wrong beat. */
    private final org.fourz.RVNKQuests.util.OutOfOrderFeedback feedback;

    public GenericItemDiscoveryTrigger(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config) {
        this.quest = quest;
        this.logger = LogManager.getInstance(plugin, "GenericItemDiscoveryTrigger");

        this.itemType = parseMaterial(QuestComponentFactory.getStringConfig(config, "item_type", "WRITTEN_BOOK"));
        String itemNameKey = QuestComponentFactory.getStringConfig(config, "custom_name",
                QuestComponentFactory.getStringConfig(config, "item_name", null));
        this.itemName = itemNameKey;
        this.worldName = QuestComponentFactory.getStringConfig(config, "world", null);
        this.requiredState = parseState(QuestComponentFactory.getStringConfig(config, "required_state", "NOT_STARTED"));
        this.advanceState = parseState(QuestComponentFactory.getStringConfig(config, "advance_state", "TRIGGER_FOUND"));
        this.feedback = org.fourz.RVNKQuests.util.OutOfOrderFeedback.from(config);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only right-click actions; ignore off-hand to avoid duplicate fires
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();

        // The state gate moved below the item match (#1904 follow-up). Identifying the item first
        // means a mismatch here is "the right book at the wrong beat" — worth telling the player,
        // rather than the silence that reads as a broken trigger.
        if (worldName != null && !player.getWorld().getName().equalsIgnoreCase(worldName)) return;

        // Paper 1.21+ may return null from event.getItem() for book interactions — fall back to main hand
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) {
            item = player.getInventory().getItemInMainHand();
        }
        if (item == null || item.getType() != itemType) return;

        // Check item name if configured — strip color codes for comparison
        if (itemName != null) {
            if (!item.hasItemMeta()) {
                logger.debug("Item discovery: " + player.getName() + " held item has no meta — skipping");
                return;
            }
            String resolved = org.fourz.RVNKQuests.util.ItemNameUtil.plainDisplayName(item);
            String displayName = resolved != null ? resolved : "";
            if (!displayName.contains(itemName)) {
                logger.debug("Item discovery: " + player.getName() + " name mismatch: '" + displayName + "' vs '" + itemName + "'");
                return;
            }
        }

        QuestState currentState = quest.getStateForPlayer(player);
        if (currentState != requiredState) {
            logger.debug("Item discovery: " + player.getName() + " state " + currentState + " != required " + requiredState + " — skipping");
            feedback.notifyWrongBeat(player, currentState, requiredState);
            return;
        }

        quest.advanceStateForPlayer(player.getUniqueId(), advanceState);
        logger.debug("Item discovery trigger fired for " + player.getName() + " on quest " + quest.getId() + " — advancing to " + advanceState);
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
