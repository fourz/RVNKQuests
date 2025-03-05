package org.fourz.RVNKQuests.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.fourz.RVNKQuests.RVNKQuests;

import java.util.Random;
import java.util.function.Consumer;

public class EnvironmentEffects {
    private static final Random random = new Random();
    private static Debug debug;
    private static boolean initialized = false;

    private EnvironmentEffects() {} // Prevent instantiation

    public static void init(RVNKQuests plugin) {
        debug = plugin.getDebugger();
        initialized = true;
    }

    private static void logDebug(String message) {
        if (initialized && debug != null) {
            debug.debug(message);
        }
    }

    public static void startDramaticLightningSequence(JavaPlugin plugin, Location centerLocation, 
                                           int radius, int durationTicks, 
                                           int lightningStrikes, Consumer<Void> onComplete) {
        World world = centerLocation.getWorld();
        long originalTime = world.getTime();

        new BukkitRunnable() {
            int ticksElapsed = 0;
            int lightningStruck = 0;
            int[] lightningTimes = generateLightningTimes(lightningStrikes, durationTicks);

            @Override
            public void run() {
                ticksElapsed++;

                if (lightningStruck < lightningStrikes && ticksElapsed >= lightningTimes[lightningStruck]) {
                    strikeDramaticLightning(centerLocation, radius);
                    lightningStruck++;
                }

                updateWorldDarkness(world, originalTime, ticksElapsed, durationTicks);

                if (ticksElapsed >= durationTicks) {
                    if (onComplete != null) {
                        onComplete.accept(null);
                    }
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public static void weatherClearDramatic(JavaPlugin plugin, World world, int durationTicks) {
        long startTime = world.getTime();
        boolean wasStorming = world.hasStorm();
        boolean wasThundering = world.isThundering();

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
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static void strikeDramaticLightning(Location center, int radius) {
        double xOffset = (random.nextDouble() - 0.5) * radius;
        double zOffset = (random.nextDouble() - 0.5) * radius;
        
        Location strikeLocation = center.clone().add(xOffset, 0, zOffset);
        strikeLocation.setY(center.getWorld().getHighestBlockYAt(strikeLocation));
        
        center.getWorld().strikeLightning(strikeLocation);
    }

    private static void updateWorldDarkness(World world, long originalTime, int ticksElapsed, int totalTicks) {
        long targetTime = 18000; // Night time
        long currentTime = originalTime + ((targetTime - originalTime) * ticksElapsed / totalTicks);
        world.setTime(currentTime);
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

}
