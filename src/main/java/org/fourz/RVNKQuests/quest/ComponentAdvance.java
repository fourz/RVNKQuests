package org.fourz.RVNKQuests.quest;

import org.bukkit.event.Listener;
import org.fourz.RVNKQuests.party.PartyBeatContext;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The one way a quest component advances a player, so its {@code on_advance} list (#2267) rides
 * the commit.
 *
 * <p>When the component has no {@code on_advance}, this makes exactly the call the component made
 * before 1.1.69, so a quest without the key behaves as it did. When it has one, the advance goes
 * through the overload that runs the hook for each player whose change committed, and never for
 * a refused one.</p>
 */
public final class ComponentAdvance {

    private ComponentAdvance() {
    }

    /**
     * {@code quest.tryAdvanceStateForPlayer(player, target, ctx)}, plus the component's
     * {@code on_advance} hook.
     *
     * @return true when the firer's change committed
     */
    public static CompletableFuture<Boolean> tryAdvance(DataDrivenQuest quest, Listener component, UUID player,
                                                        QuestState target, PartyBeatContext ctx) {
        Consumer<UUID> hook = quest.onAdvanceHook(component);
        if (hook == null) {
            return quest.tryAdvanceStateForPlayer(player, target, ctx);
        }
        return quest.tryAdvanceStateForPlayer(player, target, ctx, hook);
    }

    /**
     * {@code quest.advanceStateForPlayer(player, target, ctx)}, plus the component's
     * {@code on_advance} hook, for components that only need the {@code Void} future.
     */
    public static CompletableFuture<Void> advance(DataDrivenQuest quest, Listener component, UUID player,
                                                  QuestState target, PartyBeatContext ctx) {
        Consumer<UUID> hook = quest.onAdvanceHook(component);
        if (hook == null) {
            return quest.advanceStateForPlayer(player, target, ctx);
        }
        return quest.tryAdvanceStateForPlayer(player, target, ctx, hook).thenApply(ignored -> (Void) null);
    }
}
