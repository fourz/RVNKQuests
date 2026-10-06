package org.fourz.RVNKQuests.npc;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent;
import org.fourz.rvnkcore.api.service.INpcService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The one RVNKQuests listener that decides what an NPC says (#2214).
 *
 * <h2>Why one central listener</h2>
 *
 * <p>Every {@code NPC_INTERACT} trigger and {@code TALK_TO} objective is its own Bukkit listener,
 * registered like every other quest component. One click on a keyed NPC therefore reaches every
 * component for that key, across every quest. If each sent its own dialogue line, one click on an
 * NPC shared by three quests would print three lines. So components never speak. They report
 * the advance they started with {@link #recordAdvance}, and this listener, at
 * {@link EventPriority#MONITOR} (after every component), sends at most <b>one</b> line for the
 * click.</p>
 *
 * <h2>Which line</h2>
 *
 * <ol>
 *   <li>The advances recorded for the click are awaited. An advance that did not commit (the
 *       prerequisite gate refused it, or the state already moved) does not count.</li>
 *   <li>Each committed advance gives a context: {@code done} if it reached COMPLETED, else
 *       {@code offer} for a trigger and {@code active} for an objective. The highest wins:
 *       {@code done} &gt; {@code offer} &gt; {@code active}.</li>
 *   <li>With no committed advance, the fallback looks at every quest with a component for this
 *       key and click: {@code active} if the player has one in progress, else {@code done} if
 *       they completed one. A quest the player has not started gives nothing, so a click never
 *       advertises a quest the player cannot take yet.</li>
 *   <li>The line is the RVNKLore entry {@code npc_<key>_<context>}. No entry, or no RVNKLore,
 *       means silence; the quest action has already happened either way.</li>
 * </ol>
 *
 * <h2>Key validation</h2>
 *
 * <p>{@link #requestKeyValidation()} runs a debounced check after quests register: every NPC
 * component key is looked up in {@link INpcService}. Unknown keys get a warning naming the quest.
 * When the service is missing or has no NPC plugin behind it, one warning is logged for the
 * plugin's lifetime, not one per component.</p>
 */
public class NpcInteractionCoordinator implements Listener {

    /** Reads one dialogue line; empty when there is no entry or no RVNKLore. */
    @FunctionalInterface
    public interface DialogueSource {
        CompletableFuture<Optional<String>> lookup(String npcKey, String context);
    }

    /** One advance a component started for a click. */
    private record Claim(NpcQuestComponent component, QuestState target, CompletableFuture<Void> advance) {}

    private final Supplier<? extends Collection<? extends NpcQuestComponent>> components;
    private final Supplier<INpcService> npcService;
    private final DialogueSource dialogue;
    private final Executor mainThread;
    private final Consumer<Runnable> delayedTask;
    private final Consumer<String> warn;
    private final Consumer<String> debug;

    /** Claims per click. Main thread only: the event and every component handler run there. */
    private final Map<RvnkNpcInteractEvent, List<Claim>> pending = new IdentityHashMap<>();

    private final AtomicBoolean validationScheduled = new AtomicBoolean(false);
    private final AtomicBoolean unavailableWarned = new AtomicBoolean(false);

    /**
     * @param components  the live NPC components, walked from the quest list on each call
     * @param npcService  RVNKCore's NPC service; may supply null
     * @param dialogue    the RVNKLore dialogue lookup
     * @param mainThread  runs a task on the server thread
     * @param delayedTask runs a task on the server thread a few seconds later (key validation)
     * @param warn        warning log sink
     * @param debug       debug log sink
     */
    public NpcInteractionCoordinator(Supplier<? extends Collection<? extends NpcQuestComponent>> components,
                                     Supplier<INpcService> npcService,
                                     DialogueSource dialogue,
                                     Executor mainThread,
                                     Consumer<Runnable> delayedTask,
                                     Consumer<String> warn,
                                     Consumer<String> debug) {
        this.components = components;
        this.npcService = npcService;
        this.dialogue = dialogue;
        this.mainThread = mainThread;
        this.delayedTask = delayedTask;
        this.warn = warn != null ? warn : msg -> { };
        this.debug = debug != null ? debug : msg -> { };
    }

    // ==================== Claims (called by components) ====================

    /**
     * Records that a component started an advance for this click. Call from the component's
     * event handler, on the main thread, right after {@code advanceStateForPlayer}.
     *
     * @param event     the click
     * @param component the component that fired
     * @param target    the state the component advanced to
     * @param advance   the future {@code advanceStateForPlayer} returned
     */
    public void recordAdvance(RvnkNpcInteractEvent event, NpcQuestComponent component,
                              QuestState target, CompletableFuture<Void> advance) {
        if (event == null || component == null || target == null) return;
        pending.computeIfAbsent(event, e -> new ArrayList<>(2))
            .add(new Claim(component, target, advance != null ? advance : CompletableFuture.completedFuture(null)));
    }

    /** Number of clicks with unresolved claims. Visible for tests: must be 0 after each click. */
    int pendingClicks() {
        return pending.size();
    }

    // ==================== The click ====================

    /**
     * Runs after every component. Not {@code ignoreCancelled}: the claims for the click must be
     * cleared even when another plugin cancelled it. A cancelled click sends no line.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onNpcInteract(RvnkNpcInteractEvent event) {
        List<Claim> claims = pending.remove(event);
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        String key = NpcKeyRules.normalize(event.getNpcKey());
        if (player == null || key == null) return;

        if (claims == null || claims.isEmpty()) {
            resolveFallback(player, key, event.getClickType())
                .ifPresent(context -> speak(player, key, event.getNpcName(), context));
            return;
        }

        CompletableFuture<?>[] futures = claims.stream().map(Claim::advance).toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).handle((v, ex) -> null).thenRun(() -> mainThread.execute(() -> {
            DialogueContext context = contextFromClaims(player, claims);
            if (context == null) {
                context = resolveFallback(player, key, event.getClickType()).orElse(null);
            }
            if (context != null) {
                speak(player, key, event.getNpcName(), context);
            }
        }));
    }

    /**
     * The context of the committed advances, or null when none committed. An advance committed
     * when the player's cached state is now its target; a refused advance leaves the old state.
     */
    DialogueContext contextFromClaims(Player player, List<Claim> claims) {
        DialogueContext best = null;
        for (Claim claim : claims) {
            if (claim.advance().isCompletedExceptionally()) continue;
            DataDrivenQuest quest = claim.component().getQuest();
            if (quest == null || quest.getStateForPlayer(player) != claim.target()) continue;
            DialogueContext context;
            if (claim.target() == QuestState.COMPLETED) {
                context = DialogueContext.DONE;
            } else {
                context = claim.component().isTrigger() ? DialogueContext.OFFER : DialogueContext.ACTIVE;
            }
            best = DialogueContext.max(best, context);
        }
        return best;
    }

    /**
     * The context when no component advanced anything: {@code active} for a quest in progress,
     * else {@code done} for a completed one, else empty.
     */
    Optional<DialogueContext> resolveFallback(Player player, String key, RvnkNpcInteractEvent.ClickType click) {
        boolean active = false;
        boolean done = false;
        for (NpcQuestComponent component : safeComponents()) {
            if (!component.hasValidKey() || !NpcKeyRules.matches(component.getNpcKey(), key)) continue;
            if (!component.acceptsClick(click)) continue;
            DataDrivenQuest quest = component.getQuest();
            if (quest == null) continue;
            QuestState state = quest.getStateForPlayer(player);
            if (isInProgress(state)) {
                active = true;
            } else if (state == QuestState.COMPLETED) {
                done = true;
            }
        }
        if (active) return Optional.of(DialogueContext.ACTIVE);
        if (done) return Optional.of(DialogueContext.DONE);
        return Optional.empty();
    }

    /** TRIGGER_FOUND, QUEST_ACTIVE or OBJECTIVE_FOUND. */
    public static boolean isInProgress(QuestState state) {
        return state == QuestState.TRIGGER_FOUND
            || state == QuestState.QUEST_ACTIVE
            || state == QuestState.OBJECTIVE_FOUND;
    }

    /** Looks up the line and sends it on the main thread. Silent on no entry or any failure. */
    private void speak(Player player, String key, String npcName, DialogueContext context) {
        CompletableFuture<Optional<String>> lookup;
        try {
            lookup = dialogue.lookup(key, context.loreSuffix());
        } catch (RuntimeException e) {
            debug.accept("NPC dialogue lookup failed for " + context.loreEntryName(key) + ": " + e.getMessage());
            return;
        }
        if (lookup == null) return;
        lookup.whenComplete((line, ex) -> {
            if (ex != null || line == null || line.isEmpty() || line.get().isBlank()) {
                debug.accept("No NPC dialogue for " + context.loreEntryName(key) + " - staying silent");
                return;
            }
            String message = formatLine(npcName, key, line.get());
            mainThread.execute(() -> {
                if (player.isOnline()) {
                    player.sendMessage(message);
                }
            });
        });
    }

    /** {@code <NPC name>: <line>}, with {@code &} colour codes in the lore text honoured. */
    static String formatLine(String npcName, String key, String line) {
        String name = npcName == null || npcName.isBlank() ? key : ChatColor.stripColor(npcName);
        return ChatColor.YELLOW + name + ChatColor.GRAY + ": " + ChatColor.WHITE
            + ChatColor.translateAlternateColorCodes('&', line);
    }

    // ==================== Key validation ====================

    /**
     * Schedules one key check a few seconds from now; further calls before it runs are merged.
     * The delay lets the NPC plugin finish loading its NPCs after server start.
     */
    public void requestKeyValidation() {
        if (!validationScheduled.compareAndSet(false, true)) return;
        try {
            delayedTask.accept(() -> {
                validationScheduled.set(false);
                validateKeysNow();
            });
        } catch (RuntimeException e) {
            validationScheduled.set(false);
            debug.accept("Could not schedule NPC key validation: " + e.getMessage());
        }
    }

    /** Checks every NPC component key against the NPC service and logs the problems. */
    public void validateKeysNow() {
        Collection<? extends NpcQuestComponent> current = safeComponents();
        if (current.isEmpty()) return;

        INpcService service = safeService();
        if (service == null || !service.isAvailable()) {
            if (unavailableWarned.compareAndSet(false, true)) {
                warn.accept(unavailableWarning(current));
            }
            return;
        }
        for (String warning : unknownKeyWarnings(current, service)) {
            warn.accept(warning);
        }
    }

    /** One warning for all quests when there is no NPC provider. */
    static String unavailableWarning(Collection<? extends NpcQuestComponent> components) {
        Set<String> quests = new LinkedHashSet<>();
        for (NpcQuestComponent c : components) {
            if (c.getQuest() != null) quests.add(c.getQuest().getId());
        }
        return "NPC service unavailable (RVNKCore has no NPC provider - is Citizens installed?): "
            + components.size() + " NPC_INTERACT/TALK_TO component(s) in quest(s) "
            + String.join(", ", quests) + " cannot fire until it is";
    }

    /** One warning per quest and key that no NPC carries. */
    static List<String> unknownKeyWarnings(Collection<? extends NpcQuestComponent> components, INpcService service) {
        Map<String, String> warnings = new LinkedHashMap<>();
        for (NpcQuestComponent c : components) {
            if (!c.hasValidKey()) continue; // already warned at construction
            String questId = c.getQuest() != null ? c.getQuest().getId() : "?";
            String dedupe = questId + "|" + c.getNpcKey();
            if (warnings.containsKey(dedupe)) continue;
            boolean found;
            try {
                found = service.findByKey(c.getNpcKey()).isPresent();
            } catch (RuntimeException e) {
                found = false;
            }
            if (!found) {
                warnings.put(dedupe, "Quest '" + questId + "': " + c.getTypeName() + " npc_key '"
                    + c.getNpcKey() + "' is not on any NPC - tag one with /rvnk npc tag "
                    + c.getNpcKey() + " <npcId>");
            }
        }
        return new ArrayList<>(warnings.values());
    }

    /**
     * The NPC components of the given quests, each once. A component mapped under several states
     * is one cached instance, so identity de-duplicates it.
     */
    public static List<NpcQuestComponent> collect(Collection<? extends org.fourz.RVNKQuests.quest.Quest> quests) {
        List<NpcQuestComponent> out = new ArrayList<>();
        if (quests == null) return out;
        Set<NpcQuestComponent> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (org.fourz.RVNKQuests.quest.Quest quest : quests) {
            if (!(quest instanceof DataDrivenQuest dq)) continue;
            for (QuestState state : QuestState.values()) {
                for (org.bukkit.event.Listener listener : dq.createListenersForState(state)) {
                    if (listener instanceof NpcQuestComponent c && seen.add(c)) {
                        out.add(c);
                    }
                }
            }
        }
        return out;
    }

    private Collection<? extends NpcQuestComponent> safeComponents() {
        try {
            Collection<? extends NpcQuestComponent> list = components.get();
            return list != null ? list : List.of();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private INpcService safeService() {
        try {
            return npcService.get();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }
}
