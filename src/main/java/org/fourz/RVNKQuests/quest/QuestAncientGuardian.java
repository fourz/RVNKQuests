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
 * 4. OBJECTIVE_COMPLETE:
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
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.trigger.*;
import org.fourz.RVNKQuests.objective.*;

import java.util.ArrayList;
import java.util.List;

public class QuestAncientGuardian implements Quest {
    private final RVNKQuests plugin;
    private QuestState currentState = QuestState.NOT_STARTED;
    private final ListenerGuardianAwakening guardianListener;
    private final ListenerForgottenSite forgottenSiteListener;

    public QuestAncientGuardian(RVNKQuests plugin) {
        this.plugin = plugin;
        this.guardianListener = new ListenerGuardianAwakening(this);
        this.forgottenSiteListener = new ListenerForgottenSite(this);
    }

    @Override
    public String getId() {
        return "ancient_guardian";
    }

    @Override
    public String getName() {
        return "Ancient Guardian";
    }

    @Override
    public void initialize() {
        // No initialization needed
    }

    @Override
    public void cleanup() {
        // Remove any remaining entities if needed
    }

    @Override
    public boolean isCompleted(Player player) {
        return currentState == QuestState.COMPLETED;
    }

    @Override
    public QuestState getCurrentState() {
        return currentState;
    }

    @Override
    public void advanceState(QuestState newState) {
        this.currentState = newState;
        plugin.getQuestManager().updateQuestListeners(this);
    }

    @Override
    public Location getLecternLocation() {
        return null; // Not used in this quest
    }

    @Override
    public RVNKQuests getPlugin() {
        return plugin;
    }

    /**
     * Manages the quest's state transitions and associated listeners.
     * Each state has specific listeners that handle the quest mechanics:
     * - GuardianAwakening: Spawns the Elder Guardian when players gather
     * - GuardianDefeat: Handles guardian death and quest book drop
     * - ForgottenSite: Detects when players find underwater ruins
     * - ForgottenSiteDefeated: Manages defender deaths and final reward
     */
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
                listeners.add(new ListenerForgottenSiteDefeated(this, forgottenSiteListener));
                break;
        }
        
        return listeners;
    }
}
