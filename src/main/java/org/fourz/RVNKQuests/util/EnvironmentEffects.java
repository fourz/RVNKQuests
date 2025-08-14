package org.fourz.RVNKQuests.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.util.log.LogManager;
import org.fourz.RVNKQuests.util.log.FZLogger;

import java.util.Random;
import java.util.function.Consumer;

public class EnvironmentEffects {
    private static final Random random = new Random();
    private static FZLogger logger;
    private static boolean initialized = false;

    private EnvironmentEffects() {} // Prevent instantiation

    public static void init(RVNKQuests plugin) {
    logger = LogManager.getInstance(plugin, EnvironmentEffects.class);
    initialized = true;
    }

    private static void logDebug(String message) {
        if (initialized && logger != null) {
            logger.debug(message);
        }
    }

    public static void startDramaticLightningSequence(JavaPlugin plugin, Location centerLocation, 
                                           int radius, int durationTicks, 
                                           int lightningStrikes, Consumer<Void> onComplete) {
        if (centerLocation == null || centerLocation.getWorld() == null) {
            logDebug("Cannot start lightning sequence: null location or world");
            if (onComplete != null) onComplete.accept(null);
            return;
        }
        
        World world = centerLocation.getWorld();
        long originalTime = world.getTime();

        logDebug("Starting dramatic lightning sequence in world: " + world.getName());

        new BukkitRunnable() {
            int ticksElapsed = 0;
            int lightningStruck = 0;
            int[] lightningTimes = generateLightningTimes(lightningStrikes, durationTicks);

            @Override
            public void run() {
                ticksElapsed++;

                // Verify world is still loaded/valid
                if (!world.equals(centerLocation.getWorld())) {
                    logDebug("World mismatch, cancelling lightning sequence");
                    this.cancel();
                    if (onComplete != null) onComplete.accept(null);
                    return;
                }

                if (lightningStruck < lightningStrikes && ticksElapsed >= lightningTimes[lightningStruck]) {
                    strikeDramaticLightning(centerLocation, radius);
                    lightningStruck++;
                }

                updateWorldDarkness(world, originalTime, ticksElapsed, durationTicks);

                if (ticksElapsed >= durationTicks) {
                    logDebug("Lightning sequence completed in: " + world.getName());
                    if (onComplete != null) {
                        onComplete.accept(null);
                    }
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public static void weatherClearDramatic(JavaPlugin plugin, World world, int durationTicks) {
        if (world == null) {
            logDebug("Cannot clear weather: null world");
            return;
        }
        
        long startTime = world.getTime();
        boolean wasStorming = world.hasStorm();
        boolean wasThundering = world.isThundering();

        logDebug("Starting dramatic weather clearing in world: " + world.getName());

        new BukkitRunnable() {
            int ticksElapsed = 0;
            
            @Override
            public void run() {
                ticksElapsed++;
                double progress = (double) ticksElapsed / durationTicks;

                // Gradually transition to day
                if (startTime > 12000) { // If night, transition to day
                    long targetTime = 1000; // Early morning
                    long currentTime = startTime + (long)((targetTime - startTime) * progress);
                    world.setTime(currentTime);
                }

                // Gradually reduce storm intensity
                if (wasStorming || wasThundering) {
                    if (progress >= 0.8) { // At 80% through transition, clear weather
                        world.setStorm(false);
                        world.setThundering(false);
                    }
                }

                if (ticksElapsed >= durationTicks) {
                    logDebug("Weather clearing completed in: " + world.getName());
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static void strikeDramaticLightning(Location center, int radius) {
        if (center == null || center.getWorld() == null) return;
        
        double xOffset = (random.nextDouble() - 0.5) * radius;
        double zOffset = (random.nextDouble() - 0.5) * radius;
        
        Location strikeLocation = center.clone().add(xOffset, 0, zOffset);
        strikeLocation.setY(center.getWorld().getHighestBlockYAt(strikeLocation));
        
        center.getWorld().strikeLightning(strikeLocation);
    }

    private static void updateWorldDarkness(World world, long originalTime, int ticksElapsed, int totalTicks) {
        if (world == null) return;
        
        long targetTime = 18000; // Night time
        long currentTime = originalTime + ((targetTime - originalTime) * ticksElapsed / totalTicks);
        world.setTime(currentTime);
        
        // Debug time changes
        if (ticksElapsed % 20 == 0) { // Log every second
            logDebug("World time update: " + world.getName() + " time " + currentTime);
        }
    }

    private static int[] generateLightningTimes(int count, int maxTime) {
        int[] times = new int[count];
        int halfTime = maxTime / 2;
        
        // Reserve the last lightning strike for near the end
        times[count-1] = maxTime - 20;
        
        // Distribute the rest over the second half of the duration
        for (int i = 0; i < count-1; i++) {
            // Start at halfway point and distribute remaining strikes
            double percentage = Math.pow((double)i / (count-1), 2);
            times[i] = halfTime + (int)((maxTime - halfTime - 20) * percentage);
        }
        
        java.util.Arrays.sort(times);
        logDebug("Generated lightning times: " + java.util.Arrays.toString(times));
        return times;
    }

    /**
     * Start a dramatic lightning sequence at a location
     * 
     * @param plugin The plugin instance
     * @param center The center location for the effect
     * @param radius The radius in blocks
     * @param duration The total duration in ticks
     * @param strikes The number of lightning strikes
     * @param onComplete Callback when the sequence is complete
     */
    public static void startDramaticLightningSequence(
            RVNKQuests plugin, 
            Location center, 
            int radius, 
            int duration, 
            int strikes, 
            Consumer<Void> onComplete) {
        
        new BukkitRunnable() {
            private int count = 0;
            
            @Override
            public void run() {
                // Strike lightning at a random offset from center
                if (count < strikes) {
                    double offsetX = (Math.random() * 2 - 1) * radius;
                    double offsetZ = (Math.random() * 2 - 1) * radius;
                    
                    Location strikeLoc = center.clone().add(offsetX, 0, offsetZ);
                    center.getWorld().strikeLightningEffect(strikeLoc);
                    
                    count++;
                } else {
                    // We're done with the sequence
                    cancel();
                    if (onComplete != null) {
                        onComplete.accept(null);
                    }
                }
            }
        }.runTaskTimer(plugin, 0, duration / strikes);
    }
}
