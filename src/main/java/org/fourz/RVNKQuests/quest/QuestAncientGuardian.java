package org.fourz.RVNKQuests.quest;

/* Quest: Ancient Guardian
 * 
 * A challenging underwater quest that leads players through an epic
 * encounter with an Elder Guardian and ancient underwater ruins.
 * 
 * Quest Flow:
 * 1. NOT_STARTED:
 *    - Players gather near spawn in event world
 *    - Elder Guardian spawns at nearest ocean monument
 * 
 * 2. TRIGGER_FOUND:
 *    - Players must defeat the Elder Guardian
 *    - Guardian drops Ancient Inscription book
 *    - Book contains cryptic message about underwater ruins
 * 
 * 3. QUEST_ACTIVE:
 *    - Players must locate underwater ruins/structures
 *    - Identified by presence of prismarine or ancient debris
 * 
 * 4. OBJECTIVE_FOUND:
 *    - Group of armed Drowned defenders spawn
 *    - All defenders must be defeated
 * 
 * 5. COMPLETED:
 *    - Treasure chest spawns with special loot:
 *      * Enchanted trident (Loyalty III)
 *      * Heart of the Sea
 *    - Server-wide completion announcement
 */

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.trigger.*;
import org.fourz.RVNKQuests.objective.*;
import org.fourz.RVNKQuests.reward.QuestLoot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class QuestAncientGuardian extends AbstractQuest {
    // Quest-specific fields
    private final ListenerGuardianAwakening guardianListener;
    private final ListenerForgottenSite forgottenSiteListener;
    private Location guardianLocation;

    public QuestAncientGuardian(RVNKQuests plugin) {
        // Call parent constructor with quest ID and name
        super(plugin, "ancient_guardian", "Ancient Guardian");
        
        // Initialize quest-specific listeners
        this.guardianListener = new ListenerGuardianAwakening(this);
        this.forgottenSiteListener = new ListenerForgottenSite(this);
    }

    @Override
    public void initialize() {
        debugger.debug("Initializing Ancient Guardian quest");
        // Initialize the quest in NOT_STARTED state
        this.state = QuestState.NOT_STARTED;
    }

    @Override
    public void cleanup() {
        debugger.debug("Cleaning up Ancient Guardian quest");
        // Remove any remaining entities if needed
        if (guardianListener.getGuardian() != null) {
            guardianListener.getGuardian().remove();
        }
        forgottenSiteListener.getDefenders().forEach(drowned -> drowned.remove());
    }

    @Override
    public Location getStartLocation() {
        return guardianLocation; // May be null until the guardian spawns
    }

    @Override
    public String getStartTrigger() {
        return "Elder Guardian";
    }

    // Add getter/setter for guardianLocation
    public void setGuardianLocation(Location location) {
        this.guardianLocation = location;
    }

    private QuestLoot createUnderwaterLoot() {
        return () -> Arrays.asList(
            createEnchantedTrident(),
            new ItemStack(Material.HEART_OF_THE_SEA, 1),
            new ItemStack(Material.PRISMARINE_CRYSTALS, 10),
            new ItemStack(Material.NAUTILUS_SHELL, 3)
        );
    }
    
    private ItemStack createEnchantedTrident() {
        ItemStack trident = new ItemStack(Material.TRIDENT);
        trident.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.LOYALTY, 3);
        return trident;
    }

    /**
     * Implements abstract method from AbstractQuest
     * Called when a player starts this quest
     */
    @Override
    protected boolean onStart(Player player) {
        debugger.debug("Starting Ancient Guardian quest for player: " + player.getName());
        
        // Send the player a quest start message
        player.sendMessage("§b[Ancient Guardian] §fYou've discovered an ancient underwater mystery!");
        player.sendMessage("§b[Ancient Guardian] §fDefeat the Elder Guardian to uncover its secrets.");
        
        return true;
    }

    /**
     * Implements abstract method from AbstractQuest
     * Called when a player completes this quest
     */
    @Override
    protected boolean onComplete(Player player) {
        debugger.debug("Completing Ancient Guardian quest for player: " + player.getName());
        
        // Send completion message
        player.sendMessage("§b[Ancient Guardian] §fYou've defeated the ancient defenders and claimed their treasure!");
        
        // Record the achievement globally
        plugin.getServer().broadcastMessage(
            "§b[Ancient Guardian] §f" + player.getName() + " has uncovered the secrets of the ancient underwater ruins!"
        );
        
        return true;
    }

    /**
     * Implements abstract method from AbstractQuest
     * Called to update quest progress for a player
     */
    @Override
    public boolean update(Player player) {
        // This quest uses event-based progression rather than manual updates
        debugger.debug("Update called for Ancient Guardian quest, but this quest uses event-based progression");
        return false;
    }

    /**
     * Manages the quest's state transitions and associated listeners.
     * Each state has specific listeners that handle the quest mechanics:
     * - GuardianAwakening: Spawns the Elder Guardian when players gather
     * - GuardianDefeat: Handles guardian death and quest book drop
     * - ForgottenSite: Detects when players find underwater ruins
     * - ForgottenSiteDefeated: Manages defender deaths and final reward
     */
    @Override
    public List<Listener> createListenersForState(QuestState state) {
        List<Listener> listeners = new ArrayList<>();
        
        switch (state) {
            case NOT_STARTED:
                listeners.add(guardianListener);
                break;
            case TRIGGER_FOUND:
                listeners.add(new ListenerGuardianDefeat(this, guardianListener));
                break;
            case QUEST_ACTIVE:
                listeners.add(forgottenSiteListener);
                break;
            case OBJECTIVE_FOUND:
                listeners.add(new ListenerForgottenSiteDefeated(this, forgottenSiteListener, createUnderwaterLoot()));
                break;
        }
        
        return listeners;
    }
}
