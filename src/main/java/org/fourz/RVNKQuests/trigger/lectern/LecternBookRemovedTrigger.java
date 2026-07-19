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

        ItemStack book = event.getBook();
        if (book == null) return;

        boolean matched = bookMatches(book);
        logBookSeen(player, book, matched);

        if (quest.getStateForPlayer(player) != requiredState) return;
        if (!matched) return;

        quest.advanceStateForPlayer(player.getUniqueId(), advanceState);
        logger.debug("LecternBookRemovedTrigger fired for " + player.getName());
    }

    /**
     * Logs every book taken off a lectern in this trigger's world, matched or not (#1499).
     *
     * <p>Previously the only output was a DEBUG line on a successful match, so an author whose
     * book did not match had nothing to go on. The most common cause is confusing the signed
     * book title with the anvil display name — {@code book_name} matches the latter. Emitting
     * the name actually seen lets an author copy it straight into the trigger config.
     *
     * <p>Runs before the required-state check on purpose: a book taken while the player is in
     * the wrong state is exactly the case an author needs to see.
     */
    private void logBookSeen(Player player, ItemStack book, boolean matched) {
        String seenName = org.fourz.RVNKQuests.util.ItemNameUtil.plainDisplayName(book);
        ILoreIntegration lore = plugin.getLoreIntegration();
        String seenLoreId = (lore != null) ? lore.resolveItemId(book) : null;
        String expected = (loreBookId != null)
                ? "lore_book_id=" + loreBookId
                : "book_name=" + bookName;

        logger.info("[lectern] quest=" + quest.getId()
                + " player=" + player.getName()
                + " world=" + player.getWorld().getName()
                + " seen_name=" + seenName
                + " seen_lore_id=" + seenLoreId
                + " expected=" + expected
                + " state=" + quest.getStateForPlayer(player)
                + " matched=" + matched);
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
