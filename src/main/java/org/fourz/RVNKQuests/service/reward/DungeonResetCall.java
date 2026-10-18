package org.fourz.RVNKQuests.service.reward;

import org.fourz.rvnkcore.api.dungeon.Dungeons;
import org.fourz.rvnkcore.api.dungeon.ResetReason;
import org.fourz.rvnkcore.api.dungeon.ResetResult;

import java.util.concurrent.CompletableFuture;

/**
 * The one place that touches RVNKCore's dungeon types for the reset reward (#2371). Loaded only
 * after {@code DungeonApi.isPresent()}.
 */
final class DungeonResetCall {

    /** Plain result, so the caller holds no RVNKCore type. */
    record Outcome(boolean ok, String status, String message, int moved, int spawned) { }

    private DungeonResetCall() { }

    static CompletableFuture<Outcome> reset(String id, String actor) {
        return Dungeons.resolve().reset(id, ResetReason.QUEST, actor)
                .thenApply(DungeonResetCall::of);
    }

    static Outcome of(ResetResult r) {
        return new Outcome(r.ok(), r.status().name(), r.message(), r.playersMoved(), r.mobsSpawned());
    }
}
