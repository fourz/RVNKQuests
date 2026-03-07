package org.fourz.RVNKQuests.util;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

/**
 * Interface to wrap Bukkit Mob entities and provide pathfinding
 * functionality for different API versions.
 */
public interface FollowingMob {
    
    /**
     * Gets the underlying Bukkit Mob
     * @return The Bukkit mob entity
     */
    Mob getMob();
    
    /**
     * Gets the pathfinder controller for this mob
     * @return The pathfinder
     */
    PathFinder getPathfinder();
    
    /**
     * Creates a FollowingMob wrapper for a Bukkit Mob
     * @param mob The Bukkit mob to wrap
     * @return A FollowingMob wrapper
     */
    static FollowingMob of(Mob mob) {
        if (mob == null) return null;
        
        try {
            return new SafeFollowingMob(mob);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Interface for pathfinder functionality
     */
    interface PathFinder {
        /**
         * Makes the mob move to a location
         * @param loc The target location
         * @return True if pathfinding was started
         */
        boolean moveTo(Location loc);
        
        /**
         * Makes the mob move to a location at given speed
         * @param loc The target location
         * @param speed The movement speed
         * @return True if pathfinding was started
         */
        boolean moveTo(Location loc, double speed);
        
        /**
         * Stops any active pathfinding
         */
        void stopPathfinding();
    }
    
    /**
     * Safe implementation that works across multiple Bukkit versions
     */
    class SafeFollowingMob implements FollowingMob {
        private final Mob mob;
        private final PathFinder pathFinder;
        
        public SafeFollowingMob(Mob mob) {
            this.mob = mob;
            this.pathFinder = new SafePathFinder(mob);
        }
        
        @Override
        public Mob getMob() {
            return mob;
        }
        
        @Override
        public PathFinder getPathfinder() {
            return pathFinder;
        }
        
        /**
         * Safe implementation that uses reflection to call methods when available
         */
        private static class SafePathFinder implements PathFinder {
            private final Mob mob;
            
            public SafePathFinder(Mob mob) {
                this.mob = mob;
            }
            
            @Override
            public boolean moveTo(Location loc) {
                try {
                    // Try direct API method first (Bukkit 1.16+)
                    try {
                        Object pathfinder = getMobPathfinder(mob);
                        if (pathfinder != null) {
                            return (boolean) pathfinder.getClass().getMethod("moveTo", Location.class).invoke(pathfinder, loc);
                        }
                    } catch (Exception ignored) {}
                    
                    // Fallback to using entity AI navigation
                    trySetNavigation(mob, loc);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
            
            @Override
            public boolean moveTo(Location loc, double speed) {
                try {
                    // Try direct API method first (Bukkit 1.16+)
                    try {
                        Object pathfinder = getMobPathfinder(mob);
                        if (pathfinder != null) {
                            try {
                                return (boolean) pathfinder.getClass().getMethod("moveTo", Location.class, double.class)
                                    .invoke(pathfinder, loc, speed);
                            } catch (NoSuchMethodException nsm) {
                                return (boolean) pathfinder.getClass().getMethod("moveTo", Location.class)
                                    .invoke(pathfinder, loc);
                            }
                        }
                    } catch (Exception ignored) {}
                    
                    // Fallback to using entity AI navigation
                    trySetNavigation(mob, loc);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
            
            @Override
            public void stopPathfinding() {
                try {
                    // Try direct API method first (Bukkit 1.16+)
                    try {
                        Object pathfinder = getMobPathfinder(mob);
                        if (pathfinder != null) {
                            pathfinder.getClass().getMethod("stopPathfinding").invoke(pathfinder);
                            return;
                        }
                    } catch (Exception ignored) {}
                    
                    // Fallback methods
                    trySetNavigation(mob, null);
                } catch (Exception ignored) {}
            }
            
            /**
             * Gets the mob's pathfinder safely using reflection
             * @param mob The mob entity
             * @return The pathfinder object or null
             */
            private Object getMobPathfinder(Mob mob) {
                try {
                    return mob.getClass().getMethod("getPathfinder").invoke(mob);
                } catch (Exception e) {
                    return null;
                }
            }
            
            /**
             * Attempts to set the mob's navigation target through various methods
             * @param mob The mob entity
             * @param loc The target location (null to clear)
             */
            private void trySetNavigation(Mob mob, Location loc) throws Exception {
                if (loc == null) {
                    if (mob instanceof LivingEntity) {
                        ((LivingEntity) mob).setAI(false);
                        ((LivingEntity) mob).setAI(true);
                    }
                    return;
                }
                
                // Try to use the native AI to move to a location
                
                // 1. Try setting the target directly if pos
                if (loc != null) {
                    try {
                        // Some versions use 'setDestination'
                        Object handle = mob.getClass().getMethod("getHandle").invoke(mob);
                        handle.getClass().getMethod("setDestination", double.class, double.class, double.class)
                            .invoke(handle, loc.getX(), loc.getY(), loc.getZ());
                        return;
                    } catch (Exception ignored) {}
                    
                    // Try to make the entity walk in the right direction
                    double dx = loc.getX() - mob.getLocation().getX();
                    double dz = loc.getZ() - mob.getLocation().getZ();
                    float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
                    Location lookLoc = mob.getLocation().clone();
                    lookLoc.setYaw(yaw);
                    mob.teleport(lookLoc);
                }
            }
        }
    }
}
