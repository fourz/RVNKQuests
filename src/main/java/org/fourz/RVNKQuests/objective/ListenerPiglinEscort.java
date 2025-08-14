package org.fourz.RVNKQuests.objective;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestPiglinFarFromHome;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.trigger.ListenerLonePiglinTrigger;
import org.fourz.RVNKQuests.util.log.LogManager;
import org.fourz.RVNKQuests.util.log.FZLogger;
import org.fourz.RVNKQuests.util.EntityFollow;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener for the escort path of the Piglin Far From Home quest.
 * Handles piglin following mechanics and interaction.
 */
public class ListenerPiglinEscort implements Listener {
    private final Quest quest;
    private final ListenerLonePiglinTrigger piglinTrigger;
    private final FZLogger logger;
    private Player activeEscorter = null;
    
    // Add cooldown to prevent double toggling
    private static final long INTERACTION_COOLDOWN = 1000; // 1 second cooldown in milliseconds
    private final Map<UUID, Long> lastInteractionTime = new HashMap<>();
    
    // Entity follow handler
    private final EntityFollow entityFollow;
    
    private boolean firstInteraction = true;
    private boolean pathChosen = false;

    public ListenerPiglinEscort(Quest quest, ListenerLonePiglinTrigger piglinTrigger) {
        this.quest = quest;
        this.piglinTrigger = piglinTrigger;
        this.logger = LogManager.getInstance(quest.getPlugin(), getClass());
        
        // Initialize EntityFollow with smarter settings
        this.entityFollow = new EntityFollow(quest.getPlugin())
            .withDistances(3.0, 20.0)     // Configure follow and max distances
            .withSpeed(1.0)               // Configure follow speed
            .withTaskDelay(5)             // Configure task delay 
            .withJumpVelocity(0.4)        // Configure jump height
            .useNavigator(true);          // Use Minecraft's built-in pathfinding
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();
        
        // Check if the entity is our quest piglin
        if (!piglinTrigger.isQuestPiglin(entity)) {
            return;
        }
        
        logger.debug("Player {} interacted with quest piglin", player.getName());
        
        // Check cooldown to prevent double toggling
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        long lastTime = lastInteractionTime.getOrDefault(playerUUID, 0L);
        
        if (currentTime - lastTime < INTERACTION_COOLDOWN) {
            logger.debug("Interaction cooldown active for player {}, ignoring event", player.getName());
            event.setCancelled(true);
            return;
        }
        
        // Update last interaction time
        lastInteractionTime.put(playerUUID, currentTime);
        
        // First interaction setup - set player's path in the quest
        if (!pathChosen) {
            if (quest instanceof QuestPiglinFarFromHome) {
                ((QuestPiglinFarFromHome) quest).setPlayerPath(player, QuestPiglinFarFromHome.QuestPath.ESCORT_PATH);
                setActiveEscorter(player);
                pathChosen = true;
                
                // Advance the quest state
                quest.advanceState(QuestState.QUEST_ACTIVE);
            }
        }
        
        // Toggle following behavior
        if (activeEscorter != null && player.equals(activeEscorter)) {
            toggleFollowing(player);
        } else if (activeEscorter == null) {
            // No active escorter yet, set this player
            setActiveEscorter(player);
            toggleFollowing(player);
        } else {
            // Another player is already escorting
            player.sendMessage(ChatColor.RED + "This piglin is already being escorted by " + activeEscorter.getName());
        }
    }
    
    /**
     * Toggle the following behavior of the piglin
     */
    private void toggleFollowing(Player player) {
        boolean wasFollowing = entityFollow.isFollowing();
        
        if (wasFollowing) {
            // Stop following
            entityFollow.stop();
            player.sendMessage(ChatColor.GOLD + "The piglin has stopped following you. Right-click again to resume.");
        } else {
            // Start following
            Piglin piglin = getEscortPiglin();
            if (piglin != null) {
                entityFollow.start(piglin, player);
                player.sendMessage(ChatColor.GOLD + "The piglin is now following you. Right-click again to make it wait.");
                
                if (firstInteraction) {
                    player.sendMessage(ChatColor.GOLD + "GrotSnout will try to follow you around obstacles. If you lose him, go back to help him find his way.");
                }
                
                // First time interactions get extra dialog
                if (firstInteraction) {
                    firstInteraction = false;
                    sendFirstTimeDialog(player);
                }
            }
        }
    }
    
    /**
     * Sends dialog messages for first interaction
     */
    private void sendFirstTimeDialog(Player player) {
        // Schedule messages with slight delays for readability
        new BukkitRunnable() {
            @Override
            public void run() {
                player.sendMessage(ChatColor.GOLD + "GrotSnout grunts: \"You got gold? Help GrotSnout get home?\"");
            }
        }.runTaskLater(quest.getPlugin(), 20L);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                player.sendMessage(ChatColor.GOLD + "You notice he keeps looking towards the mountains in the distance.");
            }
        }.runTaskLater(quest.getPlugin(), 60L);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                player.sendMessage(ChatColor.GRAY + "Hint: Lead GrotSnout to find a Nether portal. You can toggle his following by right-clicking him.");
            }
        }.runTaskLater(quest.getPlugin(), 100L);
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // If no active escort is happening or following is off, ignore
        if (activeEscorter == null || !event.getPlayer().equals(activeEscorter) || !entityFollow.isFollowing()) {
            return;
        }
        
        // Check if player has moved far enough to matter
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && 
                           from.getBlockY() == to.getBlockY() && 
                           from.getBlockZ() == to.getBlockZ())) {
            return;
        }
        
        // EntityFollow class handles the following logic when movement is detected
    }
    
    /**
     * Checks if a player is currently escorting the piglin
     * @param player The player to check
     * @return true if this player is escorting the piglin
     */
    public boolean isEscorting(Player player) {
        return player != null && player.equals(activeEscorter) && 
               piglinTrigger.getSpawnedPiglin() != null && 
               !piglinTrigger.getSpawnedPiglin().isDead();
    }
    
    /**
     * Gets the piglin being escorted
     * @return The piglin entity or null if none
     */
    public Piglin getEscortPiglin() {
        Entity piglin = piglinTrigger.getSpawnedPiglin();
        return (piglin instanceof Piglin) ? (Piglin)piglin : null;
    }
    
    /**
     * Sets the active escorter
     * @param player The player escorting the piglin
     */
    private void setActiveEscorter(Player player) {
        if (activeEscorter != null && !activeEscorter.equals(player)) {
            activeEscorter.sendMessage(ChatColor.RED + "Someone else is now escorting the piglin.");
        }
        activeEscorter = player;
        logger.debug("Set active escorter to: {}", (player != null ? player.getName() : "null"));
    }
    
    /**
     * Gets the player who is currently escorting the piglin
     * @return The escorting player or null if none
     */
    public Player getActiveEscorter() {
        return activeEscorter;
    }
    
    /**
     * Checks if the piglin is currently following the player
     * @return true if following is active
     */
    public boolean isFollowing() {
        return entityFollow.isFollowing();
    }
    
    /**
     * Cleans up any resources used by this listener
     */
    public void cleanup() {
        logger.debug("Cleaning up PiglinEscort listener");
        activeEscorter = null;
        entityFollow.cleanup();
    }
}
