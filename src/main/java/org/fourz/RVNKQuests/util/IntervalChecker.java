package org.fourz.RVNKQuests.util;

import org.bukkit.Location;
import org.bukkit.World;
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
        if (++counter % checkFrequency != 0) {
            return false;
        }

        Location lastLocation = lastCheckLocations.get(entityId);
        if (lastLocation != null) {
            World lastWorld;
            try {
                lastWorld = lastLocation.getWorld();
            } catch (IllegalArgumentException e) {
                // Cached world was unloaded since last check — treat as world change
                lastCheckLocations.put(entityId, currentLocation.clone());
                return true;
            }

            if (lastWorld == null || currentLocation.getWorld() == null ||
                    !lastWorld.equals(currentLocation.getWorld())) {
                lastCheckLocations.put(entityId, currentLocation.clone());
                return true;
            }

            if (lastLocation.distance(currentLocation) < minMovementDistance) {
                return false;
            }
        }

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
