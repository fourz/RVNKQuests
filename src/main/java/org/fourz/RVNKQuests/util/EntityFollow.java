package org.fourz.RVNKQuests.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.RVNKQuests.util.log.LogManager;
import org.fourz.RVNKQuests.util.log.FZLogger;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Utility class for making entities follow players or other entities
 * with improved pathfinding and obstacle avoidance
 */
public class EntityFollow {
    // Follow mechanics constants
    private static final double DEFAULT_FOLLOW_DISTANCE = 3.0;
    private static final double DEFAULT_MAX_FOLLOW_DISTANCE = 30.0;
    private static final double DEFAULT_FOLLOW_SPEED = 1.05;
    private static final int DEFAULT_FOLLOW_TASK_DELAY = 5;
    private static final double DEFAULT_JUMP_VELOCITY = 0.3;
    private static final int STUCK_THRESHOLD = 10; // ticks before considering entity "stuck"
    
    // Instance-specific settings
    private final JavaPlugin plugin;
    private final FZLogger logger;
    private double followDistance;
    private double maxFollowDistance;
    private double followSpeed;
    private int followTaskDelay;
    private double jumpVelocity;
    
    // Runtime state
    private BukkitTask followTask = null;
    private boolean isFollowing = false;
    private Player leader = null;
    private Entity follower = null;
    private FollowingMob followingMob = null;
    private Location lastFollowerLocation = null;
    private int stuckCounter = 0;
    private Location pathTarget = null;
    private boolean shouldUseNavigator = false;
    private boolean ignoreQuestMobs = false; // New flag to ignore quest mobs
    
    // Cache of created FollowingMob instances
    private static final Map<UUID, FollowingMob> mobCache = new HashMap<>();

    // Set of materials that are considered obstacles
    private static final Set<Material> OBSTACLE_MATERIALS = new HashSet<>();
    static {
        // Blocks that entities should avoid or jump over
        OBSTACLE_MATERIALS.add(Material.WATER);
        OBSTACLE_MATERIALS.add(Material.LAVA);
        OBSTACLE_MATERIALS.add(Material.CACTUS);
        OBSTACLE_MATERIALS.add(Material.CAMPFIRE);
        OBSTACLE_MATERIALS.add(Material.FIRE);
        OBSTACLE_MATERIALS.add(Material.SOUL_FIRE);
        OBSTACLE_MATERIALS.add(Material.SWEET_BERRY_BUSH);
        OBSTACLE_MATERIALS.add(Material.COBWEB);
        OBSTACLE_MATERIALS.add(Material.POINTED_DRIPSTONE);
    }
    
    /**
     * Creates an EntityFollow with default settings
     * @param plugin The plugin instance
     */
    public EntityFollow(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.followDistance = DEFAULT_FOLLOW_DISTANCE;
        this.maxFollowDistance = DEFAULT_MAX_FOLLOW_DISTANCE;
        this.followSpeed = DEFAULT_FOLLOW_SPEED;
        this.followTaskDelay = DEFAULT_FOLLOW_TASK_DELAY;
        this.jumpVelocity = DEFAULT_JUMP_VELOCITY;
    }
    
    /**
     * Configure follow distance settings
     * @param followDistance The minimum distance before follower starts moving
     * @param maxFollowDistance The maximum regular follow distance 
     * @return this, for method chaining
     */
    public EntityFollow withDistances(double followDistance, double maxFollowDistance) {
        this.followDistance = followDistance;
        this.maxFollowDistance = maxFollowDistance;
        return this;
    }
    
    /**
     * Configure movement speed
     * @param followSpeed The speed at which the entity follows
     * @return this, for method chaining
     */
    public EntityFollow withSpeed(double followSpeed) {
        this.followSpeed = followSpeed;
        return this;
    }
    
    /**
     * Configure task delay (ticks between movement updates)
     * @param followTaskDelay The delay in server ticks
     * @return this, for method chaining
     */
    public EntityFollow withTaskDelay(int followTaskDelay) {
        this.followTaskDelay = followTaskDelay;
        return this;
    }
    
    /**
     * Configure jump velocity for obstacle clearing
     * @param jumpVelocity The upward velocity for jumping
     * @return this, for method chaining
     */
    public EntityFollow withJumpVelocity(double jumpVelocity) {
        this.jumpVelocity = jumpVelocity;
        return this;
    }
    
    /**
     * Enables or disables the use of Minecraft's built-in entity navigation
     * Only works when follower is a Mob
     * @param useNavigator Whether to use entity navigation API
     * @return this, for method chaining
     */
    public EntityFollow useNavigator(boolean useNavigator) {
        this.shouldUseNavigator = useNavigator;
        return this;
    }
    
    /**
     * Configure whether to ignore quest mobs during following
     * @param ignoreQuestMobs Whether follower should ignore quest mobs
     * @return this, for method chaining
     */
    public EntityFollow ignoreQuestMobs(boolean ignoreQuestMobs) {
        this.ignoreQuestMobs = ignoreQuestMobs;
        return this;
    }
    
