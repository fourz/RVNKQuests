package org.fourz.RVNKQuests.util;

import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.rvnkcore.RVNKCore;

import java.lang.reflect.Method;

/**
 * Reflection-based bridge to RVNKLore's ILoreItemResolver service.
 *
 * <p>Soft-dep: returns null from {@link #create} if RVNKLore is absent, not yet enabled,
 * or has not registered the service. Callers should treat null as "resolver unavailable"
 * and fall back to display-name matching where appropriate.</p>
 */
public class LoreItemResolverBridge {

    private final Object resolverInstance;
    private final Method getBookIdMethod;

    private LoreItemResolverBridge(Object instance, Method method) {
        this.resolverInstance = instance;
        this.getBookIdMethod = method;
    }

    /**
     * Attempts to obtain the ILoreItemResolver from the RVNKCore ServiceRegistry via reflection.
     *
     * @param plugin The RVNKQuests plugin instance (used only for server access)
     * @return A usable bridge, or null if RVNKLore / RVNKCore is unavailable
     */
    public static LoreItemResolverBridge create(RVNKQuests plugin) {
        try {
            var rvnkLorePlugin = plugin.getServer().getPluginManager().getPlugin("RVNKLore");
            if (rvnkLorePlugin == null || !rvnkLorePlugin.isEnabled()) return null;

            var corePlugin = plugin.getServer().getPluginManager().getPlugin("RVNKCore");
            if (!(corePlugin instanceof RVNKCore core)) return null;

            // Load ILoreItemResolver from RVNKLore's classloader to avoid ClassNotFoundException
            Class<?> resolverClass = Class.forName(
                "org.fourz.RVNKLore.service.ILoreItemResolver",
                true,
                rvnkLorePlugin.getClass().getClassLoader()
            );

            @SuppressWarnings("unchecked")
            Object instance = core.getService((Class<Object>) (Class<?>) resolverClass);
            if (instance == null) return null;

            Method method = resolverClass.getMethod("getBookId", ItemStack.class);
            return new LoreItemResolverBridge(instance, method);

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the lore item name from the item's PDC, or null if not a lore item.
     *
     * @param item The item to inspect
     * @return Item name (e.g. {@code "heralds_mandate"}), or null
     */
    public String getBookId(ItemStack item) {
        try {
            return (String) getBookIdMethod.invoke(resolverInstance, item);
        } catch (Exception e) {
            return null;
        }
    }
}
