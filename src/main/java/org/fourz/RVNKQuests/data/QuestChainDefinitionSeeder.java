package org.fourz.RVNKQuests.data;

import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.dto.*;
import org.fourz.RVNKQuests.data.dto.QuestChainDTO.ChainNode;
import org.fourz.RVNKQuests.service.IQuestChainService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Seeds quest chain definitions on first run.
 * Called during onEnable() after quest definitions are loaded
 * (chains reference existing quest IDs).
 *
 * <p>Currently seeds one chain: "Wanderer's Path" linking the seeded
 * quests into a sequential narrative.</p>
 */
public class QuestChainDefinitionSeeder {

    private final RVNKQuests plugin;
    private final LogManager logger;

    public QuestChainDefinitionSeeder(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "QuestChainSeeder");
    }

    /**
     * Seeds chain definitions if none exist.
     * Safe to call on every startup — skips if chains already registered.
     */
    public void seedIfNeeded() {
        IQuestChainService chainService = plugin.getQuestChainService();
        if (chainService == null) {
            logger.warning("Quest chain service not available — skipping seed");
            return;
        }

        try {
            if (!chainService.getAllChains().join().isEmpty()) {
                logger.debug("Quest chains already exist — skipping seed");
                return;
            }

            seed(chainService);
        } catch (Exception e) {
            logger.error("Failed to seed quest chain definitions", e);
        }
    }

    private void seed(IQuestChainService chainService) {
        int count = 0;

        // Chain 1: Wanderer's Path
        // A sequential chain linking the seeded quests into a narrative arc.
        // piglin_far_from_home → ashen_pilgrim → (unlocks ancient_guardian)
        QuestChainDTO wanderersPath = QuestChainDTO.builder()
            .chainId("wanderers_path")
            .name("Wanderer's Path")
            .description("A journey through the world's forgotten corners. "
                + "Help a lost piglin find its way, walk the ashen pilgrim's road, "
                + "and earn the right to face the ancient guardian.")
            .category("adventure")
            .node(ChainNode.sequence("seq_wanderers",
                ChainNode.quest("node_piglin", "piglin_far_from_home"),
                ChainNode.quest("node_pilgrim", "ashen_pilgrim")
            ))
            .completionReward(RewardDTO.create("wanderers_unlock_guardian",
                    RewardType.QUEST_UNLOCK, "ancient_guardian", 1)
                .withDescription("Unlocks the Ancient Guardian quest")
                .withMetadata(Map.of("questId", "ancient_guardian", "notify", "true")))
            .completionReward(RewardDTO.experience("wanderers_xp", 500)
                .withDescription("500 XP for completing the Wanderer's Path"))
            .repeatable(false)
            .createdAt(Instant.now())
            .metadata("difficulty", "beginner")
            .metadata("estimated_time", "30-60 minutes")
            .build();

        if (registerChain(chainService, wanderersPath)) count++;

        logger.info("Seeded " + count + " quest chain definition(s)");
    }

    private boolean registerChain(IQuestChainService chainService, QuestChainDTO chain) {
        try {
            boolean success = chainService.registerChain(chain).join();
            if (success) {
                logger.info("Registered chain: " + chain.chainId() + " (" + chain.name() + ")");
            } else {
                logger.warning("Failed to register chain: " + chain.chainId());
            }
            return success;
        } catch (Exception e) {
            logger.error("Error registering chain: " + chain.chainId(), e);
            return false;
        }
    }
}
