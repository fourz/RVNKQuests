package org.fourz.RVNKQuests.reward;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.HashMap;
import java.util.Map;

public class QuestItem {
    private static final Map<String, ItemStack> questItems = new HashMap<>();

    // Initialize all quest items
    static {
        initializeQuestItems();
    }

    private static void initializeQuestItems() {
        // Add Grotsnout's journal
        questItems.put("grotsnouts_journal", createGrotsnoutJournal());
        questItems.put("grotsnouts_last_stand", createGrotSnoutsLastStandBook());
        
        // Add more quest items here following the pattern:
        // questItems.put("item_id", createItemMethod());
    }

    public static ItemStack getQuestItem(String name) {
        ItemStack item = questItems.get(name);
        if (item == null) {
            return loadFromDatabase(name);
        }
        return item.clone(); // Return a clone to prevent modifications to the original
    }

    private static ItemStack createGrotSnoutsLastStandBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        
        meta.setTitle("GrotSnout's Last Stand");
        meta.setAuthor("GrotSnout da Lost");
        
        meta.addPage(
            "GrotSnout sat alone, starin’ at da broken portal.\n\n" +
            "No fire. No gold. No boyz.\n\n" +
            "Just cold wind whisperin’, stones too dead ta burn, an’ stars dat didn’t care.\n\n" +
            "'Dis place is gonna be me zoggin’ grave,' he muttered."
        );
        
        meta.addPage(
            "He finks of da Bastions, da lootin’, da gold.\n\n" +
            "How long he gotta sit ‘ere, waitin’ fer nothin’?\n\n" +
            "'Is dere someone I can pay ta let me go?'\n\n" +
            "But dere’s no one. Just da guards."
        );
        
        meta.addPage(
            "One big an’ dark. Two rattlin’ bone-boyz. Two tusked beasts, gruntin’ in da dark.\n\n" +
            "Dey don’t know his name.\n\n" +
            "Dey don’t care he’s stuck ‘ere.\n\n" +
            "Dey just stand, watchin’, waitin’, makin’ sure no one gets through."
        );
        
        meta.addPage(
            "'Dey fink dey got me beat.'\n\n" +
            "He grinned.\n\n" +
            "'Well I ain’t stayin’ in dis zoggin’ place.'\n\n" +
            "GrotSnout’s last stand.\n\n" +
            "A stupid plan, da best kind. He’d krump ‘em. All of ‘em.\n\n" +
            "Break da gate. Let da fire come back."
        );
        
        meta.addPage(
            "If it don’t work?\n\n" +
            "At least he’d go down swingin’.\n\n" +
            "Wind howlin’.\n\n" +
            "Blade drawn.\n\n" +
            "'Let’s see who’s still standin’ when da sun comes up.'"
        );
        
        book.setItemMeta(meta);

        return book;
        
    }

    // Quest Item Creation Methods
    private static ItemStack createGrotsnoutJournal() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        
        meta.setTitle("DIS AIN'T RIGHT!");
        meta.setAuthor("GrotSnout da Lost");
        
        String[] pages = {
            "Oi, you wot's readin' dis?\n" +
            "GrotSnout 'ere, da biggest an' loudest Piglin!\n\n" +
            "Me an' da boyz went through da burny hole, but now we's stuck!\n" +
            "Too cold! No lava! No GOLD!\n\n" +
            "Da portal's all zogged up!\n" +
            "Bad gitz guard it!",

            "One big an' dark...\n" +
            "Two clanky bone-boyz...\n" +
            "Two fat tuskers!\n\n" +
            "Dey fink dey's da boss of dis place!\n" +
            "NAH! Krump 'em! Smash 'em!\n" +
            "Make da clankers rattle their last!\n\n" +
            "Show dem tuskers who's da boss!",

            "Once dey'z gone, da portal should work!\n\n" +
            "Den I'z leggin' it back home!\n" +
            "GrotSnout ain't stayin' in dis zoggin' place!\n\n" +
            "If ya krump 'em, I'll owe ya...\n" +
            "Uhhh... SEVEN—no, EIGHT whole gold coins!\n" +
            "(If I remember where I put 'em.)",

            "Now quit readin' dis!\n\n" +
            "Get out dere an' start bashin'!\n" +
            "WAAAAAGH!"
        };
        
        for (String page : pages) {
            meta.addPage(page);
        }
        
        book.setItemMeta(meta);
        return book;
    }

    // Template for adding new quest items:
    /*
    private static ItemStack createNewQuestItem() {
        ItemStack item = new ItemStack(Material.YOUR_MATERIAL);
        // Set item meta and properties
        return item;
    }
    */

    private static ItemStack loadFromDatabase(String name) {
        // TODO: Implement database retrieval
        return null;
    }
}
