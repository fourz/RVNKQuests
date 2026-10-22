package org.fourz.RVNKQuests.objective.generic;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.util.ItemNameUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The item a DELIVER objective accepts (#2390). Every key that is set must match.
 *
 * <h3>Keys</h3>
 * <ul>
 *   <li>{@code item_type} — a {@link Material} name ({@code WRITTEN_BOOK} or {@code minecraft:written_book})</li>
 *   <li>{@code custom_name} (alias {@code item_name}) — the display name, colour codes (in the item
 *       and in the config) and the
 *       Paper JSON-quote artifact stripped, matched with {@code contains} like
 *       {@code GenericItemDiscoveryTrigger}</li>
 *   <li>{@code lore_item_id} — the RVNKLore item. A number matches the {@code rvnklore:lore_item_id}
 *       INTEGER tag; any other text matches the {@code rvnklore:lore_item_name} STRING tag, case
 *       ignored. RVNKLore stamps both on every item it mints ({@code ItemManager.applyIdPdc}).</li>
 * </ul>
 *
 * <p>The tags are read straight from the item's PersistentDataContainer by their namespaced keys.
 * RVNKQuests needs no RVNKLore class for this, and the read works with RVNKLore absent.</p>
 */
public final class DeliverItemMatch {

    /** RVNKLore's numeric item id tag: {@code new NamespacedKey(RVNKLore, "lore_item_id")}. */
    public static final NamespacedKey LORE_ITEM_ID_KEY = new NamespacedKey("rvnklore", "lore_item_id");
    /** RVNKLore's item name tag: {@code new NamespacedKey(RVNKLore, "lore_item_name")}. */
    public static final NamespacedKey LORE_ITEM_NAME_KEY = new NamespacedKey("rvnklore", "lore_item_name");

    private final Material itemType;
    private final String customName;
    private final Integer loreItemId;
    private final String loreItemName;

    private DeliverItemMatch(Material itemType, String customName, Integer loreItemId, String loreItemName) {
        this.itemType = itemType;
        this.customName = customName;
        this.loreItemId = loreItemId;
        this.loreItemName = loreItemName;
    }

    /**
     * Reads the match keys from a component config.
     *
     * @throws IllegalArgumentException when no key is set, or {@code item_type} is not a material
     */
    public static DeliverItemMatch from(Map<String, Object> config) {
        List<String> problems = new ArrayList<>();

        Material type = null;
        String typeRaw = blankToNull(QuestComponentFactory.getStringConfig(config, "item_type", null));
        if (typeRaw != null) {
            type = Material.matchMaterial(typeRaw);
            if (type == null || type == Material.AIR) {
                problems.add("item_type '" + typeRaw + "' is not an item material");
            }
        }

        String name = blankToNull(QuestComponentFactory.getStringConfig(config, "custom_name",
            QuestComponentFactory.getStringConfig(config, "item_name", null)));
        if (name != null) {
            // The config may carry & or section codes; the comparison is on plain text.
            name = ItemNameUtil.normalize(org.bukkit.ChatColor.translateAlternateColorCodes('&', name));
        }

        Integer id = null;
        String loreName = null;
        String idRaw = blankToNull(QuestComponentFactory.getStringConfig(config, "lore_item_id", null));
        if (idRaw != null) {
            try {
                id = Integer.parseInt(idRaw);
                if (id <= 0) problems.add("lore_item_id must be a positive number or an item name");
            } catch (NumberFormatException e) {
                loreName = idRaw;
            }
        }

        if (typeRaw == null && name == null && idRaw == null) {
            problems.add("needs at least one of item_type, custom_name (item_name), lore_item_id");
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(GenericDeliverObjective.TYPE_NAME + " " + String.join("; ", problems));
        }
        return new DeliverItemMatch(type, name, id, loreName);
    }

    /** True when the stack is not empty and every configured key matches. */
    public boolean matches(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) return false;
        if (itemType != null && item.getType() != itemType) return false;

        if (customName != null) {
            String plain = ItemNameUtil.plainDisplayName(item);
            if (plain == null || !plain.contains(customName)) return false;
        }

        if (loreItemId != null || loreItemName != null) {
            if (!item.hasItemMeta()) return false;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return false;
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (pdc == null) return false;
            if (loreItemId != null && !loreItemId.equals(read(pdc, LORE_ITEM_ID_KEY, PersistentDataType.INTEGER))) {
                return false;
            }
            if (loreItemName != null) {
                String stored = read(pdc, LORE_ITEM_NAME_KEY, PersistentDataType.STRING);
                if (stored == null || !stored.trim().equalsIgnoreCase(loreItemName.trim())) return false;
            }
        }
        return true;
    }

    /** The tag value, or null when absent or stored under another type. */
    private static <T> T read(PersistentDataContainer pdc, NamespacedKey key, PersistentDataType<?, T> type) {
        try {
            return pdc.get(key, type);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** A short text for logs and the default description, e.g. {@code WRITTEN_BOOK "Outpost Ledger"}. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        if (customName != null) sb.append('"').append(customName).append('"');
        else if (loreItemName != null) sb.append(loreItemName);
        else if (itemType != null) sb.append(itemType.name().toLowerCase(Locale.ROOT).replace('_', ' '));
        else sb.append("lore item #").append(loreItemId);
        return sb.toString();
    }

    public Material getItemType() {
        return itemType;
    }

    public String getCustomName() {
        return customName;
    }

    public Integer getLoreItemId() {
        return loreItemId;
    }

    public String getLoreItemName() {
        return loreItemName;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
