package org.fourz.RVNKQuests.util;

import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.List;
import java.util.Arrays;

public class NameGenerator {
    private static final Map<EntityType, List<String>> namesByType = new HashMap<>();
    private static final Random random = new Random();
    
    static {
        // Initialize name lists for each entity type
        namesByType.put(EntityType.ZOMBIE, Arrays.asList(
            "Lost Miner",
            "Plagueborn",
            "Ravaged Settler", 
            "Cursed Vagabond",
            "Forsaken Explorer"
        ));
        
        namesByType.put(EntityType.SKELETON, Arrays.asList(
            "Bone Archer of Ravenwood",
            "Fallen Legionnaire",
            "Hollow Watchman",
            "Crypt Guardian",
            "Wraith of the Old War"
        ));
        
        namesByType.put(EntityType.CREEPER, Arrays.asList(
            "Emerald Wraith",
            "Silent Menace",
            "The Lurking Ruin",
            "Whispering Stalker",
            "Verdant Terror"
        ));
        
        namesByType.put(EntityType.SPIDER, Arrays.asList(
            "Venomous Broodling",
            "Cave Stalker",
            "Silkweaver Horror",
            "The Eight-Legged Bane",
            "Shadow Spinner"
        ));
        
        namesByType.put(EntityType.ENDERMAN, Arrays.asList(
            "Voidwalker",
            "Ebon Gazer",
            "Wandering Phantom",
            "Riftborn Seer",
            "The Vanishing Herald"
        ));
        
        namesByType.put(EntityType.WITCH, Arrays.asList(
            "Hag of the Withering Woods",
            "The Brewmistress",
            "Coven Elder",
            "Hexborn Alchemist",
            "Shadow Enchantress"
        ));
        
        namesByType.put(EntityType.BLAZE, Arrays.asList(
            "Infernal Sentinel",
            "Ember Warden",
            "The Smoldering Shade",
            "Flameborn Revenant",
            "Guardian of the Pyre"
        ));
        
        namesByType.put(EntityType.GHAST, Arrays.asList(
            "Wailing Specter",
            "Phantom of the Nether",
            "Echoing Doom",
            "Netherbound Lament",
            "Sorrowful Shade"
        ));
        
        namesByType.put(EntityType.WITHER_SKELETON, Arrays.asList(
            "Dreadknight of the Nether",
            "Ebonblade Warrior",
            "Cursed Boneguard",
            "Void-Touched Raider",
            "Ashen Executioner"
        ));
        
        namesByType.put(EntityType.PIGLIN_BRUTE, Arrays.asList(
            "Bloodsworn Marauder",
            "The Gilded Ravager",
            "Barbarian of the Bastion",
            "Goldhoarder's Wrath",
            "Oathbreaker of the Pits"
        ));

        namesByType.put(EntityType.PIGLIN, Arrays.asList(
            "Goldseeker",
            "The Gilded Wanderer",
            "Barterer of the Bastion",
            "Goldhoarder's Acolyte",
            "Oathkeeper of the Pits"
        ));

        namesByType.put(EntityType.HOGLIN, Arrays.asList(
            "Ravaging Beast",
            "The Crimson Brute",
            "The Tusked Terror",
            "Bloodbound Marauder",
            "The Feral Boar"
        ));        
    }
    
    /**
     * Generates a thematic name for the given entity type
     * @param entityType The type of entity to name
     * @return A randomly selected name fitting for the entity type
     */
    public static String generateMobName(EntityType entityType) {
        // on a random chance of 1/5, generate a generic name   
        if (random.nextInt(5) == 0) {
            return generateGenericName(entityType);
        }
        if (namesByType.containsKey(entityType)) {
            List<String> names = namesByType.get(entityType);
            return names.get(random.nextInt(names.size()));
        } else {
            // Generate a generic name for entities without specific names
            return generateGenericName(entityType);
        }
    }
    
    /**
     * Generates a generic name for entity types that don't have specific names
     * @param entityType The type of entity to name
     * @return A generated name based on the entity type
     */
    private static String generateGenericName(EntityType entityType) {
        String entityTypeName = formatEntityTypeName(entityType.name());
        
        List<String> prefixes = Arrays.asList(
            "Cursed", "Vengeful", "Super", "Ultra", "Mega",
            "Meh", "Lame", "Forsaken", "Blustering", "Epic"
        );
        
        List<String> suffixes = Arrays.asList(
            "of the Deep", "of the Nether", "of Jelly", "of the Plant Lands",
            "of Fun", "of The Fish Men", "(no really though)", "of the End", "of the Overworld", "of the Nether"
        );
        
        String prefix = prefixes.get(random.nextInt(prefixes.size()));
        
        // 80% chance to add a suffix
        if (random.nextInt(5) != 0) {
            String suffix = suffixes.get(random.nextInt(suffixes.size()));
            return prefix + " " + entityTypeName + " " + suffix;
        } else {
            return prefix + " " + entityTypeName;
        }
    }

    private static String generateLedgendaryName(EntityType entityType) {
        String entityTypeName = formatEntityTypeName(entityType.name());
        
        List<String> prefixes = Arrays.asList(
            "Ancient", "Cursed", "Vengeful", "Corrupted", "Legendary",
            "Mythical", "Eldritch", "Forsaken", "Haunted", "Mysterious"
        );
        
        List<String> suffixes = Arrays.asList(
            "of the Raven", "of Doom", "of the Void", "of Terror",
            "of Shadows", "of the Abyss", "of the Ancient World", "of Legend", "the Undying", "the Eternal"
        );
        
        String prefix = prefixes.get(random.nextInt(prefixes.size()));
        
        // 50% chance to add a suffix
        if (random.nextBoolean()) {
            String suffix = suffixes.get(random.nextInt(suffixes.size()));
            return prefix + " " + entityTypeName + " " + suffix;
        } else {
            return prefix + " " + entityTypeName;
        }
    }

    
    /**
     * Formats entity type name for display
     * @param entityTypeName The entity type enum name
     * @return A formatted entity name
     */
    private static String formatEntityTypeName(String entityTypeName) {
        String[] words = entityTypeName.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                result.append(word.substring(1));
                result.append(" ");
            }
        }
        
        return result.toString().trim();
    }
}
