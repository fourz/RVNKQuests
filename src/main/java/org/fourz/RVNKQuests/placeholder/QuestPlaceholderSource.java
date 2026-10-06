package org.fourz.RVNKQuests.placeholder;

import org.fourz.RVNKQuests.data.dto.QuestProgressDTO;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Function;

/**
 * The data the {@code %rvnkquests_*%} expansion reads (#2214). No PlaceholderAPI types.
 *
 * @param progress player to their in-memory progress rows, or null when not loaded. Must not
 *                 query the database: PlaceholderAPI can call this on the main thread, once per
 *                 viewer per refresh.
 * @param quests   quest id to model, or null when the quest is not registered
 */
public record QuestPlaceholderSource(Function<UUID, Collection<QuestProgressDTO>> progress,
                                     Function<String, QuestStepModel> quests) {

    /** Resolves a key for a player; {@link QuestPlaceholderResolver#NONE} for a null player. */
    public String resolve(UUID player, String params) {
        try {
            if (player == null) return QuestPlaceholderResolver.NONE;
            return QuestPlaceholderResolver.resolve(params, progress.apply(player), quests);
        } catch (RuntimeException e) {
            return QuestPlaceholderResolver.NONE;
        }
    }
}
