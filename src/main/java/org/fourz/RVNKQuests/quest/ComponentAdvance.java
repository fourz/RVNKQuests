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
 *
 * <p>The same hook carries the beat's {@code advance_message} to party members whose state moved with
 * the firer (#2418); the firer still hears it from the component itself.</p>
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
        Consumer<UUID> hook = both(quest.onAdvanceHook(component), quest.memberFeedbackHook(component, player));
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
        Consumer<UUID> hook = both(quest.onAdvanceHook(component), quest.memberFeedbackHook(component, player));
        if (hook == null) {
            return quest.advanceStateForPlayer(player, target, ctx);
        }
        return quest.tryAdvanceStateForPlayer(player, target, ctx, hook).thenApply(ignored -> (Void) null);
    }

    /**
     * The {@code on_advance} hook and the party-member line (#2418) as one commit hook; either may be null. A
     * failing first hook does not cost the member the second.
     */
    static Consumer<UUID> both(Consumer<UUID> first, Consumer<UUID> second) {
        if (first == null) return second;
        if (second == null) return first;
        return uuid -> {
            try {
                first.accept(uuid);
            } finally {
                second.accept(uuid);
            }
        };
    }
}
