package org.fourz.RVNKQuests.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player completes a quest.
 * Other plugins (e.g., RVNKLore) can listen for this to trigger
 * cross-plugin integrations like lore discoveries.
 */
public class QuestCompleteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String questId;
    private final String questName;

    public QuestCompleteEvent(Player player, String questId, String questName) {
        this.player = player;
        this.questId = questId;
        this.questName = questName;
    }

    public Player getPlayer() {
        return player;
    }

    public String getQuestId() {
        return questId;
    }

    public String getQuestName() {
        return questName;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
