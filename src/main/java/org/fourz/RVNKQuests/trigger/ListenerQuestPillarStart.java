package org.fourz.RVNKQuests.trigger;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.util.Debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Listener for placing the prophecy lectern around the spawn point

public class ListenerQuestPillarStart {
    private static final String CLASS_NAME = "ListenerQuestPillarStart";
    private final RVNKQuests plugin;
    private final Debug debugger;

    public ListenerQuestPillarStart(RVNKQuests plugin) {
        this.plugin = plugin;
        this.debugger = new Debug(plugin, CLASS_NAME, plugin.getDebugger().getLogLevel()) {};
    }

    public Location buildQuestBeacon() {
        debugger.debug("Building quest beacon structure");
        return placeSpawnLectern();
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

        debugger.debug("LECTERN LOCATION: " + top);
        
        createStonePedestal(top, 100, 5, Material.DIRT, false);
        createStonePedestal(top, 1, 5, Material.GRASS_BLOCK, false);
        createStonePedestal(top, 100, 6, Material.STONE, true);
        
        if (!top.getChunk().isLoaded()) {
            top.getChunk().load();
        }
        
        top.getBlock().setType(Material.LECTERN);

        //return location of lectern
        return top;
    }

    private void createStonePedestal(Location topCenter, int height, int radius, Material block, boolean hollow) {
        int centerX = topCenter.getBlockX();
        int endY = topCenter.getBlockY();
        int startY = endY - height;
        int centerZ = topCenter.getBlockZ();

        for (int y = startY; y < endY; y++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    double distance = Math.sqrt(Math.pow(centerX - x, 2) + Math.pow(centerZ - z, 2));
                    if (distance <= radius) {
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
