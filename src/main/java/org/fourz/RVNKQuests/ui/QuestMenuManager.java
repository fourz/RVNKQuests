package org.fourz.RVNKQuests.ui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.dto.QuestProgressDTO;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Manager for GUI-based quest browsing and management.
 *
 * <p>Design Principles:</p>
 * <ul>
 *   <li>Colorful - color-coded items for quest states (green=completed, yellow=active, gray=available)</li>
 *   <li>Slim - don't overwhelm with too much info per page</li>
 *   <li>Feedback Loop - click sounds, visual confirmation</li>
 * </ul>
 *
 * <p>Menu Types:</p>
 * <ul>
 *   <li>Main Menu - Quest list with filters (active, available, completed)</li>
 *   <li>Detail View - Quest objectives and rewards</li>
 * </ul>
 */
public class QuestMenuManager implements InventoryHolder {

    private final RVNKQuests plugin;
    private final LogManager logger;
    private final Player viewer;
    private final MenuType menuType;
    private final QuestFilter filter;
    private Inventory inventory;

    // Filter types for quest list
    public enum QuestFilter {
        ALL("All Quests", Material.COMPASS),
        ACTIVE("Active Quests", Material.WRITABLE_BOOK),
        AVAILABLE("Available Quests", Material.MAP),
        COMPLETED("Completed Quests", Material.ENCHANTED_BOOK);

        private final String displayName;
        private final Material icon;

        QuestFilter(String displayName, Material icon) {
            this.displayName = displayName;
            this.icon = icon;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Material getIcon() {
            return icon;
        }
    }

    // Menu types
    public enum MenuType {
        MAIN_LIST,
        QUEST_DETAIL
    }

    /**
     * Creates a new quest menu for a player.
     *
     * @param plugin The plugin instance
     * @param viewer The player viewing the menu
     * @param filter The quest filter to apply
     */
    public QuestMenuManager(RVNKQuests plugin, Player viewer, QuestFilter filter) {
        this(plugin, viewer, MenuType.MAIN_LIST, filter);
    }

    /**
     * Creates a new quest menu with specific type.
     *
     * @param plugin The plugin instance
     * @param viewer The player viewing the menu
     * @param menuType The menu type
     * @param filter The quest filter to apply
     */
    public QuestMenuManager(RVNKQuests plugin, Player viewer, MenuType menuType, QuestFilter filter) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.viewer = viewer;
        this.menuType = menuType;
        this.filter = filter;
    }

