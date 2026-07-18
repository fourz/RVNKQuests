package org.fourz.RVNKQuests.integration;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.integration.dto.LoreEntryDTO;
import org.fourz.rvnkcore.util.log.LogManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Single reflection surface for all RVNKLore cross-plugin access (#1494).
 *
 * <p><b>Single responsibility:</b> resolve RVNKLore services from RVNKCore's
 * ServiceRegistry once, cache the {@link Method} handles, and expose typed,
 * <i>synchronous</i> operations that invoke them with graceful degradation.
 * {@link LoreIntegrationImpl} is a thin async wrapper over this facade; the
 * lectern triggers use {@link #resolveItemId(ItemStack)} directly.</p>
 *
 * <p>Consolidates the two former reflection bridges ({@code LoreItemResolverBridge}
 * and the inline reflection previously in {@code LoreIntegrationImpl}) so a change
 * to any RVNKLore service signature only needs updating here. Every accessor
 * degrades to empty/false/null when RVNKLore is absent or a service is unregistered.</p>
 */
public class LoreServiceFacade {

    private final LogManager logger;

    private boolean available = false;

    // ILoreService
    private Object loreService = null;
    private Method getLoreEntryByNameMethod = null;
    private Method getLoreEntryMethod = null;

    // ILoreBookService
    private Object loreBookService = null;
    private Method getOrCreateQuestBookMethod = null;

    // IItemService
    private Object itemService = null;
    private Method getPresetsForQuestMethod = null;
    private Method createLoreItemByNameMethod = null;
    private Method createLoreItemByIdMethod = null;

    // IRngItemService
    private Object rngItemService = null;
    private Method rollRngMethod = null;

    // ILoreItemResolver
    private Object itemResolverService = null;
    private Method resolveItemIdMethod = null;

    public LoreServiceFacade(RVNKQuests plugin) {
        this.logger = LogManager.getInstance(plugin, getClass());
        initialize(plugin);
    }

    /**
     * Resolve the RVNKCore ServiceRegistry and every RVNKLore service via reflection.
     */
    private void initialize(RVNKQuests plugin) {
        Plugin rvnkLorePlugin = plugin.getServer().getPluginManager().getPlugin("RVNKLore");
        if (rvnkLorePlugin == null || !rvnkLorePlugin.isEnabled()) {
            logger.info("RVNKLore not found - lore integration disabled");
            return;
        }

        try {
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

            // ServiceRegistry.getService(Class) returns T directly and throws if not found
            Method getServiceMethod = serviceRegistry.getClass().getMethod("getService", Class.class);

            // ILoreService (required — its absence disables the integration)
            Class<?> loreServiceInterface = Class.forName("org.fourz.RVNKLore.service.ILoreService");
            try {
                loreService = getServiceMethod.invoke(serviceRegistry, loreServiceInterface);
            } catch (Exception e) {
                logger.warning("ILoreService not registered - lore integration disabled");
                return;
            }
            Class<?> loreServiceClass = loreService.getClass();
            getLoreEntryByNameMethod = loreServiceClass.getMethod("getLoreEntryByName", String.class);
            getLoreEntryMethod = loreServiceClass.getMethod("getLoreEntry", UUID.class);

            // ILoreBookService (optional)
            try {
                Class<?> bookServiceInterface = Class.forName("org.fourz.RVNKLore.service.ILoreBookService");
                loreBookService = getServiceMethod.invoke(serviceRegistry, bookServiceInterface);
                getOrCreateQuestBookMethod = loreBookService.getClass()
                        .getMethod("getOrCreateQuestBook", String.class, String.class, String.class);
                logger.debug("RVNKLore book service available");
            } catch (Exception e) {
                logger.debug("ILoreBookService not registered - quest book integration unavailable: " + e.getMessage());
            }

            // IItemService (optional)
            try {
                Class<?> itemServiceInterface = Class.forName("org.fourz.RVNKLore.service.IItemService");
                itemService = getServiceMethod.invoke(serviceRegistry, itemServiceInterface);
                getPresetsForQuestMethod = itemService.getClass().getMethod("getPresetsForQuest", String.class);
                createLoreItemByNameMethod = itemService.getClass().getMethod("createLoreItem", String.class);
                createLoreItemByIdMethod = itemService.getClass().getMethod("createLoreItem", int.class);
                logger.debug("RVNKLore item service available");
            } catch (Exception e) {
                logger.debug("IItemService not registered - preset item integration unavailable: " + e.getMessage());
            }

            // IRngItemService (optional)
            try {
                Class<?> rngServiceInterface = Class.forName("org.fourz.RVNKLore.service.IRngItemService");
                rngItemService = getServiceMethod.invoke(serviceRegistry, rngServiceInterface);
                rollRngMethod = rngItemService.getClass().getMethod("roll", String.class, String.class);
                logger.debug("RVNKLore RNG item service available");
            } catch (Exception e) {
                logger.debug("IRngItemService not registered - RNG item integration unavailable: " + e.getMessage());
            }

            // ILoreItemResolver (optional) — prefer canonical resolveItemId, fall back to the
            // deprecated getBookId alias so we still work against older RVNKLore builds (#1498).
            try {
                Class<?> resolverInterface = Class.forName("org.fourz.RVNKLore.service.ILoreItemResolver");
                itemResolverService = getServiceMethod.invoke(serviceRegistry, resolverInterface);
                try {
                    resolveItemIdMethod = itemResolverService.getClass().getMethod("resolveItemId", ItemStack.class);
                } catch (NoSuchMethodException nsme) {
                    resolveItemIdMethod = itemResolverService.getClass().getMethod("getBookId", ItemStack.class);
                }
                logger.debug("RVNKLore item resolver available (" + resolveItemIdMethod.getName() + ")");
            } catch (Exception e) {
                logger.debug("ILoreItemResolver not registered - item id resolution unavailable: " + e.getMessage());
            }

            available = true;
            logger.debug("RVNKLore integration enabled - lore services available");

        } catch (ClassNotFoundException e) {
            logger.debug("RVNKLore classes not found - lore integration disabled");
        } catch (Exception e) {
            logger.warning("Failed to initialize lore integration: " + e.getMessage());
        }
    }

    /** @return true when RVNKLore's ILoreService resolved successfully. */
    public boolean isAvailable() {
        return available && loreService != null;
    }

    // ── Lore entry lookups (synchronous; callers wrap in async as needed) ─────────

    public Optional<LoreEntryDTO> getLoreEntryByName(String loreName) {
        if (!isAvailable()) return Optional.empty();
        try {
            @SuppressWarnings("unchecked")
            CompletableFuture<Optional<?>> future =
                (CompletableFuture<Optional<?>>) getLoreEntryByNameMethod.invoke(loreService, loreName);
            return future.join().map(this::convertToDTO);
        } catch (Exception e) {
            logger.warning("Error getting lore by name '" + loreName + "': " + e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<LoreEntryDTO> getLoreEntryById(UUID loreId) {
        if (!isAvailable()) return Optional.empty();
        try {
            @SuppressWarnings("unchecked")
            CompletableFuture<Optional<?>> future =
                (CompletableFuture<Optional<?>>) getLoreEntryMethod.invoke(loreService, loreId);
            return future.join().map(this::convertToDTO);
        } catch (Exception e) {
            logger.warning("Error getting lore by ID '" + loreId + "': " + e.getMessage());
            return Optional.empty();
        }
    }

    // ── Item / book operations (synchronous) ─────────────────────────────────────

    public Optional<ItemStack> getOrCreateQuestBook(String questItemKey, String title, String description) {
        if (loreBookService == null || getOrCreateQuestBookMethod == null) return Optional.empty();
        try {
            @SuppressWarnings("unchecked")
            CompletableFuture<Optional<?>> future =
                (CompletableFuture<Optional<?>>) getOrCreateQuestBookMethod
                    .invoke(loreBookService, questItemKey, title, description);
            return future.join().map(o -> (ItemStack) o);
        } catch (Exception e) {
            logger.warning("Error getting quest book '" + questItemKey + "': " + e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<ItemStack> rollRngItem(String poolId, String rarityTier) {
        if (rngItemService == null || rollRngMethod == null) return Optional.empty();
        try {
            @SuppressWarnings("unchecked")
            CompletableFuture<Optional<?>> future =
                (CompletableFuture<Optional<?>>) rollRngMethod.invoke(rngItemService, poolId, rarityTier);
            return future.join().map(stack -> (ItemStack) stack);
        } catch (Exception e) {
            logger.warning("Failed to roll RNG item from pool '" + poolId + "': " + e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<ItemStack> createLoreItemByName(String name) {
        if (itemService == null || createLoreItemByNameMethod == null) return Optional.empty();
        try {
            @SuppressWarnings("unchecked")
            CompletableFuture<Optional<?>> future =
                (CompletableFuture<Optional<?>>) createLoreItemByNameMethod.invoke(itemService, name);
            return future.join().map(stack -> (ItemStack) stack);
        } catch (Exception e) {
            logger.warning("Failed to spawn lore item by name '" + name + "': " + e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<ItemStack> createLoreItemById(int itemId) {
        if (itemService == null || createLoreItemByIdMethod == null) return Optional.empty();
        try {
            @SuppressWarnings("unchecked")
            CompletableFuture<Optional<?>> future =
                (CompletableFuture<Optional<?>>) createLoreItemByIdMethod.invoke(itemService, itemId);
            return future.join().map(stack -> (ItemStack) stack);
        } catch (Exception e) {
            logger.warning("Failed to spawn lore item by ID " + itemId + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    public List<ItemStack> getQuestPresetItems(String questId) {
        if (itemService == null || getPresetsForQuestMethod == null || createLoreItemByIdMethod == null) {
            return List.of();
        }
        try {
            @SuppressWarnings("unchecked")
            CompletableFuture<List<?>> presetsFuture =
                (CompletableFuture<List<?>>) getPresetsForQuestMethod.invoke(itemService, questId);
            List<?> presets = presetsFuture.join();

            List<ItemStack> items = new ArrayList<>();
            for (Object props : presets) {
                try {
                    int id = (int) props.getClass().getMethod("getDatabaseId").invoke(props);
                    @SuppressWarnings("unchecked")
                    CompletableFuture<Optional<?>> itemFuture =
                        (CompletableFuture<Optional<?>>) createLoreItemByIdMethod.invoke(itemService, id);
                    itemFuture.join().ifPresent(stack -> items.add((ItemStack) stack));
                } catch (Exception e) {
                    logger.warning("Failed to create preset item for quest '" + questId + "': " + e.getMessage());
                }
            }
            return items;
        } catch (Exception e) {
            logger.warning("Error getting preset items for quest '" + questId + "': " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Resolve the {@code lore_item_name} PDC tag from an item, or null if absent.
     * Synchronous — reads only item metadata.
     */
    public String resolveItemId(ItemStack item) {
        if (item == null || itemResolverService == null || resolveItemIdMethod == null) return null;
        try {
            return (String) resolveItemIdMethod.invoke(itemResolverService, item);
        } catch (Exception e) {
            logger.warning("Failed to resolve lore item id: " + e.getMessage());
            return null;
        }
    }

    /**
     * Convert an RVNKLore LoreEntry to a LoreEntryDTO via reflection.
     */
    private LoreEntryDTO convertToDTO(Object loreEntry) {
        try {
            Class<?> entryClass = loreEntry.getClass();
            UUID id = (UUID) entryClass.getMethod("getId").invoke(loreEntry);
            String name = (String) entryClass.getMethod("getName").invoke(loreEntry);
            String description = (String) entryClass.getMethod("getDescription").invoke(loreEntry);
            Object typeEnum = entryClass.getMethod("getType").invoke(loreEntry);
            String type = typeEnum != null ? typeEnum.toString() : "UNKNOWN";
            return new LoreEntryDTO(id.toString(), name, description, type);
        } catch (Exception e) {
            logger.warning("Error converting lore entry to DTO: " + e.getMessage());
            return new LoreEntryDTO("unknown", "Unknown", "Error loading lore", "UNKNOWN");
        }
    }
}
