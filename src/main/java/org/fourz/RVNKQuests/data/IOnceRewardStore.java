package org.fourz.RVNKQuests.data;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Server-wide record of {@code once: server} rewards that have fired (#2268).
 *
 * <p>One row per {@code quest_id} + reward key. A completion reward's key is its
 * {@code reward_id}; an {@code on_advance} entry's key is {@code <component_id>/<reward_id>}.</p>
 *
 * <p><b>The claim is the guard.</b> {@link #tryClaim} inserts the row and reports whether THIS
 * call inserted it. It is atomic in the store (a primary key plus insert-ignore), so two
 * completions racing on the same reward get exactly one {@code true} between them. A caller
 * delivers the reward only on {@code true}.</p>
 */
public interface IOnceRewardStore {

    /**
     * Records that a reward fired, if it has not already.
     *
     * @param questId   the quest
     * @param rewardKey the reward key (see the class note)
     * @param firedBy   the player whose completion fired it
     * @return true when this call recorded it, so the caller may deliver; false when it was
     *         already recorded. Completes exceptionally when the store cannot be reached.
     */
    CompletableFuture<Boolean> tryClaim(String questId, String rewardKey, UUID firedBy);

    /**
     * Forgets fired rewards so they can fire again.
     *
     * @param questId   the quest
     * @param rewardKey one reward key, or null for every once reward of the quest
     * @return how many records were removed
     */
    CompletableFuture<Integer> reset(String questId, String rewardKey);

    /**
     * @param questId the quest
     * @return the fired records of the quest, sorted by reward key
     */
    CompletableFuture<List<OnceRecord>> list(String questId);

    /** One fired once-reward. */
    record OnceRecord(String questId, String rewardKey, UUID firedBy, long firedAtMillis) {
    }
}
