package org.fourz.RVNKQuests.service;

import org.fourz.RVNKQuests.data.IOnceRewardStore;
import org.fourz.RVNKQuests.data.dto.RewardDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Run-once world rewards (#2268): the reward flag {@code once: server}.
 *
 * <p>A reward with {@code once: server} fires on the first completion on this server and never
 * again, until an admin runs {@code /quest reward reset-once}. The flag is stored as the reward
 * metadata key {@value #METADATA_KEY} = {@value #SERVER}, so it travels through the existing
 * {@code quest_definition_rewards.metadata} column with no change to that table.</p>
 *
 * <h2>Order and failure</h2>
 * <ul>
 *   <li>The rewards that survive keep their input order, so the reward_id order and the processor
 *       priority sort in {@code RewardServiceImpl} apply exactly as for any other reward.</li>
 *   <li>Claims run one after another, before any delivery. A reward is recorded when it is
 *       claimed, so a delivery that then fails is not retried; reset it to fire it again.</li>
 *   <li>No store, or a store that throws: the once reward is dropped and a warning names it. It
 *       fails closed, because a world change that runs twice is the bug this flag exists to stop.
 *       It is not recorded, so the next completion tries again.</li>
 * </ul>
 */
public final class OnceRewards {

    /** Reward metadata key, and the YAML key on a reward. */
    public static final String METADATA_KEY = "once";

    /** The only supported value. */
    public static final String SERVER = "server";

    private OnceRewards() {
    }

    /** @return true when the reward carries {@code once: server} */
    public static boolean isOnceServer(RewardDTO reward) {
        if (reward == null || reward.metadata() == null) return false;
        String v = reward.metadata().get(METADATA_KEY);
        return v != null && SERVER.equals(v.trim().toLowerCase(Locale.ROOT));
    }

    /** @return true when any reward in the list carries {@code once: server} */
    public static boolean anyOnceServer(List<RewardDTO> rewards) {
        if (rewards == null) return false;
        for (RewardDTO r : rewards) {
            if (isOnceServer(r)) return true;
        }
        return false;
    }

    /**
     * The store key of a reward: its id for a completion reward, {@code <component>/<id>} for an
     * {@code on_advance} entry, so the two lists cannot collide.
     *
     * @param componentId the component for an on_advance entry, or null for a completion reward
     * @param rewardId    the reward id
     * @return the key
     */
    public static String key(String componentId, String rewardId) {
        return componentId == null ? rewardId : componentId + "/" + rewardId;
    }

    /**
     * Claims each {@code once: server} reward and returns the rewards to deliver, in input order.
     *
     * @param store       the once store; null drops every once reward (fail closed)
     * @param questId     the quest
     * @param componentId the component for on_advance entries, or null for completion rewards
     * @param player      the player whose completion or advance fires them
     * @param rewards     the rewards, in delivery order
     * @param warn        receives one line per dropped reward
     * @return the rewards to deliver; never null
     */
    public static CompletableFuture<List<RewardDTO>> claimAndFilter(IOnceRewardStore store, String questId,
                                                                     String componentId, UUID player,
                                                                     List<RewardDTO> rewards,
                                                                     Consumer<String> warn) {
        if (rewards == null || rewards.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<RewardDTO> keep = new ArrayList<>(rewards.size());
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (RewardDTO reward : rewards) {
            if (!isOnceServer(reward)) {
                chain = chain.thenRun(() -> keep.add(reward));
                continue;
            }
            String key = key(componentId, reward.rewardId());
            if (store == null) {
                chain = chain.thenRun(() -> warn.accept("once:server reward " + questId + " " + key
                        + " dropped - no once-reward store is available; it is NOT recorded"));
                continue;
            }
            chain = chain.thenCompose(ignored -> store.tryClaim(questId, key, player)
                    .handle((claimed, ex) -> {
                        if (ex != null) {
                            warn.accept("once:server reward " + questId + " " + key
                                    + " dropped - the claim failed (" + rootMessage(ex)
                                    + "); it is NOT recorded, so the next completion retries");
                        } else if (Boolean.TRUE.equals(claimed)) {
                            keep.add(reward);
                        }
                        return null;
                    }));
        }
        return chain.thenApply(ignored -> List.copyOf(keep));
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur.getMessage() != null ? cur.getMessage() : cur.getClass().getSimpleName();
    }
}
