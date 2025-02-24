package org.fourz.RVNKQuests.trigger;

import org.bukkit.Material;
import org.bukkit.entity.Piglin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;

public class ListenerLonePiglinDeath implements Listener {
    private final Quest quest;
    private final ListenerLonePiglin lonePiglinListener;

    public ListenerLonePiglinDeath(Quest quest, ListenerLonePiglin lonePiglinListener) {
        this.quest = quest;
        this.lonePiglinListener = lonePiglinListener;
    }

    @EventHandler
    public void onPiglinDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Piglin)) return;
        if (event.getEntity() != lonePiglinListener.getLonePiglin()) return;

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("Portal Mysteries");
        meta.setAuthor("Lost Piglin");
        meta.addPage("Our portal... high in hills... others wait... help us return...");
        book.setItemMeta(meta);
        
        event.getDrops().clear();
        event.getDrops().add(book);
        quest.advanceState(QuestState.QUEST_ACTIVE);
    }
}
