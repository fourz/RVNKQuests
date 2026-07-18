package org.fourz.RVNKQuests.trigger.lectern;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.integration.ILoreIntegration;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Map;

/**
 * Fires when a player takes a specific book off a lectern.
 *
 *
 * <h3>Config keys:</h3>
 * <ul>
 *   <li>{@code world} — World name restriction (required)</li>
 *   <li>{@code book_name} — Case-sensitive display name to match (color codes stripped)</li>
 *   <li>{@code lore_book_id} — RVNKLore item name from PDC (preferred; requires RVNKLore)</li>
 *   <li>{@code required_state} — Player state that allows this trigger (default: NOT_STARTED)</li>
 *   <li>{@code advance_state} — State to advance to on match (default: TRIGGER_FOUND)</li>
 * </ul>
 * Exactly one of {@code book_name} or {@code lore_book_id} must be provided.
 */
public class LecternBookRemovedTrigger implements Listener {

    private final DataDrivenQuest quest;
    private final LogManager logger;

    private final String worldName;
    private final QuestState requiredState;
    private final QuestState advanceState;
    private final String bookName;
    private final String loreBookId;
    private final RVNKQuests plugin;

    public LecternBookRemovedTrigger(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config) {
        this.quest = quest;
        this.logger = LogManager.getInstance(plugin, "LecternBookRemovedTrigger");

        String worldVal = QuestComponentFactory.getStringConfig(config, "world", null);
        if (worldVal == null || worldVal.isEmpty()) {
            throw new IllegalArgumentException("LecternBookRemovedTrigger requires 'world' config key");
        }
        this.worldName = worldVal;

        this.requiredState = parseState(QuestComponentFactory.getStringConfig(config, "required_state", "NOT_STARTED"));
        this.advanceState = parseState(QuestComponentFactory.getStringConfig(config, "advance_state", "TRIGGER_FOUND"));

        String loreId = QuestComponentFactory.getStringConfig(config, "lore_book_id", null);
        String nameVal = QuestComponentFactory.getStringConfig(config, "book_name", null);
        if (loreId == null && nameVal == null) {
            throw new IllegalArgumentException("LecternBookRemovedTrigger requires 'lore_book_id' or 'book_name'");
        }

        this.loreBookId = loreId;
        this.bookName = nameVal;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerTakeLecternBook(PlayerTakeLecternBookEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equalsIgnoreCase(worldName)) return;
        if (quest.getStateForPlayer(player) != requiredState) return;

        ItemStack book = event.getBook();
        if (book == null) return;
        if (!bookMatches(book)) return;

        quest.advanceStateForPlayer(player.getUniqueId(), advanceState);
        logger.debug("LecternBookRemovedTrigger fired for " + player.getName());
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
