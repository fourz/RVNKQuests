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
import org.fourz.RVNKQuests.data.dto.QuestObjectiveProgressDTO;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Detail view for a single quest showing objectives and rewards.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Quest objectives with progress tracking</li>
 *   <li>Reward preview</li>
 *   <li>Accept/Abandon buttons</li>
 *   <li>Back button to return to main menu</li>
 * </ul>
 */
public class QuestDetailMenu implements InventoryHolder {

    private final RVNKQuests plugin;
    private final LogManager logger;
    private final Player viewer;
    private final Quest quest;
    private final QuestState currentState;
    private Inventory inventory;

    public QuestDetailMenu(RVNKQuests plugin, Player viewer, Quest quest, QuestState currentState) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.viewer = viewer;
        this.quest = quest;
        this.currentState = currentState;
    }

    /**
     * Opens the quest detail menu.
     */
    public void openMenu() {
        logger.debug("Opening quest detail for " + viewer.getName() + ": " + quest.getId());

        // Create inventory
        String title = ChatColor.DARK_PURPLE + quest.getName();
        inventory = Bukkit.createInventory(this, 54, title);

        // Add quest info
        addQuestInfo();

        // Add objectives
        addObjectives();

        // Add action buttons
        addActionButtons();

        // Add back button
        addBackButton();

        // Open inventory
        viewer.openInventory(inventory);
        playSound(viewer, Sound.ITEM_BOOK_PAGE_TURN);
    }

    /**
     * Adds quest information to the inventory.
     */
    private void addQuestInfo() {
        // Quest info item (top-left)
        ItemStack infoItem = new ItemStack(Material.FILLED_MAP);
        ItemMeta meta = infoItem.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + quest.getName());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Quest ID: " + ChatColor.WHITE + quest.getId());
            lore.add("");
            lore.add(ChatColor.GRAY + "Status: " + getStateDisplay(currentState));

            if (quest.getStartTrigger() != null) {
                lore.add("");
                lore.add(ChatColor.YELLOW + "Start: " + ChatColor.WHITE + quest.getStartTrigger());
            }

            meta.setLore(lore);
            infoItem.setItemMeta(meta);
        }

        inventory.setItem(4, infoItem); // Top center
    }

    /**
     * Adds objective items to the inventory.
     */
    private void addObjectives() {
        // Get objectives for this quest
        plugin.getQuestProgressService().getAllObjectives(viewer.getUniqueId(), quest.getId())
            .thenAccept(objectives -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (objectives.isEmpty()) {
                        // No objectives tracked yet
                        addPlaceholderObjective();
                    } else {
                        // Add objective items
                        int slot = 19; // Start in second row
                        for (QuestObjectiveProgressDTO objective : objectives) {
                            if (slot >= 35) break; // Max 2 rows of objectives

                            ItemStack objectiveItem = createObjectiveItem(objective);
                            inventory.setItem(slot++, objectiveItem);
                        }
                    }
                });
            })
            .exceptionally(ex -> {
                logger.error("Failed to load objectives for quest " + quest.getId(), ex);
                return null;
            });
    }

    /**
     * Creates an objective item.
     */
    private ItemStack createObjectiveItem(QuestObjectiveProgressDTO objective) {
        Material material = objective.completed() ? Material.LIME_CONCRETE : Material.YELLOW_CONCRETE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            ChatColor nameColor = objective.completed() ? ChatColor.GREEN : ChatColor.YELLOW;
            meta.setDisplayName(nameColor + "Objective: " + objective.objectiveId());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Progress: " + ChatColor.WHITE +
                    objective.progressCount() + "/" + objective.targetCount());

            if (objective.completed()) {
                lore.add(ChatColor.GREEN + "✓ Completed");
            } else {
                int remaining = objective.targetCount() - objective.progressCount();
                lore.add(ChatColor.YELLOW + String.valueOf(remaining) + " remaining");
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Adds placeholder when no objectives are tracked.
     */
    private void addPlaceholderObjective() {
        ItemStack placeholder = new ItemStack(Material.LIGHT_GRAY_CONCRETE);
        ItemMeta meta = placeholder.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GRAY + "No Objectives");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.DARK_GRAY + "Start the quest to see objectives");
            meta.setLore(lore);
            placeholder.setItemMeta(meta);
        }

        inventory.setItem(22, placeholder); // Center
    }

    /**
     * Adds action buttons based on quest state.
     */
    private void addActionButtons() {
        int buttonSlot = 45; // Bottom-left area

        switch (currentState) {
            case NOT_STARTED, TRIGGER_FOUND -> {
                // Accept button
                ItemStack acceptButton = createButton(Material.LIME_CONCRETE,
                        ChatColor.GREEN + "Accept Quest",
                        ChatColor.GRAY + "Click to start this quest");
                inventory.setItem(buttonSlot, acceptButton);
            }
            case QUEST_ACTIVE, OBJECTIVE_FOUND -> {
                // Abandon button
                ItemStack abandonButton = createButton(Material.RED_CONCRETE,
                        ChatColor.RED + "Abandon Quest",
                        ChatColor.GRAY + "Click to abandon this quest");
                inventory.setItem(buttonSlot, abandonButton);
            }
            case COMPLETED -> {
                // Already completed
                ItemStack completedInfo = createButton(Material.GRAY_CONCRETE,
                        ChatColor.GRAY + "Quest Completed",
                        ChatColor.DARK_GRAY + "You have already completed this quest");
                inventory.setItem(buttonSlot, completedInfo);
            }
            case ABANDONED -> {
                // Can restart
                ItemStack restartButton = createButton(Material.ORANGE_CONCRETE,
                        ChatColor.GOLD + "Restart Quest",
                        ChatColor.GRAY + "Click to restart this quest");
                inventory.setItem(buttonSlot, restartButton);
            }
        }
    }

    /**
     * Creates a button item.
     */
    private ItemStack createButton(Material material, String name, String lore) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            List<String> loreList = new ArrayList<>();
            loreList.add(lore);
            meta.setLore(loreList);
            button.setItemMeta(meta);
        }

        return button;
    }

    /**
     * Adds back button to return to main menu.
     */
    private void addBackButton() {
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta meta = backItem.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "← Back to Quest List");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Return to main menu");
            meta.setLore(lore);
            backItem.setItemMeta(meta);
        }

        inventory.setItem(49, backItem); // Center of bottom row
    }

    /**
     * Handles accept quest action.
     */
    public void handleAccept(Player player) {
        logger.debug("Player " + player.getName() + " accepting quest: " + quest.getId());

        // Start the quest
        quest.advanceStateForPlayer(player.getUniqueId(), QuestState.QUEST_ACTIVE)
            .thenRun(() -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.GREEN + "Quest accepted: " + ChatColor.GOLD + quest.getName());
                    playSound(player, Sound.ENTITY_PLAYER_LEVELUP);
                    player.closeInventory();
                });
            })
            .exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.RED + "Failed to accept quest: " + ex.getMessage());
                    logger.error("Failed to start quest " + quest.getId() + " for " + player.getName(), ex);
                });
                return null;
            });
    }

    /**
     * Handles abandon quest action.
     */
    public void handleAbandon(Player player) {
        logger.debug("Player " + player.getName() + " abandoning quest: " + quest.getId());

        // Reset quest progress
        plugin.getQuestProgressService().resetQuestProgress(player.getUniqueId(), quest.getId())
            .thenAccept(success -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (success) {
                        player.sendMessage(ChatColor.YELLOW + "Quest abandoned: " + ChatColor.GOLD + quest.getName());
                        playSound(player, Sound.ENTITY_ITEM_BREAK);
                    } else {
                        player.sendMessage(ChatColor.RED + "Failed to abandon quest");
                    }
                    player.closeInventory();
                });
            })
            .exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.RED + "Failed to abandon quest: " + ex.getMessage());
                    logger.error("Failed to abandon quest " + quest.getId() + " for " + player.getName(), ex);
                });
                return null;
            });
    }

    /**
     * Handles claim rewards action (completes quest).
     */
    public void handleClaimRewards(Player player) {
        logger.debug("Player " + player.getName() + " claiming rewards for quest: " + quest.getId());

        // Advance to completed state (triggers reward delivery)
        quest.advanceStateForPlayer(player.getUniqueId(), QuestState.COMPLETED)
            .thenRun(() -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.GOLD + "Rewards claimed for: " + ChatColor.GREEN + quest.getName());
                    playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE);
                    player.closeInventory();
                });
            })
            .exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.RED + "Failed to claim rewards: " + ex.getMessage());
                    logger.error("Failed to claim rewards for quest " + quest.getId(), ex);
                });
                return null;
            });
    }

    /**
     * Handles back button click.
     */
    public void handleBack(Player player) {
        // Return to main quest menu
        QuestMenuManager mainMenu = new QuestMenuManager(plugin, player, QuestMenuManager.QuestFilter.ALL);
        mainMenu.openMenu();
        playSound(player, Sound.UI_BUTTON_CLICK);
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
            case PAUSED -> ChatColor.AQUA + "Paused";
        };
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

    public Quest getQuest() {
        return quest;
    }

    public QuestState getCurrentState() {
        return currentState;
    }
}
