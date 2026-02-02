package org.fourz.RVNKQuests.ui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Event listener for quest menu interactions.
 *
 * <p>Handles:</p>
 * <ul>
 *   <li>Click events in quest menus</li>
 *   <li>Filter button clicks</li>
 *   <li>Quest item clicks (open detail view)</li>
 *   <li>Action button clicks (accept/abandon/claim)</li>
 *   <li>Navigation buttons (back/close)</li>
 * </ul>
 */
public class QuestMenuListener implements Listener {

    private final RVNKQuests plugin;
    private final LogManager logger;

    public QuestMenuListener(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Only handle player clicks
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();

        // Check if this is a quest menu
        if (holder instanceof QuestMenuManager menu) {
            handleMainMenuClick(event, player, menu);
        } else if (holder instanceof QuestDetailMenu detailMenu) {
            handleDetailMenuClick(event, player, detailMenu);
        }
    }

    /**
     * Handles clicks in the main quest menu.
     */
    private void handleMainMenuClick(InventoryClickEvent event, Player player, QuestMenuManager menu) {
        event.setCancelled(true); // Prevent item pickup

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        int slot = event.getSlot();
        logger.debug("Main menu click by " + player.getName() + " on slot " + slot);

        // Check for filter buttons (slots 45-48)
        if (slot >= 45 && slot <= 48) {
            handleFilterClick(slot, player, menu);
            return;
        }

        // Check for close button (slot 49)
        if (slot == 49 && clickedItem.getType() == Material.BARRIER) {
            player.closeInventory();
            playSound(player, Sound.BLOCK_CHEST_CLOSE);
            return;
        }

        // Check for quest items (slots 0-44)
        if (slot < 45) {
            handleQuestItemClick(clickedItem, player, menu);
        }
    }

    /**
     * Handles filter button clicks.
     */
    private void handleFilterClick(int slot, Player player, QuestMenuManager menu) {
        QuestMenuManager.QuestFilter newFilter = switch (slot) {
            case 45 -> QuestMenuManager.QuestFilter.ALL;
            case 46 -> QuestMenuManager.QuestFilter.ACTIVE;
            case 47 -> QuestMenuManager.QuestFilter.AVAILABLE;
            case 48 -> QuestMenuManager.QuestFilter.COMPLETED;
            default -> menu.getFilter(); // No change
        };

        // Only change if different filter
        if (newFilter != menu.getFilter()) {
            menu.handleFilterClick(newFilter, player);
        }
    }

    /**
     * Handles quest item clicks.
     */
    private void handleQuestItemClick(ItemStack item, Player player, QuestMenuManager menu) {
        // Extract quest name from item
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return;
        }

        String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        logger.debug("Looking for quest with name: " + displayName);

        // Find quest by name
        Quest quest = plugin.getQuestManager().getAllQuests().stream()
            .filter(q -> q.getName().equals(displayName))
            .findFirst()
            .orElse(null);

        if (quest != null) {
            menu.handleQuestClick(quest, player);
        } else {
            logger.warning("Quest not found for display name: " + displayName);
        }
    }

    /**
     * Handles clicks in the quest detail menu.
     */
    private void handleDetailMenuClick(InventoryClickEvent event, Player player, QuestDetailMenu menu) {
        event.setCancelled(true); // Prevent item pickup

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        int slot = event.getSlot();
        Material material = clickedItem.getType();
        logger.debug("Detail menu click by " + player.getName() + " on slot " + slot);

        // Check for back button (slot 49)
        if (slot == 49 && material == Material.ARROW) {
            menu.handleBack(player);
            return;
        }

        // Check for action buttons (slot 45)
        if (slot == 45) {
            handleActionButton(material, player, menu);
        }
    }

    /**
     * Handles action button clicks based on material type.
     */
    private void handleActionButton(Material material, Player player, QuestDetailMenu menu) {
        switch (material) {
            case LIME_CONCRETE -> {
                // Accept quest
                menu.handleAccept(player);
            }
            case RED_CONCRETE -> {
                // Abandon quest
                menu.handleAbandon(player);
            }
            case GOLD_BLOCK -> {
                // Claim rewards
                menu.handleClaimRewards(player);
            }
            case ORANGE_CONCRETE -> {
                // Restart quest
                menu.handleAccept(player);
            }
            case GRAY_CONCRETE -> {
                // Already completed - no action
                player.sendMessage(ChatColor.GRAY + "This quest is already completed");
                playSound(player, Sound.ENTITY_VILLAGER_NO);
            }
        }
    }

    /**
     * Plays a sound to the player.
     */
    private void playSound(Player player, Sound sound) {
        player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
    }
}
