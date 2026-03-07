package org.fourz.RVNKQuests.lore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Represents a discovery in the lore database
 */
public class LoreDiscovery {
    private final int id;
    private final String discoveryType;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final String description;
    private final long discoveryTime;
    
    /**
     * Create a new lore discovery
     * 
     * @param id The discovery ID
     * @param discoveryType The type of discovery
     * @param worldName The world name
     * @param x The x coordinate
     * @param y The y coordinate
     * @param z The z coordinate
     * @param description The discovery description
     * @param discoveryTime The time of discovery (timestamp)
     */
    public LoreDiscovery(int id, String discoveryType, String worldName, int x, int y, int z, 
                           String description, long discoveryTime) {
        this.id = id;
        this.discoveryType = discoveryType;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.description = description;
        this.discoveryTime = discoveryTime;
    }
    
    /**
     * Get the discovery ID
     * @return the ID
     */
    public int getId() {
        return id;
    }
    
    /**
     * Get the discovery type
     * @return the discovery type
     */
    public String getDiscoveryType() {
        return discoveryType;
    }
    
    /**
     * Get the world name
     * @return the world name
     */
    public String getWorldName() {
        return worldName;
    }
    
    /**
     * Get the x coordinate
     * @return the x coordinate
     */
    public int getX() {
        return x;
    }
    
    /**
     * Get the y coordinate
     * @return the y coordinate
     */
    public int getY() {
        return y;
    }
    
    /**
     * Get the z coordinate
     * @return the z coordinate
     */
    public int getZ() {
        return z;
    }
    
    /**
     * Get the discovery description
     * @return the description
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Get the discovery time
     * @return the discovery time
     */
    public long getDiscoveryTime() {
        return discoveryTime;
    }
    
    /**
     * Get the location of this discovery
     * @return the location or null if the world doesn't exist
     */
    public Location getLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z);
    }
    
    @Override
    public String toString() {
        return String.format("LoreDiscovery[id=%d, type=%s, world=%s, pos=(%d,%d,%d)]", 
            id, discoveryType, worldName, x, y, z);
    }
}
