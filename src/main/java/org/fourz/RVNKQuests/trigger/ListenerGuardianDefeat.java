package org.fourz.RVNKQuests.trigger;

import org.bukkit.Material;
import org.bukkit.entity.ElderGuardian;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;

public class ListenerGuardianDefeat implements Listener {
    private final Quest quest;
    private final ListenerGuardianAwakening guardianListener;

    public ListenerGuardianDefeat(Quest quest, ListenerGuardianAwakening guardianListener) {
        this.quest = quest;
        this.guardianListener = guardianListener;
    }

    @EventHandler
    public void onGuardianDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof ElderGuardian)) return;
        if (event.getEntity() != guardianListener.getGuardian()) return;

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("Ancient Inscription");
        meta.setAuthor("Unknown Guardian");
        meta.addPage("In depths below, a sacred site remains...\nSeek the ruins of our fallen temple...\nBut beware its defenders, forever watchful.");
        book.setItemMeta(meta);
        
        event.getDrops().clear();
        event.getDrops().add(book);
        quest.advanceState(QuestState.QUEST_ACTIVE);
    }
}
