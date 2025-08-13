package org.fourz.RVNKQuests.quest;

import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.util.LogManager;
import org.fourz.RVNKQuests.util.RVNKLogger;


/**
 * Base abstract class for quest implementations.
 * Provides common functionality and enforces structure for all quest implementations.
 */
public abstract class AbstractQuest implements Quest {
    protected final RVNKQuests plugin;
    protected final String questId;
    protected final String name;
    protected QuestState state;
    protected final RVNKLogger logger;
    
    /**
     * Creates a new quest with the specified ID and name.
     * 
     * @param plugin The main plugin instance
     * @param questId Unique identifier for this quest
     * @param name Display name of this quest shown to players
     */
    public AbstractQuest(RVNKQuests plugin, String questId, String name) {
        this.plugin = plugin;
        this.questId = questId;
        this.name = name;
        this.state = QuestState.NOT_STARTED;
        this.logger = LogManager.getInstance(plugin, getClass());
    }

    @Override
    public String getId() {
        return questId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public QuestState getCurrentState() {
        return state;
    }

    @Override
    public void advanceState(QuestState newState) {
        logger.debug("Advancing quest state from {} to {}", state, newState);
        this.state = newState;
        plugin.getQuestManager().updateQuestListeners(this);
    }

    @Override
    public boolean isCompleted(Player player) {
        return state == QuestState.COMPLETED;
    }

    @Override
    public RVNKQuests getPlugin() {
        return plugin;
    }
    
    /**
     * Starts the quest for the given player.
     * Handles common start logic and delegates specific behavior to onStart().
     * 
     * @param player The player starting the quest
     * @return true if the quest was successfully started
     */
    public boolean start(Player player) {
        if (player == null) {
            logger.warning("Cannot start quest: Player is null");
            return false;
        }
        
        if (state != QuestState.NOT_STARTED && state != QuestState.TRIGGER_FOUND) {
            logger.debug("Cannot start quest: Already started (current state: {})", state);
            return false;
        }
        
        logger.debug("Starting quest for player: {}", player.getName());
        boolean success = onStart(player);
        
        if (success) {
            advanceState(QuestState.QUEST_ACTIVE);
            player.sendMessage("§a[Quest Started] §f" + name);
        }
        
        return success;
    }
    
    /**
     * Completes the quest for the given player.
     * Handles common completion logic and delegates specific behavior to onComplete().
     * 
     * @param player The player completing the quest
     * @return true if the quest was successfully completed
     */
    public boolean complete(Player player) {
        if (player == null) {
            logger.warning("Cannot complete quest: Player is null");
            return false;
        }
        
        if (state == QuestState.COMPLETED) {
            logger.debug("Cannot complete quest: Already completed");
            return false;
        }
        
        logger.debug("Completing quest for player: {}", player.getName());
        boolean success = onComplete(player);
        
        if (success) {
            advanceState(QuestState.COMPLETED);
            player.sendMessage("§a[Quest Completed] §f" + name);
            
            // Announce completion to all players if configured
            if (plugin.getConfigManager().getConfig().getBoolean("quests.announce_completion", true)) {
                plugin.getServer().broadcastMessage(
                    "§6" + player.getName() + " §ehas completed the quest §6" + name + "§e!"
                );
            }
        }
        
        return success;
    }
    
    /**
     * Called when a quest is started for a player.
     * Implement quest-specific start logic in this method.
     * 
     * @param player The player starting the quest
     * @return true if start was successful, false otherwise
     */
    protected abstract boolean onStart(Player player);
    
    /**
     * Called when a quest is completed by a player.
     * Implement quest-specific completion logic in this method.
     * 
     * @param player The player completing the quest
     * @return true if completion was successful, false otherwise
     */
    protected abstract boolean onComplete(Player player);
    
    /**
     * Updates the quest progress for a player.
     * Subclasses should implement this to handle progress updates.
     * 
     * @param player The player whose quest progress is being updated
     * @return true if the quest was successfully updated
     */
    public abstract boolean update(Player player);
}
