package org.fourz.RVNKQuests.util;

import org.bukkit.Location;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IntervalChecker {
    private final int checkFrequency;
    private final double minMovementDistance;
    private int counter = 0;
    private final Map<UUID, Location> lastCheckLocations = new HashMap<>();
    
    public IntervalChecker(int checkFrequency, double minMovementDistance) {
        this.checkFrequency = checkFrequency;
        this.minMovementDistance = minMovementDistance;
    }
    
    public boolean shouldCheck(UUID entityId, Location currentLocation) {
        // First, check the counter frequency
        if (++counter % checkFrequency != 0) {
            return false;
        }

        // Then check the movement distance
        Location lastLocation = lastCheckLocations.get(entityId);
        if (lastLocation != null) {
            // Skip distance check if locations are in different worlds
            if (lastLocation.getWorld() == null || currentLocation.getWorld() == null ||
                !lastLocation.getWorld().equals(currentLocation.getWorld())) {
                // Different world = player teleported, always check
                lastCheckLocations.put(entityId, currentLocation.clone());
                return true;
            }

            if (lastLocation.distance(currentLocation) < minMovementDistance) {
                return false;
            }
        }

        // Update the last check location and return true
        lastCheckLocations.put(entityId, currentLocation.clone());
        return true;
    }
    
    public void reset() {
        counter = 0;
        lastCheckLocations.clear();
    }
    
    public void clearEntity(UUID entityId) {
        lastCheckLocations.remove(entityId);
    }
}
