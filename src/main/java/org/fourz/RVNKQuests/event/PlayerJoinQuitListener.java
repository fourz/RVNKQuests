package org.fourz.RVNKQuests.event;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.service.IQuestProgressService;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Listener for player join and quit events to manage quest progress loading/saving.
 *
 * <p>Handles:</p>
 * <ul>
 *   <li>Loading quest progress from storage when a player joins</li>
 *   <li>Saving quest progress to storage when a player quits</li>
 *   <li>Notifying QuestManager of player session changes</li>
 * </ul>
 */
public class PlayerJoinQuitListener implements Listener {

    private final RVNKQuests plugin;
    private final LogManager logger;

    /**
     * Creates a new PlayerJoinQuitListener.
     *
     * @param plugin The plugin instance
     */
    public PlayerJoinQuitListener(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "PlayerJoinQuitListener");
    }

    /**
     * Handles player join - loads quest progress from storage.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        logger.debug("Player joined: " + player.getName());

        IQuestProgressService service = plugin.getQuestProgressService();
        if (service == null) {
            logger.warning("QuestProgressService not available - cannot load progress for " + player.getName());
            return;
        }

        // Load progress asynchronously
        service.loadPlayerProgress(player.getUniqueId())
            .thenRun(() -> {
                logger.debug("Loaded quest progress for " + player.getName());
                // Notify QuestManager
                plugin.getQuestManager().onPlayerJoin(player);
            })
            .exceptionally(e -> {
                logger.error("Failed to load quest progress for " + player.getName(), e);
                return null;
            });
    }

    /**
     * Handles player quit - saves quest progress to storage.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        logger.debug("Player quit: " + player.getName());

        // Notify QuestManager first
        plugin.getQuestManager().onPlayerQuit(player);

        IQuestProgressService service = plugin.getQuestProgressService();
        if (service == null) {
            logger.warning("QuestProgressService not available - cannot save progress for " + player.getName());
            return;
        }

        // Save progress asynchronously
        service.saveAndUnloadPlayerProgress(player.getUniqueId())
            .thenRun(() -> {
                logger.debug("Saved quest progress for " + player.getName());
            })
            .exceptionally(e -> {
                logger.error("Failed to save quest progress for " + player.getName(), e);
                return null;
            });
    }
}
