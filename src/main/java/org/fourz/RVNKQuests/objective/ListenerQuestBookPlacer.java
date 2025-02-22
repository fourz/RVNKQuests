package org.fourz.RVNKQuests.objective;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.util.Debug;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;

import java.util.ArrayList;
import java.util.List;

// Listener for placing the event book in the prophecy lectern when a player moves near it

public class ListenerQuestBookPlacer implements Listener {
    private static final String CLASS_NAME = "ListenerQuestBookPlacer";
    private final RVNKQuests plugin;
    private final Debug debugger;
    private final Location lecternLocation;
    private boolean bookPlaced = false;

    public ListenerQuestBookPlacer(RVNKQuests plugin, Location lecternLocation) {
        this.plugin = plugin;
        this.lecternLocation = lecternLocation;
        this.debugger = new Debug(plugin, CLASS_NAME, plugin.getDebugger().getLogLevel()) {};
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (bookPlaced) return;
        
        Player player = event.getPlayer();
        if (player.getLocation().distance(lecternLocation) <= 15) {
            placeEventBook();
            bookPlaced = true;
            
            // Visual and sound effects
            player.playSound(lecternLocation, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
            lecternLocation.getWorld().spawnParticle(Particle.ENCHANTED_HIT, 
                lecternLocation.clone().add(0.5, 1, 0.5), 50, 0.5, 0.5, 0.5, 0.1);
        }
    }

    private void placeEventBook() {
        debugger.debug("Creating and placing event book");
        ItemStack book = createProphecyBook();
        
        if (book != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    Block block = lecternLocation.getBlock();
                    if (block.getType() == Material.LECTERN) {
                        Lectern lectern = (Lectern) block.getState();
                        if (lectern.getInventory() != null) {
                            lectern.getInventory().setItem(0, book);
                            //lectern.getInventory().setItem(0, new ItemStack(Material.WRITABLE_BOOK));
                            lectern.update(true);
                            debugger.debug("Book placed successfully in lectern");
                        }
                    }
                } catch (Exception e) {
                    debugger.debug("ERROR placing book: " + e.getMessage());
                }
            }, 2L);
        }
    }

    private ItemStack createProphecyBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        
        if (meta != null) {
            meta.setTitle("The First City Prophecy");
            meta.setAuthor("The Ancient Ones");
            
            List<String> pages = new ArrayList<>();
            pages.add("§5The First City Prophecy§0\n\n" +
                     "In ages past, before the shattering of worlds, there stood a city of untold wonder.\n\n" +
                     "Now, the spirits call once more...");
            pages.add("§0The whispers speak of a place:\n\n" +
                     "Where peaks touch clouds\n" +
                     "Where waters flow true\n" +
                     "Where the land remembers its past");

            meta.setPages(pages);
            book.setItemMeta(meta);
            return book;
        }
        return null;
    }
}
