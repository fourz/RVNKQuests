package org.fourz.RVNKQuests.reward;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory class for quest-related items and artifacts.
 * 
 * This class provides a centralized registry for all special items
 * used in quests, including:
 * - Quest journals and books
 * - Special artifacts and quest triggers
 * - Unique reward items
 * 
 * Items are cached after creation for performance and consistency.
 * Always use getQuestItem() to retrieve items to ensure they are properly cloned.
 */
public class QuestItem {
    /** Registry of all quest items indexed by identifier */
    private static final Map<String, ItemStack> questItems = new HashMap<>();

    // Initialize all quest items at class load time
    static {
        initializeQuestItems();
    }

    /**
     * Initializes and registers all quest items to the central registry
     */
    private static void initializeQuestItems() {
        // Add quest journal items
        questItems.put("grotsnouts_journal", createGrotsnoutJournal());
        questItems.put("grotsnouts_last_stand", createGrotSnoutsLastStandBook());
        
        // Register more quest items following this pattern:
        // questItems.put("item_id", createItemMethod());
    }

    /**
     * Retrieves a quest item by its identifier
     * 
     * @param name The identifier of the quest item
     * @return A clone of the requested item, or null if not found
     */
    public static ItemStack getQuestItem(String name) {
        ItemStack item = questItems.get(name);
        if (item == null) {
            return loadFromDatabase(name);
        }
        return item.clone(); // Return a clone to prevent modifications to the original
    }

    /**
     * Creates GrotSnout's last stand journal - a quest item for the Piglin Far From Home quest
     * This book contains the final entry describing GrotSnout's plan to fight the portal guardians
     * 
     * @return The written book item
     */
    private static ItemStack createGrotSnoutsLastStandBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        
        meta.setTitle("GrotSnout's Last Stand");
        meta.setAuthor("GrotSnout da Lost");
        
        // Add narrative pages that tell GrotSnout's story
        meta.addPage(
            "GrotSnout sat alone, starin' at da broken portal.\n\n" +
            "No fire. No gold. No boyz.\n\n" +
            "Just cold wind whisperin', stones too dead ta burn, an' stars dat didn't care.\n\n" +
            "'Dis place is gonna be me zoggin' grave,' he muttered."
        );
        
        // Additional pages...
        meta.addPage(
            "He finks of da Bastions, da lootin', da gold.\n\n" +
            "How long he gotta sit 'ere, waitin' fer nothin'?\n\n" +
            "'Is dere someone I can pay ta let me go?'\n\n" +
            "But dere's no one. Just da guards."
        );
        
        meta.addPage(
            "One big an' dark. Two rattlin' bone-boyz. Two tusked beasts, gruntin' in da dark.\n\n" +
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

    /**
     * Creates GrotSnout's journal - the main quest trigger for Piglin Far From Home
     * This book contains clues about the quest objectives and backstory
     * 
     * @return The written book item
     */
    private static ItemStack createGrotsnoutJournal() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        
        meta.setTitle("DIS AIN'T RIGHT!");
        meta.setAuthor("GrotSnout da Lost");
        
        // Create the journal with several pages of quest information
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

    /**
     * Attempts to load a quest item from the database if not found in memory
     * 
     * @param name The identifier of the quest item
     * @return The loaded item or null if not found
     */
    private static ItemStack loadFromDatabase(String name) {
        // TODO: Implement database retrieval
        return null;
    }
}
