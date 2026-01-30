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
import org.fourz.rvnkcore.util.log.LogManager;

public class ListenerGuardianDefeat implements Listener {
    private final Quest quest;
    private final ListenerGuardianAwakening guardianListener;
    private final LogManager logger;

    public ListenerGuardianDefeat(Quest quest, ListenerGuardianAwakening guardianListener) {
        this.quest = quest;
        this.guardianListener = guardianListener;
        this.logger = LogManager.getInstance(quest.getPlugin(), getClass());
    }

    @EventHandler
    public void onGuardianDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof ElderGuardian)) return;
        if (event.getEntity() != guardianListener.getGuardian()) return;

        logger.debug("Elder Guardian defeated, dropping Ancient Inscription");
        
        ItemStack book = createAncientInscription();
        
        event.getDrops().clear();
        event.getDrops().add(book);
        
        // Add some underwater thematic items
        event.getDrops().add(new ItemStack(Material.PRISMARINE_SHARD, 5));
        event.getDrops().add(new ItemStack(Material.PRISMARINE_CRYSTALS, 3));
        
        quest.advanceState(QuestState.QUEST_ACTIVE);
    }
    
    private ItemStack createAncientInscription() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("Ancient Underwater Inscription");
        meta.setAuthor("Unknown Depths");
        
        meta.addPage(
            "In depths below, where daylight fears to reach,\n" +
            "Lies a sacred temple, beyond mortal's breach.\n\n" +
            "Seek the blue stone, the lanterns of the sea,\n" +
            "Where ancient guardians watch eternally.\n\n" +
            "The prismarine path shall guide your way,\n" +
            "To treasures hidden from the light of day."
        );
        
        meta.addPage(
            "Dare not disturb the ancient ones who sleep,\n" +
            "For their wrath comes swift from waters deep.\n\n" +
            "Only those who brave the darkest tide,\n" +
            "Shall find the trident, with loyalty inside.\n\n" +
            "The heart of the sea awaits the bold,\n" +
            "But first, defeat those who the ruins hold."
        );
        
        meta.setLore(java.util.Arrays.asList(
            "§bAn ancient text with clues about underwater ruins",
            "§7Look for structures with prismarine and sea lanterns"
        ));
        
        book.setItemMeta(meta);
        return book;
    }
}
