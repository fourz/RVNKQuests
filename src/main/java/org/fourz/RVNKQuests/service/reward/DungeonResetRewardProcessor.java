package org.fourz.RVNKQuests.service.reward;

import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.data.dto.RewardType;
import org.fourz.RVNKQuests.dungeon.DungeonApi;
import org.fourz.RVNKQuests.service.IRewardService.RewardDeliveryResult;
import org.fourz.RVNKQuests.service.RewardProcessor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * DUNGEON_RESET reward (#2371): resets the dungeon named in {@code value} with reason QUEST, so the
 * next player finds it fresh. A dungeon whose policy refuses quest resets reports REFUSED.
 *
 * <p>The RVNKCore types are touched only inside {@link DungeonResetCall}, after
 * {@link DungeonApi#isPresent()}, so this processor loads on an older RVNKCore.</p>
 */
public class DungeonResetRewardProcessor implements RewardProcessor {

    @Override
    public RewardType getType() {
        return RewardType.DUNGEON_RESET;
    }

    @Override
    public boolean requiresOnlinePlayer() {
        return false;
    }

    @Override
    public boolean isAvailable() {
        return DungeonApi.isPresent();
    }

    @Override
    public CompletableFuture<RewardDeliveryResult> deliver(UUID playerId, RewardDTO reward) {
        String id = reward.value();
        if (id == null || id.isBlank()) {
            return CompletableFuture.completedFuture(
                    RewardDeliveryResult.failure(reward, "No dungeon id in the reward value", "EMPTY_DUNGEON"));
        }
        if (!DungeonApi.isPresent()) {
            return CompletableFuture.completedFuture(RewardDeliveryResult.failure(reward,
                    "RVNKCore has no dungeon contract (needs 1.5.112-alpha+)", "DUNGEON_API_MISSING"));
        }
        return DungeonResetCall.reset(id.trim(), "quest:" + reward.rewardId())
                .thenApply(r -> r.ok()
                        ? RewardDeliveryResult.success(reward, "Reset dungeon " + id + ": " + r.message(),
                                Map.of("dungeon", id, "players_moved", r.moved(), "mobs_spawned", r.spawned()))
                        : RewardDeliveryResult.failure(reward, "Dungeon " + id + ": " + r.message(), "DUNGEON_" + r.status()));
    }
}
