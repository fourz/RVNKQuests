package org.fourz.RVNKQuests.placeholder;

import org.bukkit.plugin.Plugin;

/**
 * The only class that touches {@link RVNKQuestsPlaceholderExpansion} (#2214), the same isolation
 * as RVNKEvents 1.1.48.
 *
 * <p>Its method signatures carry no PlaceholderAPI types, and RVNKQuests calls it only after
 * PlaceholderAPI is confirmed enabled. Without PlaceholderAPI the expansion class is never loaded,
 * so its superclass is never resolved and RVNKQuests enables with no
 * {@code NoClassDefFoundError}.</p>
 */
public final class PlaceholderRegistrar {

    private PlaceholderRegistrar() {}

    /**
     * Registers the expansion.
     *
     * @return the registered expansion as an opaque handle for {@link #unregister}, or null when
     *         PlaceholderAPI refused it (for example, the identifier is already taken)
     */
    public static Object register(Plugin plugin, QuestPlaceholderSource source) {
        RVNKQuestsPlaceholderExpansion expansion = new RVNKQuestsPlaceholderExpansion(plugin, source);
        return expansion.register() ? expansion : null;
    }

    /** Unregisters a handle returned by {@link #register}. Ignores null and foreign objects. */
    public static void unregister(Object handle) {
        if (handle instanceof RVNKQuestsPlaceholderExpansion expansion) {
            expansion.unregister();
        }
    }
}
