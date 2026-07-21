package org.fourz.RVNKQuests.trigger.lectern;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.integration.ILoreIntegration;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Map;

/**
 * Fires when a player right-clicks a lectern that has a specific book placed on it.
 *
 * <h3>Config keys:</h3>
 * <ul>
 *   <li>{@code world} — World name restriction (required)</li>
 *   <li>{@code book_name} — Case-sensitive display name to match (color codes stripped)</li>
 *   <li>{@code lore_book_id} — RVNKLore item name from PDC (preferred; requires RVNKLore)</li>
 *   <li>{@code cancel_interaction} — Whether to cancel the lectern open event (default: false)</li>
 *   <li>{@code required_state} — Player state that allows this trigger (default: NOT_STARTED)</li>
 *   <li>{@code advance_state} — State to advance to on match (default: TRIGGER_FOUND)</li>
 *   <li>{@code debug} — when true, log an author-facing diagnostic at WARNING for every candidate
 *       interaction, naming which gate stopped it (world/book/state). Default: false (#1499)</li>
 * </ul>
 * Exactly one of {@code book_name} or {@code lore_book_id} must be provided.
 */
public class LecternBookOnTrigger implements Listener {

    private final DataDrivenQuest quest;
    private final LogManager logger;

    private final String worldName;
    private final QuestState requiredState;
    private final QuestState advanceState;
    private final boolean cancelInteraction;
    private final String bookName;
    private final String loreBookId;
    private final boolean debug;
    private final RVNKQuests plugin;

    public LecternBookOnTrigger(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config) {
        this.quest = quest;
        this.logger = LogManager.getInstance(plugin, "LecternBookOnTrigger");

        String worldVal = QuestComponentFactory.getStringConfig(config, "world", null);
        if (worldVal == null || worldVal.isEmpty()) {
            throw new IllegalArgumentException("LecternBookOnTrigger requires 'world' config key");
        }
        this.worldName = worldVal;

        this.requiredState = parseState(QuestComponentFactory.getStringConfig(config, "required_state", "NOT_STARTED"));
        this.advanceState = parseState(QuestComponentFactory.getStringConfig(config, "advance_state", "TRIGGER_FOUND"));
        this.cancelInteraction = QuestComponentFactory.getBoolConfig(config, "cancel_interaction", false);

        String loreId = QuestComponentFactory.getStringConfig(config, "lore_book_id", null);
        String nameVal = QuestComponentFactory.getStringConfig(config, "book_name", null);
        if (loreId == null && nameVal == null) {
            throw new IllegalArgumentException("LecternBookOnTrigger requires 'lore_book_id' or 'book_name'");
        }

        this.loreBookId = loreId;
        this.bookName = nameVal;
        this.debug = QuestComponentFactory.getBoolConfig(config, "debug", false);
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.LECTERN) return;
        if (!(block.getState() instanceof Lectern lectern)) return;

        Player player = event.getPlayer();
        ItemStack bookOnLectern = lectern.getInventory().getItem(0);
        boolean hasBook = bookOnLectern != null && bookOnLectern.getType() != Material.AIR;
        boolean matched = hasBook && bookMatches(bookOnLectern);

        // Emit BEFORE the world/state guards so wrong-world and empty-lectern cases are visible (#1499).
        LecternDebug.emit(plugin, logger, debug, "BOOK_ON", quest.getId(), player,
                worldName, hasBook ? bookOnLectern : null, bookName, loreBookId, matched,
                quest.getStateForPlayer(player), requiredState);

        if (!player.getWorld().getName().equalsIgnoreCase(worldName)) return;
        if (quest.getStateForPlayer(player) != requiredState) return;
        if (!matched) return;

        if (cancelInteraction) event.setCancelled(true);

        quest.advanceStateForPlayer(player.getUniqueId(), advanceState);
        logger.debug("LecternBookOnTrigger fired for " + player.getName());
    }

    private boolean bookMatches(ItemStack item) {
        if (loreBookId != null) {
            ILoreIntegration lore = plugin.getLoreIntegration();
            if (lore == null) return false;
            return loreBookId.equals(lore.resolveItemId(item));
        }
        String name = org.fourz.RVNKQuests.util.ItemNameUtil.plainDisplayName(item);
        return name != null && bookName.equals(name);
    }

    private QuestState parseState(String name) {
        try {
            return QuestState.valueOf(name);
        } catch (IllegalArgumentException e) {
            return QuestState.TRIGGER_FOUND;
        }
    }
}
