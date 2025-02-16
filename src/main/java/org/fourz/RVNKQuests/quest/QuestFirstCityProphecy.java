package org.fourz.RVNKQuests.quest;

import org.bukkit.*;
import org.bukkit.block.Lectern;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.event.Listener;

import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.trigger.ListenerProphecyDiscovery;
import org.fourz.RVNKQuests.trigger.ListenerProphecyVisions;
import org.fourz.RVNKQuests.trigger.ListenerEventPopulated;
import org.fourz.RVNKQuests.objective.ListenerFirstCityChoice;
import org.fourz.RVNKQuests.util.Debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class QuestFirstCityProphecy implements Quest {
    private static final String CLASS_NAME = "QuestFirstCityProphecy";
    private final RVNKQuests plugin;
    private final Debug debugger;
    private QuestState currentState = QuestState.NOT_STARTED;
    private Location lecternLocation;

    public QuestFirstCityProphecy(RVNKQuests plugin) {
        this.plugin = plugin;
        this.debugger = new Debug(plugin, CLASS_NAME, plugin.getDebugger().getLogLevel()) {};
    }

    @Override
    public String getId() {
        return "first_city_prophecy";
    }

    @Override
    public String getName() {
        return "The First City Prophecy";
    }

    @Override
    public void initialize() {
        debugger.debug("Initializing First City Prophecy quest");
        buildQuestBeacon();
    }

    @Override
    public void cleanup() {
        debugger.debug("Cleaning up First City Prophecy quest");
        if (lecternLocation != null) {
            lecternLocation.getBlock().setType(Material.AIR);
        }
    }

    @Override
    public boolean isCompleted(Player player) {
        return currentState == QuestState.COMPLETED;
    }

    @Override
    public QuestState getCurrentState() {
        return currentState;
    }

    @Override
    public void advanceState(QuestState newState) {
        debugger.debug("Advancing quest state from " + currentState + " to " + newState);
        this.currentState = newState;
        plugin.getQuestManager().updateQuestListeners(this);
    }

    @Override
    public Location getLecternLocation() {
        return lecternLocation;
    }

    public void buildQuestBeacon() {
        debugger.debug("Building quest beacon structure");
        Location lecternLocation = placeSpawnLectern();
        if (lecternLocation != null) {
            placeEventBook(lecternLocation);
        } else {
            debugger.debug("Failed to place lectern, skipping book placement");
        }
    }

    private Location placeSpawnLectern() {
        debugger.debug("Placing prophecy lectern at spawn");
        World world = Bukkit.getWorld("event");
        if (world == null) {
            debugger.debug("Event world not found, using default world");
            world = Bukkit.getWorlds().get(0);
        }

        Location top = world.getSpawnLocation().clone();
        Random random = new Random();
        int x = (random.nextInt(10) - 5) * 9;
        int z = (random.nextInt(10) - 5) * 9;
        top.add(x, 0, z);
        top.setY(world.getHighestBlockYAt(top));
        top.add(0, 2, 0);

        //output location to console
        debugger.debug("LECTERN LOCATION: " + top);
        
        // Create outer grass pedestal first
        createStonePedestal(top, 100, 5, Material.DIRT, false);
        createStonePedestal(top, 1, 5, Material.GRASS_BLOCK, false);
        
        // Create inner stone pedestal with slightly smaller radius
        createStonePedestal(top, 100, 6, Material.STONE, true);
        
        // Ensure chunk is loaded
        if (!top.getChunk().isLoaded()) {
            top.getChunk().load();
        }
        
        // Place lectern
        top.getBlock().setType(Material.LECTERN);
        this.lecternLocation = top;
        
        return top;
    }

    private void placeEventBook(Location lecternLocation) {
        debugger.debug("Creating and placing event book");
        ItemStack book = createProphecyBook();
        
        if (book != null) {
            // Schedule the book placement for the next tick to ensure lectern is properly placed
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    Block block = lecternLocation.getBlock();
                    if (block.getType() == Material.LECTERN) {
                        Lectern lectern = (Lectern) block.getState();
                        if (lectern.getInventory() != null) {
                            lectern.getInventory().setItem(0, book);
                            lectern.update(true);
                            debugger.debug("Book placed successfully in lectern");
                        } else {
                            debugger.debug("ERROR: Lectern inventory is null");
                        }
                    } else {
                        debugger.debug("ERROR: Block is not a lectern, found: " + block.getType());
                    }
                } catch (Exception e) {
                    debugger.debug("ERROR placing book: " + e.getMessage());
                    e.printStackTrace();
                }
            }, 2L);
        } else {
            debugger.debug("ERROR: Failed to create book");
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

    public boolean isValidSettlementLocation(Location loc) {
        debugger.debug("Checking settlement location validity at: " + loc);
        World world = loc.getWorld();
        int highestY = world.getHighestBlockYAt(loc);
        
        if (highestY < 100) {
            debugger.debug("Location rejected: Height " + highestY + " is too low");
            return false;
        }
        
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; x <= 16; z++) {
                Location check = loc.clone().add(x, 0, z);
                if (check.getBlock().getType() == Material.WATER) {
                    debugger.debug("Location accepted: Found water source nearby");
                    return true;
                }
            }
        }
        debugger.debug("Location rejected: No water source found nearby");
        return false;
    }

    public List<Listener> createListenersForState(QuestState state) {
        List<Listener> listeners = new ArrayList<>();
        switch (state) {
            case NOT_STARTED:
                listeners.add(new ListenerEventPopulated(this));
                break;
            case TRIGGER_FOUND:
                listeners.add(new ListenerProphecyDiscovery(this));
                break;
            case QUEST_ACTIVE:
                listeners.add(new ListenerProphecyVisions(plugin, this));
                break;
            case OBJECTIVE_COMPLETE:
                listeners.add(new ListenerFirstCityChoice(plugin, this));
                break;
        }
        return listeners;
    }

    public void createStonePedestal(Location topCenter, int height, int radius, Material block, boolean hollow) {
        //plugin.getDebugger().debug("=== Starting Pedestal Creation ===");
        //plugin.getDebugger().debug("Parameters - Height: " + height + ", Radius: " + radius + ", Material: " + block + ", Hollow: " + hollow);

        // Center point (topCenter is the top-middle of the cylinder)
        int centerX = topCenter.getBlockX();
        int endY = topCenter.getBlockY();
        int startY = endY - height; // Work downward from the top
        int centerZ = topCenter.getBlockZ();

        // Create the cylinder layer by layer, starting from the bottom
        for (int y = startY; y < endY; y++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    // Calculate distance from center
                    double distance = Math.sqrt(Math.pow(centerX - x, 2) + Math.pow(centerZ - z, 2));
                    
                    // If point is within radius
                    if (distance <= radius) {
                        // For hollow cylinders, only place blocks on the outer edge
                        if (!hollow || distance >= radius - 1) {
                            topCenter.getWorld().getBlockAt(x, y, z).setType(block);
                        }
                    }
                }
            }
        }
        
        debugger.debug("=== Pedestal Creation Complete ===");
    }
    
}
