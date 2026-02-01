package org.fourz.rvnkquests.integration;

import org.bukkit.plugin.Plugin;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.rvnkquests.integration.dto.LoreEntryDTO;
import org.fourz.rvnkcore.util.log.LogManager;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of ILoreIntegration for RVNKLore cross-plugin communication.
 *
 * <p>Uses reflection to access RVNKLore services via RVNKCore's ServiceRegistry,
 * avoiding compile-time dependencies while enabling seamless lore integration.</p>
 *
 * <p>Gracefully degrades when RVNKLore is unavailable - all lore lookups return
 * empty Optional/false, and quests continue to function without lore features.</p>
 */
public class LoreIntegrationImpl implements ILoreIntegration {

    private final RVNKQuests plugin;
    private final LogManager logger;

    // RVNKLore service references (populated via reflection)
    private boolean loreAvailable = false;
    private Object loreService = null;
    private Method getLoreEntryByNameMethod = null;
    private Method getLoreEntryMethod = null;

    public LoreIntegrationImpl(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        initializeLoreService();
    }

    /**
     * Initialize RVNKLore service via reflection.
     */
    private void initializeLoreService() {
        Plugin rvnkLorePlugin = plugin.getServer().getPluginManager().getPlugin("RVNKLore");
        if (rvnkLorePlugin == null || !rvnkLorePlugin.isEnabled()) {
            logger.info("RVNKLore not found - lore integration disabled");
            return;
        }

        try {
            // Get RVNKCore ServiceRegistry
            Plugin rvnkCorePlugin = plugin.getServer().getPluginManager().getPlugin("RVNKCore");
            if (rvnkCorePlugin == null || !rvnkCorePlugin.isEnabled()) {
                logger.warning("RVNKCore not found - cannot access lore services");
                return;
            }

            Class<?> rvnkCoreClass = Class.forName("org.fourz.rvnkcore.RVNKCore");
            Object coreInstance = rvnkCoreClass.getMethod("getInstance").invoke(null);
            if (coreInstance == null) {
                logger.warning("RVNKCore instance is null");
                return;
            }

            Object serviceRegistry = rvnkCoreClass.getMethod("getServiceRegistry").invoke(coreInstance);
            if (serviceRegistry == null) {
                logger.warning("RVNKCore ServiceRegistry is null");
                return;
            }

            // Get ILoreService from registry
            Class<?> registryClass = serviceRegistry.getClass();
            Method getServiceMethod = registryClass.getMethod("getService", Class.class);

            Class<?> loreServiceInterface = Class.forName("org.fourz.rvnklore.service.ILoreService");
            Optional<?> loreServiceOpt = (Optional<?>) getServiceMethod.invoke(serviceRegistry, loreServiceInterface);

            if (loreServiceOpt.isEmpty()) {
                logger.warning("ILoreService not registered - lore integration disabled");
                return;
            }

            loreService = loreServiceOpt.get();

            // Cache reflection methods for lore lookups
            Class<?> loreServiceClass = loreService.getClass();
            getLoreEntryByNameMethod = loreServiceClass.getMethod("getLoreEntryByName", String.class);
            getLoreEntryMethod = loreServiceClass.getMethod("getLoreEntry", UUID.class);

            loreAvailable = true;
            logger.info("RVNKLore integration enabled - lore services available");

        } catch (ClassNotFoundException e) {
            logger.info("RVNKLore classes not found - lore integration disabled");
        } catch (Exception e) {
            logger.warning("Failed to initialize lore integration: " + e.getMessage());
        }
    }

    @Override
    public boolean isLoreAvailable() {
        return loreAvailable && loreService != null;
    }

    @Override
    public CompletableFuture<Optional<LoreEntryDTO>> getLoreForQuest(String questId) {
        // Quest lore uses naming pattern: quest_<questId>
        return getLoreByName("quest_" + questId);
    }

    @Override
    public CompletableFuture<Optional<LoreEntryDTO>> getLoreByName(String loreName) {
        if (!isLoreAvailable()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                @SuppressWarnings("unchecked")
                CompletableFuture<Optional<?>> future =
                    (CompletableFuture<Optional<?>>) getLoreEntryByNameMethod.invoke(loreService, loreName);

                Optional<?> result = future.join();
                return result.map(this::convertToDTO);

            } catch (Exception e) {
                logger.warning("Error getting lore by name '" + loreName + "': " + e.getMessage());
                return Optional.empty();
            }
        });
    }

    @Override
    public CompletableFuture<Optional<LoreEntryDTO>> getLoreById(UUID loreId) {
        if (!isLoreAvailable()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                @SuppressWarnings("unchecked")
                CompletableFuture<Optional<?>> future =
                    (CompletableFuture<Optional<?>>) getLoreEntryMethod.invoke(loreService, loreId);

                Optional<?> result = future.join();
                return result.map(this::convertToDTO);

            } catch (Exception e) {
                logger.warning("Error getting lore by ID '" + loreId + "': " + e.getMessage());
                return Optional.empty();
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> grantLoreDiscovery(UUID playerId, String loreId) {
        if (!isLoreAvailable()) {
            logger.debug("Cannot grant lore discovery - RVNKLore unavailable");
            return CompletableFuture.completedFuture(false);
        }

        // Note: Discovery tracking is a future RVNKLore feature
        // For now, log the intent and return success
        logger.debug("Lore discovery grant requested for player " + playerId + ": " + loreId);
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Optional<String>> getNPCDialogue(String npcId, String context) {
        // NPC dialogue uses naming pattern: npc_<npcId>_<context>
        String loreName = "npc_" + npcId + "_" + context;

        return getLoreByName(loreName)
            .thenApply(opt -> opt.map(LoreEntryDTO::description));
    }

    @Override
    public CompletableFuture<String> getNPCDialogueOrDefault(String npcId, String context, String fallback) {
        return getNPCDialogue(npcId, context)
            .thenApply(opt -> opt.orElse(fallback));
    }

    @Override
    public CompletableFuture<Boolean> hasDiscovered(UUID playerId, String loreId) {
        // Note: Discovery tracking is a future RVNKLore feature
        // For now, return false (no discoveries tracked)
        if (!isLoreAvailable()) {
            return CompletableFuture.completedFuture(false);
        }

        logger.debug("Discovery check for player " + playerId + ": " + loreId + " (feature pending)");
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<List<String>> getPlayerDiscoveries(UUID playerId) {
        // Note: Discovery tracking is a future RVNKLore feature
        // For now, return empty list
        if (!isLoreAvailable()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        logger.debug("Discovery list for player " + playerId + " (feature pending)");
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    /**
     * Convert RVNKLore LoreEntry to LoreEntryDTO via reflection.
     */
    private LoreEntryDTO convertToDTO(Object loreEntry) {
        try {
            Class<?> entryClass = loreEntry.getClass();

            UUID id = (UUID) entryClass.getMethod("getId").invoke(loreEntry);
            String name = (String) entryClass.getMethod("getName").invoke(loreEntry);
            String description = (String) entryClass.getMethod("getDescription").invoke(loreEntry);

            // Get type enum and convert to string
            Object typeEnum = entryClass.getMethod("getType").invoke(loreEntry);
            String type = typeEnum != null ? typeEnum.toString() : "UNKNOWN";

            return new LoreEntryDTO(id.toString(), name, description, type);

        } catch (Exception e) {
            logger.warning("Error converting lore entry to DTO: " + e.getMessage());
            return new LoreEntryDTO("unknown", "Unknown", "Error loading lore", "UNKNOWN");
        }
    }
}
