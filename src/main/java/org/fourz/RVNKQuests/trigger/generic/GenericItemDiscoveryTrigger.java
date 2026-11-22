package org.fourz.RVNKQuests.trigger.generic;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Map;

/**
 * Generic item discovery trigger — triggers when the player picks up, takes or holds a specific item.
 * Generalizes ListenerProphecyDiscovery.
 *
 * <p>Fires on any of (#2461): a right-click with the item in the main hand, picking the item up off
 * the ground, closing any inventory (a chest take) while the item is in the player's inventory, or
 * switching the hotbar onto the item. Only the right-click path tells the player about a wrong
 * beat; the passive paths stay silent.</p>
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

    /** Optional per-beat line + cue sent when this component advances the quest (#2025). */
    private final org.fourz.RVNKQuests.util.AdvanceFeedback advanceFeedback;

    /** Runs the advance line on the server thread once the advance commits (#2249). */
    private final java.util.concurrent.Executor mainThread;

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
        this.advanceFeedback = org.fourz.RVNKQuests.util.AdvanceFeedback.from(config);
        this.mainThread = org.fourz.RVNKQuests.util.AdvanceFeedback.mainThread(plugin);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only right-click actions; ignore off-hand to avoid duplicate fires
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();

        // Paper 1.21+ may return null from event.getItem() for book interactions — fall back to main hand
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) {
            item = player.getInventory().getItemInMainHand();
        }
        // The state gate sits below the item match (#1904 follow-up). Identifying the item first
        // means a mismatch here is "the right book at the wrong beat" — worth telling the player,
        // rather than the silence that reads as a broken trigger.
        discover(player, item, true, "right-click");
    }

    /**
     * Picking the item up off the ground counts as finding it (#2461). The stack is not in the
     * inventory yet when this event fires, so match the picked-up stack itself.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        discover(player, event.getItem().getItemStack(), false, "pickup");
    }

    /**
     * Taking the item out of a chest (or any container) counts as finding it (#2461). On close,
     * scan the player's own inventory once — one scan per close, not per tick, and it also
     * catches shift-clicks, drags and hotbar swaps without tracking each click type.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        discover(player, findMatch(player.getInventory().getContents(), itemType, itemName), false, "inventory close");
    }

    /** Switching the hotbar onto the item counts as holding it (#2461). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        discover(player, player.getInventory().getItem(event.getNewSlot()), false, "held");
    }

    /**
     * Advance the player when {@code item} is this trigger's item, in the right world, at the
     * right beat.
     *
     * @param explicit true for a right-click: a wrong beat then tells the player. The passive
     *                 paths (pickup, close, held) stay silent at a wrong beat, so carrying the
     *                 item around never spams the out-of-order line.
     */
    private void discover(Player player, ItemStack item, boolean explicit, String via) {
        if (item == null) return;
        if (worldName != null && !player.getWorld().getName().equalsIgnoreCase(worldName)) return;
        if (!matches(item, itemType, itemName)) {
            if (explicit && item.getType() == itemType && itemName != null) {
                logger.debug("Item discovery: " + player.getName() + " name mismatch: '"
                    + org.fourz.RVNKQuests.util.ItemNameUtil.plainDisplayName(item) + "' vs '" + itemName + "'");
            }
            return;
        }

        QuestState currentState = quest.getStateForPlayer(player);
        if (currentState != requiredState) {
            if (!explicit) return;
            // #1930: name what the feedback decided, not just that we skipped — the message is
            // player-only and otherwise unobservable from the server.
            var outcome = feedback.notifyWrongBeat(player, currentState, requiredState);
            logger.debug("Out-of-order item discovery: " + player.getName() + " quest=" + quest.getId()
                + " current=" + currentState + " required=" + requiredState + " -> " + outcome);
            return;
        }

        // Party fan-out (#1986): a discovery has no place of its own — it happens wherever the
        // holder is standing. Radius 0 is deliberate and legal: the service applies its
        // min_share_radius floor, so this shares on the same footing as a kill.
        // Notify only after the advance commits (#2249).
        advanceFeedback.notifyIfCommitted(player,
                org.fourz.RVNKQuests.quest.ComponentAdvance.tryAdvance(quest, this, player.getUniqueId(), advanceState,
            org.fourz.RVNKQuests.party.PartyBeatContext.of(
                player.getLocation(), 0.0, requiredState)),
                mainThread);
        logger.debug("Item discovery trigger fired (" + via + ") for " + player.getName() + " on quest "
            + quest.getId() + " - advancing to " + advanceState);
    }

    /**
     * @return true when {@code item} has the configured material and, if a name is configured,
     *         a plain display name that contains it (colour and Paper JSON quotes stripped)
     */
    static boolean matches(ItemStack item, Material itemType, String itemName) {
        if (item == null || item.getType() != itemType) return false;
        if (itemName == null) return true;
        if (!item.hasItemMeta()) return false;
        String resolved = org.fourz.RVNKQuests.util.ItemNameUtil.plainDisplayName(item);
        return resolved != null && resolved.contains(itemName);
    }

    /** @return the first stack in {@code contents} that {@link #matches}, or null */
    static ItemStack findMatch(ItemStack[] contents, Material itemType, String itemName) {
        if (contents == null) return null;
        for (ItemStack stack : contents) {
            if (matches(stack, itemType, itemName)) return stack;
        }
        return null;
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
