package org.fourz.RVNKQuests.util;

 import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

/*
import org.bukkit.craftbukkit.v1_21_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_21_R1.inventory.CraftItemStack;
import net.minecraft.core.BlockPosition;
import net.minecraft.world.level.block.entity.TileEntityLectern;

public class NMSUtil {
    
    public static boolean placeLecternBook(World world, Location location, ItemStack book) {
        try {
            // Convert Bukkit world to NMS world
            net.minecraft.world.level.World nmsWorld = ((CraftWorld) world).getHandle();
            
            // Create BlockPosition from location
            BlockPosition pos = new BlockPosition(
                location.getBlockX(), 
                location.getBlockY(), 
                location.getBlockZ()
            );
            
            // Get lectern tile entity
            TileEntityLectern lectern = (TileEntityLectern) nmsWorld.getBlockEntity(pos);
            if (lectern == null) {
                return false;
            }
            
            // Convert Bukkit ItemStack to NMS ItemStack
            net.minecraft.world.item.ItemStack nmsBook = CraftItemStack.asNMSCopy(book);
            
            // Place book in lectern
            lectern.setItem(nmsBook);
            lectern.update();
            
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    } 
}

*/