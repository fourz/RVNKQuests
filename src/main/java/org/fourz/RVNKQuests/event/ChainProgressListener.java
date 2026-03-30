package org.fourz.RVNKQuests.event;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.service.IQuestChainService;
import org.fourz.RVNKQuests.service.IQuestChainService.ChainUpdate;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.List;

/**
 * Listens for quest completions and updates chain progress.
 *
 * <p>Bridges the gap between individual quest completion events and the
 * chain service, unlocking next quests in a chain and delivering chain
 * completion rewards.</p>
 */
public class ChainProgressListener implements Listener {

    private final RVNKQuests plugin;
    private final LogManager logger;

    public ChainProgressListener(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuestComplete(QuestCompleteEvent event) {
        IQuestChainService chainService = plugin.getQuestChainService();
        if (chainService == null) return;

        Player player = event.getPlayer();

        chainService.onQuestComplete(player.getUniqueId(), event.getQuestId())
            .thenAccept(updates -> {
                if (updates.isEmpty()) return;

                Bukkit.getScheduler().runTask(plugin, () ->
                    notifyPlayer(player, updates));
            })
            .exceptionally(ex -> {
                logger.warning("Failed to process chain update for " +
                    player.getName() + " completing " + event.getQuestId() + ": " + ex.getMessage());
                return null;
            });
    }

    private void notifyPlayer(Player player, List<ChainUpdate> updates) {
        if (!player.isOnline()) return;

        for (ChainUpdate update : updates) {
            switch (update.type()) {
                case QUESTS_UNLOCKED -> {
                    player.sendMessage("\u00a76\u00a7l[Quest Chain] \u00a7eNew quests unlocked!");
                    for (String questId : update.unlockedQuests()) {
                        player.sendMessage("  \u00a7a\u25b6 \u00a7f" + formatQuestName(questId));
                    }
                }
                case CHAIN_COMPLETED -> {
                    player.sendMessage("\u00a76\u00a7l[Quest Chain] \u00a7aChain complete: \u00a7f" + update.chainId());
                    if (!update.deliveredRewards().isEmpty()) {
                        player.sendMessage("  \u00a7eRewards granted!");
                    }
                }
                case QUEST_COMPLETED -> {
                    logger.debug("Quest " + update.message() + " completed in chain " + update.chainId());
                }
                case NODE_COMPLETED -> {
                    logger.debug("Node completed in chain " + update.chainId());
                }
            }
        }
    }

    private String formatQuestName(String questId) {
        if (questId == null || questId.isEmpty()) return "Unknown Quest";
        String[] words = questId.split("_");
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) formatted.append(" ");
            String word = words[i];
            if (!word.isEmpty()) {
                formatted.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) formatted.append(word.substring(1).toLowerCase());
            }
        }
        return formatted.toString();
    }
}
