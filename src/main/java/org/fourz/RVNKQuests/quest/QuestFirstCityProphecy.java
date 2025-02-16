package org.fourz.RVNKQuests.quest;

import org.bukkit.*;
import org.bukkit.block.Lectern;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.fourz.RVNKQuests.RVNKQuests;
import org.bukkit.event.Listener;
import org.fourz.RVNKQuests.objective.ListenerFirstCityChoice;
import org.fourz.RVNKQuests.trigger.ListenerProphecyDiscovery;
import org.fourz.RVNKQuests.trigger.ListenerProphecyVisions;
import org.fourz.RVNKQuests.trigger.ListenerEventPopulated;

import java.util.ArrayList;
import java.util.List;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CylinderRegion;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.math.Vector2;
//import com.sk89q.worldedit.world.World;


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
        Location spawn = world.getSpawnLocation().clone();
        spawn.add(12, 0, -12);

        // Ensure we're placing on the highest block
        spawn.setY(world.getHighestBlockYAt(spawn));
        
        createStonePedestal(spawn);

        // Place lectern at the top center (adjusted for new cylinder height)
        Location lecternLoc = spawn.clone().add(0, 11, 0);
        
        plugin.getDebugger().debug("Lectern placed at: " + lecternLocation);

        plugin.getDebugger().debug("Creating prophecy book item");
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        
        plugin.getDebugger().debug("Setting book metadata");
        meta.setTitle("The First City Prophecy");
        meta.setAuthor("The Ancient Ones");
        
        plugin.getDebugger().debug("Adding book pages");
        String page1 = "§5The First City Prophecy§0\n\n" +
                      "In ages past, before the shattering of worlds, there stood a city of untold wonder.\n\n" +
                      "Now, the spirits call once more...";
        String page2 = "§0The whispers speak of a place:\n\n" +
                      "Where peaks touch clouds\n" +
                      "Where waters flow true\n" +
                      "Where the land remembers its past";
        
        meta.addPage(page1);
        meta.addPage(page2);
        plugin.getDebugger().debug("Book pages added - Page count: " + meta.getPageCount());
        
        book.setItemMeta(meta);
        plugin.getDebugger().debug("Book metadata applied successfully");

        try {
            Lectern lectern = (Lectern) lecternLoc.getBlock().getState();
            plugin.getDebugger().debug("Lectern state retrieved at: " + lecternLoc);
            
            lectern.getInventory().setItem(0, book);
            plugin.getDebugger().debug("Book placed in lectern inventory - Slot 0");
            
            boolean updated = lectern.update();
            plugin.getDebugger().debug("Lectern state update: " + (updated ? "successful" : "failed"));
            
            // Verify book placement
            ItemStack placedBook = lectern.getInventory().getItem(0);
            if (placedBook != null && placedBook.getType() == Material.WRITTEN_BOOK) {
                BookMeta placedMeta = (BookMeta) placedBook.getItemMeta();
                plugin.getDebugger().debug("Book verification - Title: " + placedMeta.getTitle() +
                                         ", Author: " + placedMeta.getAuthor() +
                                         ", Pages: " + placedMeta.getPageCount());
            } else {
                plugin.getDebugger().debug("WARNING: Book placement verification failed!");
            }
        } catch (Exception e) {
            plugin.getDebugger().debug("ERROR placing book: " + e.getMessage());
        }
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

    public void createStonePedestal(Location location) {
        plugin.getDebugger().debug("=== Starting Stone Pedestal Creation ===");
        plugin.getDebugger().debug("Initial location: " + String.format("x:%.2f, y:%.2f, z:%.2f", 
            location.getX(), location.getY(), location.getZ()));

        // Ensure the location is on block boundaries
        location.setX(location.getBlockX());
        location.setY(location.getBlockY());
        location.setZ(location.getBlockZ());
        
        plugin.getDebugger().debug("Aligned location: " + String.format("x:%d, y:%d, z:%d", 
            location.getBlockX(), location.getBlockY(), location.getBlockZ()));
    
        try {
            // Convert Bukkit world and location to WorldEdit's format
            plugin.getDebugger().debug("Converting Bukkit world to WorldEdit format");
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(location.getWorld());
            
            plugin.getDebugger().debug("Creating center vector at: " + String.format("x:%d, y:%d, z:%d", 
                location.getBlockX(), location.getBlockY(), location.getBlockZ()));
            BlockVector3 center = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        
            // Define the radius and height of the cylinder
            Vector2 radius = new Vector2(5, 5);
            int height = 10;
            plugin.getDebugger().debug("Cylinder parameters - Radius: 5 blocks, Height: 10 blocks");

            plugin.getDebugger().debug("Creating cylinder region");
            plugin.getDebugger().debug("Y-Range: " + location.getBlockY() + " to " + (location.getBlockY() + height));
            CylinderRegion cylinder = new CylinderRegion(weWorld, center, radius, location.getBlockY() + height, location.getBlockY());
            
            plugin.getDebugger().debug("Cylinder bounds: " + 
                String.format("min(%d,%d,%d) max(%d,%d,%d)",
                    cylinder.getMinimumPoint().getX(),
                    cylinder.getMinimumPoint().getY(),
                    cylinder.getMinimumPoint().getZ(),
                    cylinder.getMaximumPoint().getX(),
                    cylinder.getMaximumPoint().getY(),
                    cylinder.getMaximumPoint().getZ()));
        
            // Use an EditSession to make changes
            plugin.getDebugger().debug("Opening WorldEdit EditSession");
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                plugin.getDebugger().debug("Setting blocks to stone");
                int blocksAffected = editSession.setBlocks(cylinder, BlockTypes.STONE.getDefaultState());
                plugin.getDebugger().debug("Blocks affected: " + blocksAffected);
                
                plugin.getDebugger().debug("Flushing EditSession");
                editSession.flushSession();
            }
            plugin.getDebugger().debug("EditSession closed successfully");
            
        } catch (Exception e) {
            plugin.getDebugger().debug("ERROR creating pedestal: " + e.getMessage());
            plugin.getDebugger().debug("Stack trace:");
            e.printStackTrace();
        }
        
        plugin.getDebugger().debug("=== Stone Pedestal Creation Complete ===");
    }
    
}
