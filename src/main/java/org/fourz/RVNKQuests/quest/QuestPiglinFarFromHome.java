package org.fourz.RVNKQuests.quest;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.trigger.ListenerLonePiglinTrigger;
import org.fourz.RVNKQuests.objective.*;
import org.fourz.RVNKQuests.reward.QuestLoot;
import org.fourz.RVNKQuests.lore.LoreDatabase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class QuestPiglinFarFromHome extends AbstractQuest {
    private ListenerLonePiglinTrigger lonePiglinTrigger;
    private ListenerEncounterPortal portalListener;
    private ListenerPiglinEscort piglinEscortListener;
    private Location spawnLocation;
    
    // Track which path the player has chosen
    private final Map<UUID, QuestPath> playerPaths = new HashMap<>();
    
    // Constants for the quest
    private static final String PIGLIN_NAME = "GrotSnout da Lost";
    
    public enum QuestPath {
        COMBAT_PATH,
        ESCORT_PATH
    }

    public QuestPiglinFarFromHome(RVNKQuests plugin) {
        // Call the parent constructor with the plugin, quest ID, and display name
        super(plugin, "piglin_far_from_home", "Piglin Far From Home");
    }

    @Override
    public void initialize() {
        debugger.debug("Initializing Piglin Far From Home quest");
        
        // Create the listener with custom location parameters from config
        String worldName = getPlugin().getConfigManager().getConfig()
                .getString("quests.piglin_far_from_home.world", "event");
        double spawnRadius = getPlugin().getConfigManager().getConfig()
                .getDouble("quests.piglin_far_from_home.spawn_radius", 30.0);
        
        // Initialize the ListenerLonePiglinTrigger with specific world and radius
        this.lonePiglinTrigger = new ListenerLonePiglinTrigger(this, getPlugin(), worldName, null, spawnRadius);
        this.lonePiglinTrigger.setPiglinName(PIGLIN_NAME);

        // Set up portal encounter mobs
        Map<EntityType, Integer> portalMobs = new HashMap<>();
        portalMobs.put(EntityType.WITHER_SKELETON, 1);
        portalMobs.put(EntityType.SKELETON, 2);
        portalMobs.put(EntityType.HOGLIN, 2);        
        this.portalListener = new ListenerEncounterPortal(this, portalMobs);
        
        // Initialize the piglin escort listener with ListenerLonePiglinTrigger
        this.piglinEscortListener = new ListenerPiglinEscort(this, lonePiglinTrigger);
        
        debugger.debug("Piglin Far From Home quest initialized");
    }

    @Override
    public void cleanup() {
        debugger.debug("Cleaning up Piglin Far From Home quest");
        
        // Clean up any remaining entities and listeners
        cleanupPortalPrevention();
        
        if (lonePiglinTrigger != null) {
            lonePiglinTrigger.cleanup();
        }
        
        if (piglinEscortListener != null) {
            piglinEscortListener.cleanup();
        }
        
        playerPaths.clear();
    }

    /**
     * Cleans up the portal prevention listener if it exists
     */
    private void cleanupPortalPrevention() {
        if (portalListener != null && portalListener.getPortalPreventionListener() != null) {
            portalListener.getPortalPreventionListener().unregister();
        }
    }

    @Override
    public Location getStartLocation() {
        return spawnLocation; // May be null until the piglin spawns
    }

    @Override
    public String getStartTrigger() {
        return PIGLIN_NAME;
    }

    // Add setter for spawnLocation
    public void setSpawnLocation(Location location) {
        this.spawnLocation = location;
    }

    /**
     * Creates the standard portal loot for the combat path
     */
    private QuestLoot createPortalLoot() {
        return () -> Arrays.asList(
            new ItemStack(Material.GOLDEN_APPLE, 3),
            new ItemStack(Material.NETHERITE_SCRAP, 1),
            new ItemStack(Material.DIAMOND, 5),
            new ItemStack(Material.EMERALD, 10)            
        );
    }
    
    /**
     * Creates special loot for the escort path (when GrotSnout reaches the portal)
     */
    private QuestLoot createSpecialLoot() {
        return () -> {
            List<ItemStack> items = new ArrayList<>(createPortalLoot().generateLoot());
            // Add GrotSnout's special gratitude item
            ItemStack gratitude = createGrotsnoutGratitudeItem();
            items.add(gratitude);
            return items;
        };
    }
    
    /**
     * Creates GrotSnout's special gratitude item
     */
    private ItemStack createGrotsnoutGratitudeItem() {
        ItemStack item = new ItemStack(Material.GILDED_BLACKSTONE, 1);
        // Set custom meta for the item - this would be expanded in a real implementation
        return item;
    }
    
    /**
     * Creates GrotSnout's journal book item
     */
    private ItemStack createGrotsnoutJournal() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        
        if (meta != null) {
            meta.setTitle("GrotSnout's Last Stand");
            meta.setAuthor("GrotSnout da Lost");
            
            meta.addPage("GrotSnout sat alone, starin' at da broken portal.\n\nNo fire. No gold. No herd.\n\nJust cold wind whisperin', stones too dead ta burn, an' stars dat didn't care.\n\n'Dis place is gonna be me stinkin' grave,' he muttered.");
            meta.addPage("He thinks of da Bastions, da lootin', da shiny gold.\n\nHow long he gotta sit 'ere, waitin' for nothin'?\n\n'Is dere someone I can trade wid to let me go?'\n\nBut dere's no one. Just da guards.");
            meta.addPage("One big an' dark. Two rattlin' bone-walkers. Two tusked beasts, gruntin' in da dark.\n\nDey guard da broken portal way up high, where clouds touch da stone.\n\nDey don't know his name.\n\nDey don't care he's stuck down 'ere.");
            meta.addPage("'Dey think dey got me beat.'\n\nHe grinned.\n\n'Well I ain't stayin' in dis rotten place.'\n\nGrotSnout's last stand.\n\nA stupid plan, da best kind. He'd smash 'em. All of 'em.\n\nFix da gate. Let da fire come back.");
            meta.addPage("If it don't work?\n\nAt least he'd go down swingin'.\n\nWind howlin' on da clifftops.\n\nBlade drawn.\n\n'Let's see who's still standin' when da sun burns bright!'");
            
            book.setItemMeta(meta);
        }
        
        return book;
    }
    
    /**
     * Set the quest path for a player
     */
    public void setPlayerPath(Player player, QuestPath path) {
        playerPaths.put(player.getUniqueId(), path);
        debugger.debug("Player " + player.getName() + " has chosen the " + path + " path");
    }
    
    /**
     * Get the quest path for a player
     */
    public QuestPath getPlayerPath(Player player) {
        return playerPaths.getOrDefault(player.getUniqueId(), QuestPath.COMBAT_PATH);
    }

    /**
     * Checks if any player has chosen the escort path
     */
    private boolean hasActiveEscorter() {
        return playerPaths.containsValue(QuestPath.ESCORT_PATH) && 
               piglinEscortListener.getActiveEscorter() != null;
    }

    @Override
    public List<Listener> createListenersForState(QuestState state) {
        List<Listener> listeners = new ArrayList<>();
        
        switch (state) {
            case NOT_STARTED:
                listeners.add(lonePiglinTrigger);
                break;
                
            case TRIGGER_FOUND:
                // Add both path options - players can either kill the piglin or escort it
                listeners.add(new ListenerLonePiglinDeath(this, lonePiglinTrigger, createGrotsnoutJournal()));
                listeners.add(piglinEscortListener);
                break;
                
            case QUEST_ACTIVE:
                // Add portal listener for detecting the portal location
                listeners.add(portalListener);
                
                // IMPORTANT FIX: Keep piglin escort listener active if escort path is chosen
                // This ensures the piglin continues to follow the player
                if (hasActiveEscorter()) {
                    debugger.debug("Maintaining piglin escort listener for active escort path");
                    listeners.add(piglinEscortListener);
                }
                break;
                
            case OBJECTIVE_FOUND:
                // The combat path just needs to defeat the portal guards
                listeners.add(new ListenerEncounterPortalDefeated(this, portalListener, createPortalLoot()));
                
                // IMPORTANT FIX: Keep escort listener active during this state too
                if (hasActiveEscorter()) {
                    listeners.add(piglinEscortListener);
                    // The escort path also needs to track if GrotSnout reaches the portal
                    listeners.add(new ListenerPiglinPortalReunion(this, piglinEscortListener, portalListener, createSpecialLoot()));
                }
                break;
                
            //case OBJECTIVE_COMPLETE:
            case COMPLETED:
                // Keep minimal listeners for completed quests if needed
                break;
        }
        
        return listeners;
    }
    
    @Override
    protected boolean onStart(Player player) {
        debugger.debug("Starting Piglin Far From Home quest for " + player.getName());
        
        // Record the discovery in the lore database
        LoreDatabase loreDb = getPlugin().getLoreDatabase();
        if (loreDb != null && spawnLocation != null) {
            loreDb.recordDiscovery(
                "lost_piglin",
                spawnLocation.getWorld().getName(),
                spawnLocation.getBlockX(),
                spawnLocation.getBlockY(),
                spawnLocation.getBlockZ(),
                "A lost piglin named " + PIGLIN_NAME + " was discovered far from the Nether."
            );
            debugger.debug("Recorded lost piglin discovery in lore database");
        }
        
        return true;
    }
    
    @Override
    protected boolean onComplete(Player player) {
        debugger.debug("Completing Piglin Far From Home quest for " + player.getName());
        
        // Cleanup quest-specific resources
        cleanupPortalPrevention();
        
        // Award additional rewards if needed beyond the loot
        player.giveExp(500); // Give player some XP as completion reward
        
        // Record quest completion in lore database
        QuestPath path = getPlayerPath(player);
        LoreDatabase loreDb = getPlugin().getLoreDatabase();
        if (loreDb != null && portalListener != null) {
            String description;
            if (path == QuestPath.ESCORT_PATH) {
                description = "Helped " + PIGLIN_NAME + " return home through a restored Nether portal.";
            } else {
                description = "Defeated the guardians of a Nether portal after finding the journal of " + PIGLIN_NAME + ".";
            }
            
            // Record the completion at the portal location
            Location portalLoc = portalListener.getPortalLocation();
            if (portalLoc != null) {
                loreDb.recordDiscovery(
                    "quest_completion",
                    portalLoc.getWorld().getName(),
                    portalLoc.getBlockX(),
                    portalLoc.getBlockY(),
                    portalLoc.getBlockZ(),
                    description
                );
                debugger.debug("Recorded quest completion in lore database");
            }
        }
        
        return true;
    }
    
    @Override
    public boolean update(Player player) {
        // This quest doesn't need periodic updates, but we could implement progress tracking here
        debugger.debug("Update requested for player: " + player.getName() + ", state: " + getCurrentState());
        return false; // No updates performed
    }
}