    /**
     * Starts the follow task - entity will follow the player
     * @param follower The entity that will follow
     * @param leader The player to follow
     * @return true if started successfully
     */
    public boolean start(Entity follower, Player leader) {
        if (follower == null || leader == null || !leader.isOnline()) {
            logger.debug("Cannot start follow - invalid entities");
            return false;
        }
        
        this.follower = follower;
        this.leader = leader;
        this.lastFollowerLocation = follower.getLocation().clone();
        this.stuckCounter = 0;
        
        // Set up FollowingMob if this is a Mob entity and navigator is requested
        if (follower instanceof Mob && shouldUseNavigator) {
            UUID entityId = follower.getUniqueId();
            if (!mobCache.containsKey(entityId)) {
                followingMob = FollowingMob.of((Mob)follower);
                if (followingMob != null) {
                    mobCache.put(entityId, followingMob);
                }
            } else {
                followingMob = mobCache.get(entityId);
            }

            if (followingMob == null) {
                logger.debug("Failed to create FollowingMob wrapper, falling back to velocity-based movement");
            }
        } else {
            followingMob = null;
        }
        
        // Cancel any existing tasks
        stop();
        
        // Start a new follow task
        isFollowing = true;
        followTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (shouldCancelTask()) {
                    cancel();
                    isFollowing = false;
                    return;
                }
                
                updateFollowerMovement();
            }
        }.runTaskTimer(plugin, 0, followTaskDelay);
        
    logger.debug("Started entity follow task");
        return true;
    }
    
    /**
     * Stops the follow task
     */
    public void stop() {
        if (followTask != null) {
            followTask.cancel();
            followTask = null;
        }
        isFollowing = false;
        
        // Stop any active pathfinding
        if (followingMob != null) {
            try {
                followingMob.getPathfinder().stopPathfinding();
            } catch (Exception e) {
                logger.debug("Error stopping pathfinding: " + e.getMessage());
            }
        }

        logger.debug("Stopped entity follow task");
        
        // Make the entity look at the leader when stopped, if both still valid
        if (isValidLeaderAndFollower()) {
            lookAt(follower, leader.getLocation());
        }
    }
    
    /**
     * Toggles the follow behavior
     * @return true if the follow behavior is now active
     */
    public boolean toggle() {
        if (isFollowing) {
            stop();
            return false;
        } else {
            return isValidLeaderAndFollower() && start(follower, leader);
        }
    }
    
    /**
     * Cleans up resources
     */
    public void cleanup() {
        stop();
        leader = null;
        follower = null;
        followingMob = null;
        lastFollowerLocation = null;
        pathTarget = null;
    }
    
    /**
     * Checks if the follow task is active
     * @return true if following is active
     */
    public boolean isFollowing() {
        return isFollowing && followTask != null && !followTask.isCancelled();
    }
    
    /**
     * Gets the entity that is following
     * @return The follower entity
     */
    public Entity getFollower() {
        return follower;
    }
    
    /**
     * Gets the player being followed
     * @return The leader player
     */
    public Player getLeader() {
        return leader;
    }
    
    /**
     * Check if both leader and follower are valid
     */
    private boolean isValidLeaderAndFollower() {
        return leader != null && leader.isOnline() && !leader.isDead() && 
               follower != null && follower.isValid();
    }
    
    /**
     * Check if the task should be cancelled
     */
    private boolean shouldCancelTask() {
        return !isValidLeaderAndFollower();
    }
    
    /**
     * Updates the follower's movement to follow the leader
     */
    private void updateFollowerMovement() {
        if (!isValidLeaderAndFollower()) return;
        
        Location leaderLoc = leader.getLocation();
        Location followerLoc = follower.getLocation();
        double distance = followerLoc.distance(leaderLoc);
        
        // Check if we're already close enough
        if (distance <= followDistance) {
            // Make follower look at leader
            lookAt(follower, leader.getLocation());
            return;
        }
        
        // Check if entity is stuck
        checkIfStuck(followerLoc);
        
        // If follower is within max follow distance, adjust movement
        if (distance <= maxFollowDistance) {
            // Try to use entity pathfinding if we have a valid FollowingMob
            if (followingMob != null && shouldUseNavigator) {
                updateNavigationTarget(leaderLoc);
            } else {
                // Fall back to direct movement control
                moveTowardsTarget(leaderLoc);
            }
        }
        // When beyond max follow distance, entity will stop moving but won't teleport
    }
    
    /**
     * Updates the navigation target for Mob entities using built-in pathfinding
     */
    private void updateNavigationTarget(Location target) {
        if (followingMob == null) return;
        
        // Only update the path if we need to (improves performance)
        if (pathTarget == null || 
            pathTarget.getWorld() != target.getWorld() || 
            pathTarget.distanceSquared(target) > 4.0 ||
            stuckCounter > 0) {
            
            // Create a new pathing target
            pathTarget = target.clone();
            
            // Start pathfinding to the target using our wrapper
            try {
                boolean result;
                
                if (ignoreQuestMobs && followingMob.getMob() instanceof Mob) {
                    Mob mob = (Mob) followingMob.getMob();
                    // Save current target to restore after path update
                    Entity currentTarget = mob.getTarget();
                    
                    // Clear target temporarily to avoid combat during navigation update
                    mob.setTarget(null);
                    
                    // Update path
                    result = followingMob.getPathfinder().moveTo(target, followSpeed);
                    
                    // Restore original target if it wasn't a quest mob
                    if (currentTarget != null && !currentTarget.hasMetadata("rvnkquests.questmob")) {
                        mob.setTarget((LivingEntity)currentTarget);
                    }
                } else {
                    result = followingMob.getPathfinder().moveTo(target, followSpeed);
                }
                
                logger.debug("Updated pathfinding target, success: " + result + "");
            } catch (Exception e) {
                logger.debug("Error updating navigation: " + e.getMessage());
                // Fall back to velocity-based movement for this update
                moveTowardsTarget(target);
            }
        }
    }
    
    /**
     * Moves the entity towards a target using velocity
     */
    private void moveTowardsTarget(Location target) {
        // Get current locations
        Location followerLoc = follower.getLocation();
        
        // Calculate base direction vector
        Vector direction = target.toVector().subtract(followerLoc.toVector());
        
        // Check if there are obstacles in the way
        boolean needsJump = hasObstacleAhead(followerLoc, direction) || stuckCounter > 0;
        
        // Normalize and scale by speed
        direction.normalize().multiply(followSpeed);
        
        // Add jump component if needed
        if (needsJump) {
            direction.setY(jumpVelocity);
            logger.debug("Adding jump velocity to clear obstacle");
        }
        
        // Apply velocity
        follower.setVelocity(direction);
        
        // Make follower look in movement direction
        lookAt(follower, target);
    }
    
    /**
     * Checks if the entity is stuck and not making progress
     */
    private void checkIfStuck(Location currentLoc) {
        if (lastFollowerLocation == null || 
            lastFollowerLocation.getWorld() != currentLoc.getWorld()) {
            lastFollowerLocation = currentLoc.clone();
            return;
        }
        
        // Calculate if we've moved significantly
        double moveDistance = lastFollowerLocation.distanceSquared(currentLoc);
        if (moveDistance < 0.01) { // Barely moved
            stuckCounter++;
            if (stuckCounter >= STUCK_THRESHOLD) {
                logger.debug("Entity appears stuck, stuckCounter: " + stuckCounter + "");
            }
        } else {
            // Reset stuck counter if we've moved
            stuckCounter = 0;
        }
        
        // Update last location
        lastFollowerLocation = currentLoc.clone();
    }
    
    /**
     * Checks if there are obstacles in front of the entity
     */
    private boolean hasObstacleAhead(Location location, Vector direction) {
        // Normalize direction and create a test vector
        Vector testDirection = direction.clone().normalize();
        
        // Check blocks ahead
        for (double d = 0.5; d <= 2.0; d += 0.5) {
            Vector testVector = testDirection.clone().multiply(d);
            Location testLoc = location.clone().add(testVector);
            Block block = testLoc.getBlock();
            
            // Check for obstacles
            if (block.getType() != Material.AIR && 
                block.getType() != Material.CAVE_AIR && 
                block.getType() != Material.VOID_AIR && 
                !block.isPassable() || 
                OBSTACLE_MATERIALS.contains(block.getType())) {
                
                logger.debug("Detected obstacle: " + block.getType() + " at distance " + d + "");
                return true;
            }
            
            // Also check for drops/gaps
            Block belowBlock = testLoc.clone().subtract(0, 1, 0).getBlock();
            if (belowBlock.getType() == Material.AIR || 
                belowBlock.getType() == Material.CAVE_AIR || 
                belowBlock.getType() == Material.VOID_AIR ||
                belowBlock.isPassable()) {
                
                // There's a gap ahead, check if it's too large to safely cross
                int gapDepth = 0;
                Location checkLoc = belowBlock.getLocation().clone();
                
                while (gapDepth < 5 && checkLoc.getBlockY() > 0) {
                    checkLoc.subtract(0, 1, 0);
                    Block checkBlock = checkLoc.getBlock();
                    
                    if (checkBlock.getType() != Material.AIR && 
                        checkBlock.getType() != Material.CAVE_AIR && 
                        checkBlock.getType() != Material.VOID_AIR && 
                        !checkBlock.isPassable()) {
                        break;
                    }
                    
                    gapDepth++;
                }
                
                    if (gapDepth > 2) { // Gap is too deep to safely cross
                        logger.debug("Detected drop ahead with depth " + gapDepth + "");
                        return true;
                    }
            }
        }
        
        return false;
    }
    
    /**
     * Makes an entity look at a specific location
     */
    public static void lookAt(Entity entity, Location target) {
        // Calculate the direction vector
        Location location = entity.getLocation();
        double dx = target.getX() - location.getX();
        double dz = target.getZ() - location.getZ();
        
        // Calculate the yaw
        double yaw = Math.atan2(dz, dx);
        
        // Convert to degrees
        yaw = yaw * (180 / Math.PI) - 90;
        if (yaw < 0) {
            yaw += 360;
        }
        
        // Update the entity's rotation
        Location newLoc = location.clone();
        newLoc.setYaw((float) yaw);
        entity.teleport(newLoc);
    }
}
