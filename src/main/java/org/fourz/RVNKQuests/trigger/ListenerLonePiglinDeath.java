package org.fourz.RVNKQuests.trigger;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.reward.QuestItem;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Listener that handles the death of the quest piglin.
 * This supports the "combat path" of certain quests by detecting when players 
 * kill the quest piglin and dropping special items like journals.
 */
public class ListenerLonePiglinDeath implements Listener {
    private final Quest quest;
    private final ListenerLonePiglinTrigger lonePiglinListener;
    private final LogManager logger;
    private final ItemStack questItem;

    /**
     * Creates a listener with the default quest item (GrotSnout's journal)
     * 
     * @param quest The quest this listener belongs to
     * @param lonePiglinListener The trigger that spawns and tracks the piglin
     */
    public ListenerLonePiglinDeath(Quest quest, ListenerLonePiglinTrigger lonePiglinListener) {
    this(quest, lonePiglinListener, QuestItem.getQuestItem("grotsnouts_journal"));
    }

    /**
     * Creates a listener with a custom quest item to drop
     * 
     * @param quest The quest this listener belongs to
     * @param lonePiglinListener The trigger that spawns and tracks the piglin
     * @param questItem The item to drop when the piglin is killed
     */
    public ListenerLonePiglinDeath(Quest quest, ListenerLonePiglinTrigger lonePiglinListener, ItemStack questItem) {
    this.quest = quest;
    this.lonePiglinListener = lonePiglinListener;
    this.questItem = questItem;
    this.logger = LogManager.getInstance(quest.getPlugin(), getClass());
    }

    @EventHandler
    public void onPiglinDeath(EntityDeathEvent event) {
        // Verify this is our specific quest piglin using the listener's tracking
        if (!lonePiglinListener.isQuestPiglin(event.getEntity())) {
            logger.debug("Piglin death: not the quest Piglin");
            return;
        }

        logger.debug("Quest Piglin died, preparing to drop journal");
        if (questItem == null) {
            logger.warning("Failed to retrieve quest item!");
            return;
        }

        // Replace normal drops with our quest item
        logger.debug("Clearing existing drops and adding quest journal");
        event.getDrops().clear();
        event.getDrops().add(questItem);

        // Advance the quest to the active state since player has chosen the combat path
        logger.debug("Advancing quest state to QUEST_ACTIVE");
        quest.advanceState(QuestState.QUEST_ACTIVE);
    }
}