    /**
     * Opens the quest menu for the player.
     */
    public void openMenu() {
        logger.debug("Opening quest menu for " + viewer.getName() + " (filter: " + filter + ")");

        // Create inventory asynchronously to avoid blocking main thread
        CompletableFuture.runAsync(() -> {
            createInventory();
        }).thenRunAsync(() -> {
            // Open on main thread
            viewer.openInventory(inventory);
            playSound(viewer, Sound.BLOCK_CHEST_OPEN);
        }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
    }

    /**
     * Creates the inventory with quest items.
     */
    private void createInventory() {
        // Create 54-slot inventory (6 rows)
        String title = ChatColor.DARK_PURPLE + "Quest Menu - " + ChatColor.GOLD + filter.getDisplayName();
        inventory = Bukkit.createInventory(this, 54, title);

        // Add filter buttons (bottom row)
        addFilterButtons();

        // Add quest items based on filter
        addQuestItems();

        // Add close button
        addCloseButton();
    }

    /**
     * Adds filter buttons to the inventory.
     */
    private void addFilterButtons() {
        int[] filterSlots = {45, 46, 47, 48}; // Bottom-left slots
        QuestFilter[] filters = QuestFilter.values();

        for (int i = 0; i < filters.length && i < filterSlots.length; i++) {
            QuestFilter currentFilter = filters[i];
            ItemStack filterItem = createFilterButton(currentFilter, currentFilter == filter);
            inventory.setItem(filterSlots[i], filterItem);
        }
    }

    /**
     * Creates a filter button item.
     */
    private ItemStack createFilterButton(QuestFilter targetFilter, boolean active) {
        ItemStack item = new ItemStack(targetFilter.getIcon());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String name = (active ? ChatColor.YELLOW + "► " : ChatColor.GRAY) + targetFilter.getDisplayName();
            meta.setDisplayName(name);

            List<String> lore = new ArrayList<>();
            if (active) {
                lore.add(ChatColor.DARK_GRAY + "Currently viewing");
            } else {
                lore.add(ChatColor.GRAY + "Click to filter");
            }
            meta.setLore(lore);

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Adds quest items to the inventory based on current filter.
     */
    private void addQuestItems() {
        UUID playerUuid = viewer.getUniqueId();
        List<Quest> quests = plugin.getQuestManager().getAllQuests();

        // Filter quests based on filter type
        List<CompletableFuture<QuestItemData>> questDataFutures = quests.stream()
            .map(quest -> createQuestItemData(quest, playerUuid))
            .collect(Collectors.toList());

        // Wait for all quest data to be collected
        CompletableFuture.allOf(questDataFutures.toArray(new CompletableFuture[0]))
            .thenAccept(v -> {
                List<QuestItemData> questData = questDataFutures.stream()
                    .map(CompletableFuture::join)
                    .filter(data -> shouldShowQuest(data, filter))
                    .collect(Collectors.toList());

                // Add quest items to inventory (rows 0-4, slots 0-44)
                int slot = 0;
                for (QuestItemData data : questData) {
                    if (slot >= 45) break; // Don't overflow into button row

                    ItemStack questItem = createQuestItem(data);
                    inventory.setItem(slot++, questItem);
                }

                // Fill empty slots with placeholder
                fillEmptySlots(slot);
            });
    }

    /**
     * Creates quest item data asynchronously.
     */
    private CompletableFuture<QuestItemData> createQuestItemData(Quest quest, UUID playerUuid) {
        return quest.getStateForPlayer(playerUuid)
            .thenApply(state -> new QuestItemData(quest, state));
    }

    /**
     * Determines if a quest should be shown based on filter.
     */
    private boolean shouldShowQuest(QuestItemData data, QuestFilter filter) {
        return switch (filter) {
            case ALL -> true;
            case ACTIVE -> data.state == QuestState.QUEST_ACTIVE || data.state == QuestState.OBJECTIVE_FOUND;
            case AVAILABLE -> data.state == QuestState.NOT_STARTED || data.state == QuestState.TRIGGER_FOUND;
            case COMPLETED -> data.state == QuestState.COMPLETED;
        };
    }

    /**
     * Creates a quest item for the inventory.
     */
    private ItemStack createQuestItem(QuestItemData data) {
        Material material = getQuestMaterial(data.state);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Color-coded name based on state
            ChatColor nameColor = getQuestColor(data.state);
            meta.setDisplayName(nameColor + data.quest.getName());

            // Lore with quest info
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Status: " + getStateDisplay(data.state));
            lore.add("");
            lore.add(ChatColor.YELLOW + "Click for details");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Gets material based on quest state.
     */
    private Material getQuestMaterial(QuestState state) {
        return switch (state) {
            case COMPLETED -> Material.LIME_CONCRETE; // Green
            case QUEST_ACTIVE, OBJECTIVE_FOUND -> Material.YELLOW_CONCRETE; // Yellow
            case NOT_STARTED, TRIGGER_FOUND -> Material.LIGHT_GRAY_CONCRETE; // Gray
            case ABANDONED -> Material.RED_CONCRETE; // Red
        };
    }

    /**
     * Gets color based on quest state.
     */
    private ChatColor getQuestColor(QuestState state) {
        return switch (state) {
            case COMPLETED -> ChatColor.GREEN;
            case QUEST_ACTIVE, OBJECTIVE_FOUND -> ChatColor.YELLOW;
            case NOT_STARTED, TRIGGER_FOUND -> ChatColor.WHITE;
            case ABANDONED -> ChatColor.RED;
        };
    }

    /**
     * Gets display text for quest state.
     */
    private String getStateDisplay(QuestState state) {
        return switch (state) {
            case COMPLETED -> ChatColor.GREEN + "Completed";
            case QUEST_ACTIVE -> ChatColor.YELLOW + "Active";
            case OBJECTIVE_FOUND -> ChatColor.YELLOW + "In Progress";
            case NOT_STARTED -> ChatColor.WHITE + "Available";
            case TRIGGER_FOUND -> ChatColor.AQUA + "Discovered";
            case ABANDONED -> ChatColor.RED + "Abandoned";
        };
    }

    /**
     * Fills empty slots with placeholder items.
     */
    private void fillEmptySlots(int startSlot) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
        }

        for (int i = startSlot; i < 45; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    /**
     * Adds close button to inventory.
     */
    private void addCloseButton() {
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta meta = closeItem.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Close");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Close this menu");
            meta.setLore(lore);
            closeItem.setItemMeta(meta);
        }

        inventory.setItem(49, closeItem); // Center of bottom row
    }

    /**
     * Handles click on a quest item.
     */
    public void handleQuestClick(Quest quest, Player player) {
        logger.debug("Player " + player.getName() + " clicked quest: " + quest.getId());

        // Get player's state for this quest
        quest.getStateForPlayer(player.getUniqueId()).thenAccept(state -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Open detail view
                QuestDetailMenu detailMenu = new QuestDetailMenu(plugin, player, quest, state);
                detailMenu.openMenu();
                playSound(player, Sound.UI_BUTTON_CLICK);
            });
        });
    }

    /**
     * Handles click on a filter button.
     */
    public void handleFilterClick(QuestFilter newFilter, Player player) {
        logger.debug("Player " + player.getName() + " changed filter to: " + newFilter);

        // Create new menu with different filter
        QuestMenuManager newMenu = new QuestMenuManager(plugin, player, newFilter);
        newMenu.openMenu();
        playSound(player, Sound.UI_BUTTON_CLICK);
    }

    /**
     * Plays a sound to the player.
     */
    private void playSound(Player player, Sound sound) {
        player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player getViewer() {
        return viewer;
    }

    public QuestFilter getFilter() {
        return filter;
    }

    /**
     * Data class for quest item creation.
     */
    private static class QuestItemData {
        final Quest quest;
        final QuestState state;

        QuestItemData(Quest quest, QuestState state) {
            this.quest = quest;
            this.state = state;
        }
    }
}
