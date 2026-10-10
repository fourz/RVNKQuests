package org.fourz.RVNKQuests.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

/**
 * PlaceholderAPI expansion {@code %rvnkquests_*%} (#2214).
 *
 * <p>Loaded only through {@link PlaceholderRegistrar}, after PlaceholderAPI is confirmed enabled,
 * so RVNKQuests itself never links against PlaceholderAPI classes. Values come from in-memory
 * progress through {@link QuestPlaceholderSource}; key handling is in
 * {@link QuestPlaceholderResolver}.</p>
 *
 * <p>Every key is per player. Citizens resolves NPC names and holograms with a null player, so
 * there every key returns {@code -}.</p>
 */
public final class RVNKQuestsPlaceholderExpansion extends PlaceholderExpansion {

    public static final String IDENTIFIER = "rvnkquests";

    private final Plugin plugin;
    private final QuestPlaceholderSource source;

    public RVNKQuestsPlaceholderExpansion(Plugin plugin, QuestPlaceholderSource source) {
        this.plugin = plugin;
        this.source = source;
    }

    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    @SuppressWarnings("deprecation")
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    @SuppressWarnings("deprecation")
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** Keep the expansion across {@code /papi reload}; RVNKQuests unregisters it on disable. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        try {
            if (player == null) return QuestPlaceholderResolver.NONE;
            return source.resolve(player.getUniqueId(), params);
        } catch (Throwable t) {
            return QuestPlaceholderResolver.NONE;
        }
    }
}
