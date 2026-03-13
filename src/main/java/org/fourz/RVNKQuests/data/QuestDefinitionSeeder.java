package org.fourz.RVNKQuests.data;

import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.dto.*;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;

/**
 * Seeds the 3 original quest definitions into the repository on first run.
 * Called during onEnable() before QuestManager.initializeQuests().
 *
 * <p>Quest IDs remain identical to the hardcoded versions so existing
 * player progress is preserved.</p>
 */
public class QuestDefinitionSeeder {

    private final RVNKQuests plugin;
    private final LogManager logger;

    public QuestDefinitionSeeder(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "QuestDefinitionSeeder");
    }

    /**
     * Gets the configured default world for quest definitions.
     */
    private String getDefaultWorld() {
        return plugin.getConfigManager().getConfig()
            .getString("quests.default_world", "event");
    }

    /**
     * Seeds quest definitions if they don't already exist.
     * Safe to call on every startup — skips existing quests.
     */
    public void seedIfNeeded() {
        IQuestRepository repo = plugin.getQuestRepository();
        if (repo == null) {
            logger.warning("Quest repository not available — skipping seed");
            return;
        }

        try {
            long count = repo.count().join();
            if (count > 0) {
                logger.debug("Quest definitions already exist (" + count + ") — skipping seed");
                return;
            }

            seed(repo);
        } catch (Exception e) {
            logger.error("Failed to seed quest definitions", e);
        }
    }

    /**
     * Force re-seeds all quest definitions, overwriting existing ones.
     * Useful after changing quests.default_world or other config.
     *
     * @return Number of quests seeded
     */
    public int reseed() {
        IQuestRepository repo = plugin.getQuestRepository();
        if (repo == null) {
            logger.warning("Quest repository not available — cannot reseed");
            return 0;
        }

        try {
            return seed(repo);
        } catch (Exception e) {
            logger.error("Failed to reseed quest definitions", e);
            return 0;
        }
    }

    private int seed(IQuestRepository repo) {
        logger.info("Seeding quest definitions (world: " + getDefaultWorld() + ")...");
        int seeded = 0;

        if (Boolean.TRUE.equals(repo.save(createPiglinFarFromHome()).join())) seeded++;
        if (Boolean.TRUE.equals(repo.save(createAncientGuardian()).join())) seeded++;
        if (Boolean.TRUE.equals(repo.save(createFirstCityProphecy()).join())) seeded++;
        if (Boolean.TRUE.equals(repo.save(createAshenPilgrim()).join())) seeded++;

        logger.info("Seeded " + seeded + " quest definition(s)");
        return seeded;
    }

    private QuestDTO createPiglinFarFromHome() {
        String world = getDefaultWorld();

        Map<String, Object> stateMapping = new LinkedHashMap<>();
        stateMapping.put("NOT_STARTED", List.of("trigger_piglin_spawn"));
        stateMapping.put("TRIGGER_FOUND", List.of("obj_piglin_death", "obj_piglin_escort"));
        stateMapping.put("QUEST_ACTIVE", List.of("obj_portal_encounter"));
        stateMapping.put("OBJECTIVE_FOUND", List.of("obj_portal_defeated"));

        Map<String, Object> triggerPiglinSpawn = new LinkedHashMap<>();
        triggerPiglinSpawn.put("type", "PROXIMITY_MOB_SPAWN");
        triggerPiglinSpawn.put("entity_type", "PIGLIN");
        triggerPiglinSpawn.put("custom_name", "GrotSnout da Lost");
        triggerPiglinSpawn.put("world", world);
        triggerPiglinSpawn.put("radius", 50);
        triggerPiglinSpawn.put("context_key", "spawned_piglin");
        triggerPiglinSpawn.put("interact_book", "grotsnouts_journal");
        triggerPiglinSpawn.put("beg_on_attack", true);
        triggerPiglinSpawn.put("beg_message", "OI! Don't 'it me! I ain't da enemy 'ere! Read me book first, ya git!");
        triggerPiglinSpawn.put("beg_count", 1);
        triggerPiglinSpawn.put("detect_existing", true);

        Map<String, Object> objPiglinDeath = new LinkedHashMap<>();
        objPiglinDeath.put("objective_type", "KILL");
        objPiglinDeath.put("entity_type", "PIGLIN");
        objPiglinDeath.put("custom_name", "GrotSnout");
        objPiglinDeath.put("required_kills", 1);
        objPiglinDeath.put("quest_mob_only", true);
        objPiglinDeath.put("required_state", "TRIGGER_FOUND");
        objPiglinDeath.put("advance_state", "QUEST_ACTIVE");
        objPiglinDeath.put("sets_path", "COMBAT_PATH");

        Map<String, Object> objPiglinEscort = new LinkedHashMap<>();
        objPiglinEscort.put("objective_type", "REACH");
        objPiglinEscort.put("context_location_key", "portal_location");
        objPiglinEscort.put("radius", 10.0);
        objPiglinEscort.put("required_state", "TRIGGER_FOUND");
        objPiglinEscort.put("advance_state", "QUEST_ACTIVE");
        objPiglinEscort.put("sets_path", "ESCORT_PATH");

        Map<String, Object> objPortalEncounter = new LinkedHashMap<>();
        objPortalEncounter.put("objective_type", "KILL");
        objPortalEncounter.put("entity_type", "ZOMBIFIED_PIGLIN");
        objPortalEncounter.put("required_kills", 3);
        objPortalEncounter.put("quest_mob_only", true);
        objPortalEncounter.put("required_state", "QUEST_ACTIVE");
        objPortalEncounter.put("advance_state", "OBJECTIVE_FOUND");

        Map<String, Object> objPortalDefeated = new LinkedHashMap<>();
        objPortalDefeated.put("objective_type", "REACH");
        objPortalDefeated.put("context_location_key", "portal_location");
        objPortalDefeated.put("radius", 5.0);
        objPortalDefeated.put("required_state", "OBJECTIVE_FOUND");
        objPortalDefeated.put("advance_state", "COMPLETED");

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("trigger_piglin_spawn", triggerPiglinSpawn);
        components.put("obj_piglin_death", objPiglinDeath);
        components.put("obj_piglin_escort", objPiglinEscort);
        components.put("obj_portal_encounter", objPortalEncounter);
        components.put("obj_portal_defeated", objPortalDefeated);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("state_mapping", stateMapping);
        metadata.put("components", components);
        metadata.put("start_trigger", "GrotSnout da Lost");

        List<ObjectiveDTO> objectives = List.of(
            ObjectiveDTO.create("escort_piglin", ObjectiveType.REACH, "PORTAL", 1)
                .withDescription("Escort GrotSnout to the portal or defeat the piglin"),
            ObjectiveDTO.create("defeat_encounter", ObjectiveType.KILL, "ZOMBIFIED_PIGLIN", 3)
                .withDescription("Defeat the portal guardians"),
            ObjectiveDTO.create("reach_portal", ObjectiveType.REACH, "PORTAL", 1)
                .withDescription("Reach the nether portal")
        );

        List<RewardDTO> rewards = List.of(
            RewardDTO.item("piglin_reward_1", "GILDED_BLACKSTONE", 3),
            RewardDTO.item("piglin_reward_2", "DIAMOND", 2),
            RewardDTO.experience("piglin_xp", 500)
        );

        return QuestDTO.builder()
            .questId("piglin_far_from_home")
            .name("Piglin Far From Home")
            .description("A lone piglin has wandered far from the Nether. Help GrotSnout find the way home or put an end to the lost creature.")
            .category("adventure")
            .objectives(objectives)
            .rewards(rewards)
            .metadata(metadata)
            .build();
    }

    private QuestDTO createAncientGuardian() {
        String world = getDefaultWorld();

        Map<String, Object> stateMapping = new LinkedHashMap<>();
        stateMapping.put("NOT_STARTED", List.of("trigger_guardian_proximity"));
        stateMapping.put("TRIGGER_FOUND", List.of("obj_guardian_defeat"));
        stateMapping.put("QUEST_ACTIVE", List.of("obj_forgotten_site"));
        stateMapping.put("OBJECTIVE_FOUND", List.of("obj_site_defenders"));

        Map<String, Object> triggerGuardian = new LinkedHashMap<>();
        triggerGuardian.put("type", "ENTITY_PROXIMITY");
        triggerGuardian.put("entity_type", "ELDER_GUARDIAN");
        triggerGuardian.put("world", world);
        triggerGuardian.put("radius", 50);

        Map<String, Object> objGuardianDefeat = new LinkedHashMap<>();
        objGuardianDefeat.put("objective_type", "KILL");
        objGuardianDefeat.put("entity_type", "ELDER_GUARDIAN");
        objGuardianDefeat.put("required_kills", 1);
        objGuardianDefeat.put("required_state", "TRIGGER_FOUND");
        objGuardianDefeat.put("advance_state", "QUEST_ACTIVE");

        Map<String, Object> objForgottenSite = new LinkedHashMap<>();
        objForgottenSite.put("objective_type", "DISCOVER");
        objForgottenSite.put("world", world);
        objForgottenSite.put("detection_radius", 30);
        objForgottenSite.put("detection_materials", "PRISMARINE,PRISMARINE_BRICKS,DARK_PRISMARINE,SEA_LANTERN");
        objForgottenSite.put("min_blocks", 5);
        objForgottenSite.put("required_state", "QUEST_ACTIVE");
        objForgottenSite.put("advance_state", "OBJECTIVE_FOUND");

        Map<String, Object> objSiteDefenders = new LinkedHashMap<>();
        objSiteDefenders.put("objective_type", "KILL");
        objSiteDefenders.put("entity_type", "GUARDIAN");
        objSiteDefenders.put("required_kills", 5);
        objSiteDefenders.put("required_state", "OBJECTIVE_FOUND");
        objSiteDefenders.put("advance_state", "COMPLETED");

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("trigger_guardian_proximity", triggerGuardian);
        components.put("obj_guardian_defeat", objGuardianDefeat);
        components.put("obj_forgotten_site", objForgottenSite);
        components.put("obj_site_defenders", objSiteDefenders);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("state_mapping", stateMapping);
        metadata.put("components", components);
        metadata.put("start_trigger", "Elder Guardian");

        List<ObjectiveDTO> objectives = List.of(
            ObjectiveDTO.create("defeat_guardian", ObjectiveType.KILL, "ELDER_GUARDIAN", 1)
                .withDescription("Defeat the Elder Guardian"),
            ObjectiveDTO.create("find_ruins", ObjectiveType.DISCOVER, "OCEAN_RUINS", 1)
                .withDescription("Discover the forgotten underwater site"),
            ObjectiveDTO.create("defeat_defenders", ObjectiveType.KILL, "GUARDIAN", 5)
                .withDescription("Defeat the site defenders")
        );

        List<RewardDTO> rewards = List.of(
            RewardDTO.item("guardian_trident", "TRIDENT", 1),
            RewardDTO.item("guardian_heart", "HEART_OF_THE_SEA", 1),
            RewardDTO.item("guardian_crystals", "PRISMARINE_CRYSTALS", 8),
            RewardDTO.experience("guardian_xp", 750)
        );

        return QuestDTO.builder()
            .questId("ancient_guardian")
            .name("Ancient Guardian")
            .description("An ancient Elder Guardian has awakened near the ocean monument. Defeat it and discover the forgotten underwater site it was guarding.")
            .category("adventure")
            .objectives(objectives)
            .rewards(rewards)
            .metadata(metadata)
            .build();
    }

    private QuestDTO createFirstCityProphecy() {
        String world = getDefaultWorld();

        Map<String, Object> stateMapping = new LinkedHashMap<>();
        stateMapping.put("NOT_STARTED", List.of("trigger_prophecy_discovery"));
        stateMapping.put("TRIGGER_FOUND", List.of("obj_pillar_interact"));
        stateMapping.put("QUEST_ACTIVE", List.of("obj_city_choice"));

        Map<String, Object> triggerProphecy = new LinkedHashMap<>();
        triggerProphecy.put("type", "ITEM_DISCOVERY");
        triggerProphecy.put("item_type", "WRITTEN_BOOK");
        triggerProphecy.put("item_name", "The First City Prophecy");
        triggerProphecy.put("world", world);

        Map<String, Object> objPillarInteract = new LinkedHashMap<>();
        objPillarInteract.put("objective_type", "INTERACT");
        objPillarInteract.put("block_type", "LECTERN");
        objPillarInteract.put("world", world);
        objPillarInteract.put("required_state", "TRIGGER_FOUND");
        objPillarInteract.put("advance_state", "QUEST_ACTIVE");

        Map<String, Object> objCityChoice = new LinkedHashMap<>();
        objCityChoice.put("objective_type", "INTERACT");
        objCityChoice.put("block_type", "LODESTONE");
        objCityChoice.put("world", world);
        objCityChoice.put("required_state", "QUEST_ACTIVE");
        objCityChoice.put("advance_state", "COMPLETED");

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("trigger_prophecy_discovery", triggerProphecy);
        components.put("obj_pillar_interact", objPillarInteract);
        components.put("obj_city_choice", objCityChoice);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("state_mapping", stateMapping);
        metadata.put("components", components);
        metadata.put("start_trigger", "The First City Prophecy");

        List<ObjectiveDTO> objectives = List.of(
            ObjectiveDTO.create("find_prophecy", ObjectiveType.INTERACT, "WRITTEN_BOOK", 1)
                .withDescription("Discover the prophecy book"),
            ObjectiveDTO.create("activate_pillar", ObjectiveType.INTERACT, "LECTERN", 1)
                .withDescription("Place the book at the quest pillar"),
            ObjectiveDTO.create("choose_city", ObjectiveType.INTERACT, "LODESTONE", 1)
                .withDescription("Choose your city settlement location")
        );

        List<RewardDTO> rewards = List.of(
            RewardDTO.experience("prophecy_xp", 1000),
            RewardDTO.item("prophecy_totem", "TOTEM_OF_UNDYING", 1),
            RewardDTO.command("prophecy_title", "title %player% subtitle {\"text\":\"City Founder\",\"color\":\"gold\"}")
        );

        return QuestDTO.builder()
            .questId("first_city_prophecy")
            .name("The First City Prophecy")
            .description("An ancient prophecy speaks of a First City that will rise again. Discover the book, activate the quest pillar, and choose where to build your settlement.")
            .category("story")
            .objectives(objectives)
            .rewards(rewards)
            .metadata(metadata)
            .build();
    }

    private QuestDTO createAshenPilgrim() {
        String world = getDefaultWorld();

        // State mapping: which components are active in each state
        Map<String, Object> stateMapping = new LinkedHashMap<>();
        stateMapping.put("NOT_STARTED", List.of("trigger_ashlan_spawn"));
        stateMapping.put("TRIGGER_FOUND", List.of("obj_shrine1_encounter"));
        stateMapping.put("QUEST_ACTIVE", List.of("obj_shrine2_collect"));
        stateMapping.put("OBJECTIVE_FOUND", List.of("obj_shrine3_interact"));

        // Trigger: Ashlan the Wanderer spawns near the player
        Map<String, Object> triggerAshlanSpawn = new LinkedHashMap<>();
        triggerAshlanSpawn.put("type", "PROXIMITY_MOB_SPAWN");
        triggerAshlanSpawn.put("entity_type", "WANDERING_TRADER");
        triggerAshlanSpawn.put("custom_name", "Ashlan the Wanderer");
        triggerAshlanSpawn.put("world", world);
        triggerAshlanSpawn.put("radius", 40);
        triggerAshlanSpawn.put("context_key", "ashlan_entity");

        // Shrine 1: Trial of Flame — defeat 3 blazes
        Map<String, Object> objShrine1Encounter = new LinkedHashMap<>();
        objShrine1Encounter.put("objective_type", "ENCOUNTER");
        objShrine1Encounter.put("entity_type", "BLAZE");
        objShrine1Encounter.put("spawn_count", 3);
        objShrine1Encounter.put("required_kills", 3);
        objShrine1Encounter.put("custom_name", "Shrine Guardian");
        objShrine1Encounter.put("trigger_radius", 15);
        objShrine1Encounter.put("spawn_radius", 5);
        objShrine1Encounter.put("world", world);
        objShrine1Encounter.put("x", 100);
        objShrine1Encounter.put("y", 72);
        objShrine1Encounter.put("z", -200);
        objShrine1Encounter.put("required_state", "TRIGGER_FOUND");
        objShrine1Encounter.put("advance_state", "QUEST_ACTIVE");

        // Shrine 2: Trial of Offering — collect soul-themed items (COLLECT objective)
        Map<String, Object> objShrine2Collect = new LinkedHashMap<>();
        objShrine2Collect.put("objective_type", "COLLECT");
        Map<String, Object> collectItems = new LinkedHashMap<>();
        collectItems.put("SOUL_TORCH", 4);
        collectItems.put("GHAST_TEAR", 1);
        objShrine2Collect.put("items", collectItems);
        objShrine2Collect.put("consume", true);
        objShrine2Collect.put("world", world);
        objShrine2Collect.put("x", 250);
        objShrine2Collect.put("y", 65);
        objShrine2Collect.put("z", -350);
        objShrine2Collect.put("radius", 10);
        objShrine2Collect.put("required_state", "QUEST_ACTIVE");
        objShrine2Collect.put("advance_state", "OBJECTIVE_FOUND");

        // Shrine 3: Trial of Release — interact with altar to break the curse
        Map<String, Object> objShrine3Interact = new LinkedHashMap<>();
        objShrine3Interact.put("objective_type", "INTERACT");
        objShrine3Interact.put("block_type", "SOUL_CAMPFIRE");
        objShrine3Interact.put("world", world);
        objShrine3Interact.put("required_count", 1);
        objShrine3Interact.put("required_state", "OBJECTIVE_FOUND");
        objShrine3Interact.put("advance_state", "COMPLETED");

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("trigger_ashlan_spawn", triggerAshlanSpawn);
        components.put("obj_shrine1_encounter", objShrine1Encounter);
        components.put("obj_shrine2_collect", objShrine2Collect);
        components.put("obj_shrine3_interact", objShrine3Interact);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("state_mapping", stateMapping);
        metadata.put("components", components);
        metadata.put("start_trigger", "Ashlan the Wanderer");

        List<ObjectiveDTO> objectives = List.of(
            ObjectiveDTO.create("defeat_shrine_guardians", ObjectiveType.ENCOUNTER, "BLAZE", 3)
                .withDescription("Defeat the Shrine Guardians at the Trial of Flame"),
            ObjectiveDTO.create("gather_offerings", ObjectiveType.COLLECT, "SOUL_TORCH", 4)
                .withDescription("Gather soul offerings: 4 Soul Torches and 1 Ghast Tear"),
            ObjectiveDTO.create("break_curse", ObjectiveType.INTERACT, "SOUL_CAMPFIRE", 1)
                .withDescription("Interact with the altar at the Trial of Release to break Ashlan's curse")
        );

        List<RewardDTO> rewards = List.of(
            RewardDTO.item("pilgrim_lantern", "SOUL_LANTERN", 1),
            RewardDTO.item("pilgrim_shards", "ECHO_SHARD", 2),
            RewardDTO.experience("pilgrim_xp", 600),
            RewardDTO.command("pilgrim_title", "title %player% subtitle {\"text\":\"Curse Breaker\",\"color\":\"dark_purple\"}")
        );

        return QuestDTO.builder()
            .questId("ashen_pilgrim")
            .name("The Ashen Pilgrim")
            .description("A wandering trader who ventured too far made a deal with something in the deep dark. His trade routes became a curse path between three ancient shrines. Guide Ashlan through the trials to break his curse.")
            .category("adventure")
            .objectives(objectives)
            .rewards(rewards)
            .metadata(metadata)
            .build();
    }
}
