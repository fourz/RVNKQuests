package org.fourz.RVNKQuests.quest;

import org.bukkit.*;
import org.bukkit.block.Lectern;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.fourz.RVNKQuests.RVNKQuests;
import org.bukkit.event.Listener;
import org.fourz.RVNKQuests.trigger.ListenerProphecyDiscovery;
import org.fourz.RVNKQuests.trigger.ListenerProphecyVisions;
import org.fourz.RVNKQuests.trigger.ListenerEventPopulated;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class QuestFirstCityProphecy implements Quest {
    private final RVNKQuests plugin;
    private QuestState currentState = QuestState.NOT_STARTED;
    private Location lecternLocation;

    public QuestFirstCityProphecy(RVNKQuests plugin) {
        this.plugin = plugin;
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
        plugin.getDebugger().debug("Initializing First City Prophecy quest");
        
        // Remove the automatic lectern placement
    }

    @Override
    public void cleanup() {
        plugin.getDebugger().debug("Cleaning up First City Prophecy quest");
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
        plugin.getDebugger().debug("Advancing quest state from " + currentState + " to " + newState);
        this.currentState = newState;
        plugin.getQuestManager().updateQuestListeners(this);
    }

    @Override
    public Location getLecternLocation() {
        return lecternLocation;
    }

    // Change private to public
    public void placeSpawnLectern() {
        plugin.getDebugger().debug("Placing prophecy lectern at spawn");
        World world = Bukkit.getWorld("event");
        if (world == null) {
            plugin.getDebugger().debug("Event world not found, using default world");
            world = Bukkit.getWorlds().get(0);
        }
        Location top = world.getSpawnLocation().clone();
        Random random = new Random();
        int x = (random.nextInt(10) - 5) * 8;
        int z = (random.nextInt(10) - 5) * 8;
        top.add(x, 0, z);
        top.setY(world.getHighestBlockYAt(top));
        top.add(0, 2, 0);
        
        // Create outer grass pedestal first
        createStonePedestal(top.clone(), 100, 5, Material.GRASS_BLOCK, false);
        
        // Create inner stone pedestal with slightly smaller radius
        createStonePedestal(top.clone(), 100, 4, Material.STONE, true);
        
        // Place lectern separately after pedestals are created
        top.getBlock().setType(Material.LECTERN);
        this.lecternLocation = top;

        // Create the book
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        
        meta.setTitle("The First City Prophecy");
        meta.setAuthor("The Ancient Ones");
        
        String page1 = "§5The First City Prophecy§0\n\n" +
                      "In ages past, before the shattering of worlds, there stood a city of untold wonder.\n\n" +
                      "Now, the spirits call once more...";
        String page2 = "§0The whispers speak of a place:\n\n" +
                      "Where peaks touch clouds\n" +
                      "Where waters flow true\n" +
                      "Where the land remembers its past";
        
        meta.addPage(page1);
        meta.addPage(page2);
        book.setItemMeta(meta);

        // Place the book in the lectern after a short delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (top.getBlock().getType() == Material.LECTERN) {
                Lectern lectern = (Lectern) top.getBlock().getState();
                lectern.getInventory().setItem(0, book);
                lectern.update();
                plugin.getDebugger().debug("Book placed successfully in lectern at: " + top);
            } else {
                plugin.getDebugger().debug("ERROR: Block at location is not a lectern: " + top.getBlock().getType());
            }
        }, 5L); // Increased delay to 5 ticks for more reliability
    }

    public boolean isValidSettlementLocation(Location loc) {
        plugin.getDebugger().debug("Checking settlement location validity at: " + loc);
        World world = loc.getWorld();
        int highestY = world.getHighestBlockYAt(loc);
        
        if (highestY < 100) {
            plugin.getDebugger().debug("Location rejected: Height " + highestY + " is too low");
            return false;
        }
        
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; x <= 16; z++) {
                Location check = loc.clone().add(x, 0, z);
                if (check.getBlock().getType() == Material.WATER) {
                    plugin.getDebugger().debug("Location accepted: Found water source nearby");
                    return true;
                }
            }
        }
        plugin.getDebugger().debug("Location rejected: No water source found nearby");
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
        }
        return listeners;
    }

    public void createStonePedestal(Location topCenter, int height, int radius, Material block, boolean hollow) {
        plugin.getDebugger().debug("=== Starting Pedestal Creation ===");
        plugin.getDebugger().debug("Parameters - Height: " + height + ", Radius: " + radius + ", Material: " + block + ", Hollow: " + hollow);

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
        
        // Place lectern at the top center (which is the input location)
        topCenter.getBlock().setType(Material.LECTERN);
        this.lecternLocation = topCenter;
        
        plugin.getDebugger().debug("=== Pedestal Creation Complete ===");
        plugin.getDebugger().debug("Lectern placed at: " + topCenter);
    }
    
}
