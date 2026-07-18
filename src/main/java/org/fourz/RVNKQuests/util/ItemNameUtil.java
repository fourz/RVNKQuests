package org.fourz.RVNKQuests.util;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Resolves an item's display name to plain text for config matching.
 *
 * <p>This plugin compiles against spigot-api, which only exposes the legacy
 * {@link ItemMeta#getDisplayName()} (not Paper's Adventure {@code displayName()}).
 * On Paper 1.21.x the legacy bridge returns a JSON-serialized string for items
 * named via the {@code minecraft:custom_name} data component — e.g.
 * {@code "Heralds Tome"} <em>including</em> the surrounding double-quotes — which
 * breaks naive equality against a plain config value like {@code Heralds Tome}.
 * Anvil-renamed items (legacy path) do not show the artifact; component-named
 * items (from {@code /give ... custom_name=}, datapacks, plugins) do.</p>
 *
 * <p>This helper strips color codes and the JSON-quote artifact so both naming
 * paths match consistently. Used by all item-name-matching triggers.</p>
 */
public final class ItemNameUtil {

    private ItemNameUtil() {}

    /**
     * Resolves an item's plain display name.
     *
     * @param item the item to inspect (may be null)
     * @return the plain display name, or {@code null} if the item has none
     */
    public static String plainDisplayName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return null;
        return normalize(meta.getDisplayName());
    }

    /**
     * Strips color codes and the Paper-legacy JSON-quote artifact from a raw
     * display-name string.
     *
     * @param raw the raw display name (may be null)
     * @return normalized plain text, or {@code null} if {@code raw} is null
     */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String name = ChatColor.stripColor(raw);
        if (name == null) return null;
        name = name.trim();
        // Paper 1.21 legacy bridge wraps component-set names in JSON double-quotes.
        if (name.length() >= 2 && name.charAt(0) == '"' && name.charAt(name.length() - 1) == '"') {
            name = name.substring(1, name.length() - 1)
                       .replace("\\\"", "\"")
                       .replace("\\\\", "\\");
        }
        return name;
    }
}
