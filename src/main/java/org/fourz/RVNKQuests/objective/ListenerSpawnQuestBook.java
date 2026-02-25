package org.fourz.RVNKQuests.objective;

import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.inventory.meta.BookMeta;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.util.log.LogManager;
import org.bukkit.Location;

// Listener for when a player interacts with the quest book in the prophecy lectern

public class ListenerSpawnQuestBook implements Listener {
    private final Quest quest;
    private final String requiredBookTitle;
    private final Location lecternLocation;
    private final LogManager logger;

    public ListenerSpawnQuestBook(RVNKQuests plugin, Quest quest, String requiredBookTitle, Location lecternLocation) {
        this.quest = quest;
        this.requiredBookTitle = requiredBookTitle;
        this.lecternLocation = lecternLocation;
        this.logger = LogManager.getInstance(plugin, getClass());
    }

    @EventHandler
    public void onPlayerInteractLectern(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.LECTERN) return;

        if (!block.getLocation().equals(lecternLocation)) return;

        Lectern lectern = (Lectern) block.getState();
        ItemStack book = lectern.getInventory().getItem(0);
        
        if (book == null || book.getType() != Material.WRITTEN_BOOK) return;

        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta != null && meta.getTitle().equals(requiredBookTitle)) {
            logger.debug("Player found quest pillar book: " + requiredBookTitle);
            quest.advanceStateForPlayer(event.getPlayer().getUniqueId(), QuestState.TRIGGER_FOUND);
            event.getPlayer().sendMessage("§5The ancient text speaks to you...");
        }
    }
}
