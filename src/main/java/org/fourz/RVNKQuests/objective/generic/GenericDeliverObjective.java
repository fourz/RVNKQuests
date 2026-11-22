package org.fourz.RVNKQuests.objective.generic;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.npc.NpcClickFilter;
import org.fourz.RVNKQuests.npc.NpcInteractionCoordinator;
import org.fourz.RVNKQuests.npc.NpcKeyRules;
import org.fourz.RVNKQuests.npc.NpcQuestComponent;
import org.fourz.RVNKQuests.quest.ComponentAdvance;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * DELIVER objective (#2390): click the RVNK-keyed NPC while holding the right item in the main
 * hand. The item is taken and the step advances.
 *
 * <h3>Config keys</h3>
 * <ul>
 *   <li>{@code npc_key} — the RVNK NPC key (required, {@code [a-z0-9_-]{1,48}}, any case)</li>
 *   <li>{@code item_type}, {@code custom_name} / {@code item_name}, {@code lore_item_id} — the item;
 *       at least one is required and every one given must match. See {@link DeliverItemMatch}.</li>
 *   <li>{@code amount} — how many are taken, default 1, at least 1. The main-hand stack must hold
 *       that many.</li>
 *   <li>{@code click} — {@code right} (default), {@code left} or {@code any}</li>
 *   <li>{@code wrong_item_message} — optional line for a click at this beat with the wrong item or an
 *       empty hand; {@code &} colour codes allowed. Sent through the NPC coordinator, so the click
 *       still gives one line.</li>
 *   <li>{@code required_state}, {@code advance_state}, {@code requires_path} / {@code sets_path},
 *       {@code description}, {@code on_advance}, {@code waypoint} — as on TALK_TO</li>
 * </ul>
 *
 * <h3>Taking the item</h3>
 * <p>The item is taken only after {@link ComponentAdvance#tryAdvance} returns true. A refused
 * advance (prerequisite gate, monotonic guard, a concurrent move) takes nothing. One click takes
 * at most one delivery across every DELIVER component that hears it, and a second click while the
 * first advance is still in flight is ignored, so one hand-in never costs two items.</p>
 *
 * <p>The event carries no hand, and Citizens raises one right-click per interaction (main hand).
 * This component reads only {@code getItemInMainHand()}, never the off hand.</p>
 */
public class GenericDeliverObjective implements Listener, NpcQuestComponent {

    public static final String TYPE_NAME = "DELIVER";

    private static final Set<String> KNOWN_KEYS = Set.of(
        "objective_type", "npc_key", "click", "item_type", "item_name", "custom_name", "lore_item_id",
        "amount", "wrong_item_message", "required_state", "advance_state", "requires_path", "sets_path",
        "description", "waypoint", org.fourz.RVNKQuests.reward.OnAdvanceRewards.CONFIG_KEY);

    /**
     * Clicks that already started a delivery. Shared by every DELIVER component: two quests on one
     * NPC that both want the held item must not both take it. Main thread only; weak, so a click
     * is dropped once Bukkit is done with it.
     */
    private static final Set<RvnkNpcInteractEvent> DELIVERED_CLICKS =
        Collections.newSetFromMap(new WeakHashMap<>());

    private final RVNKQuests plugin;
    private final DataDrivenQuest quest;
    private final LogManager logger;
    private final Executor mainThread;

    private final String npcKey;
    private final NpcClickFilter click;
    private final DeliverItemMatch item;
    private final int amount;
    private final String wrongItemMessage;
    private final QuestState requiredState;
    private final QuestState advanceState;
    private final String requiresPath;
    private final String setsPath;

    /** Players whose delivery advance has not resolved yet. */
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public GenericDeliverObjective(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config) {
        this(plugin, quest, config, org.fourz.RVNKQuests.util.AdvanceFeedback.mainThread(plugin));
    }

    /**
     * @param mainThread runs the take on the server thread after the advance resolves
     * @throws IllegalArgumentException on no item key, a bad {@code item_type}, or {@code amount} below 1
     */
    public GenericDeliverObjective(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config,
                                   Executor mainThread) {
        this.plugin = plugin;
        this.quest = quest;
        this.logger = LogManager.getInstance(plugin, "GenericDeliverObjective");
        this.mainThread = mainThread;

        this.item = DeliverItemMatch.from(config);
        this.amount = QuestComponentFactory.getIntConfig(config, "amount", 1);
        if (this.amount < 1) {
            throw new IllegalArgumentException(TYPE_NAME + " amount must be 1 or more (got "
                + config.get("amount") + ")");
        }

        String rawKey = QuestComponentFactory.getStringConfig(config, "npc_key", null);
        if (NpcKeyRules.isValid(rawKey)) {
            this.npcKey = NpcKeyRules.normalize(rawKey);
        } else {
            // Same as TALK_TO: an invalid key makes the component inert with a warning, so a quest
            // load never fails on it. quest component add rejects it (npcKeyProblem).
            this.npcKey = null;
            logger.warning("Quest '" + questId() + "': " + TYPE_NAME + " npc_key "
                + (rawKey == null ? "is missing" : "'" + rawKey + "' is invalid")
                + " (needs " + NpcKeyRules.KEY_FORMAT + ") - this objective can never complete");
        }

        String clickRaw = QuestComponentFactory.getStringConfig(config, "click", null);
        NpcClickFilter parsed = NpcClickFilter.parse(clickRaw);
        if (parsed == null) {
            logger.warning("Quest '" + questId() + "': " + TYPE_NAME + " click '" + clickRaw
                + "' is not any/right/left - using right");
            parsed = NpcClickFilter.RIGHT;
        }
        this.click = parsed;

        String wrong = QuestComponentFactory.getStringConfig(config, "wrong_item_message", null);
        this.wrongItemMessage = wrong == null || wrong.isBlank() ? null : wrong;

        this.requiredState = parseState(
            QuestComponentFactory.getStringConfig(config, "required_state", "QUEST_ACTIVE"), QuestState.QUEST_ACTIVE);
        this.advanceState = parseState(
            QuestComponentFactory.getStringConfig(config, "advance_state", "OBJECTIVE_FOUND"), QuestState.OBJECTIVE_FOUND);
        this.requiresPath = QuestComponentFactory.getStringConfig(config, "requires_path", null);
        this.setsPath = QuestComponentFactory.getStringConfig(config, "sets_path", null);

        for (String key : config.keySet()) {
            if (!KNOWN_KEYS.contains(key)) {
                logger.warning("Quest '" + questId() + "': " + TYPE_NAME + " ignores unknown config key '"
                    + key + "' - it will have no effect");
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onNpcInteract(RvnkNpcInteractEvent event) {
        if (!matches(event)) return;

        Player player = event.getPlayer();
        if (player == null) return;

        if (requiresPath != null && !requiresPath.equals(quest.getPathChoiceCached(player))) return;

        QuestState current = quest.getStateForPlayer(player);
        if (current != requiredState) {
            logger.debug("DELIVER gate: " + player.getName() + " quest=" + questId() + " npc=" + npcKey
                + " current=" + current + " required=" + requiredState + " - not delivered");
            return;
        }

        UUID playerId = player.getUniqueId();
        if (DELIVERED_CLICKS.contains(event) || inFlight.contains(playerId)) {
            logger.debug("DELIVER: " + player.getName() + " click already used for a delivery - ignored");
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack hand = inventory != null ? inventory.getItemInMainHand() : null;
        if (!item.matches(hand) || hand.getAmount() < amount) {
            logger.debug("DELIVER: " + player.getName() + " quest=" + questId() + " npc=" + npcKey
                + " held " + describe(hand) + ", wants " + amount + "x " + item.describe() + " - kept, not advanced");
            NpcInteractionCoordinator coordinator = plugin.getNpcCoordinator();
            if (wrongItemMessage != null && coordinator != null) {
                coordinator.recordWrongItem(event, wrongItemMessage);
            }
            return;
        }

        DELIVERED_CLICKS.add(event);
        inFlight.add(playerId);

        if (setsPath != null) {
            quest.setPathChoice(player, setsPath);
        }
        Location checkpoint = event.getLocation() != null ? event.getLocation() : player.getLocation();
        CompletableFuture<Boolean> advance;
        try {
            advance = ComponentAdvance.tryAdvance(quest, this, playerId, advanceState,
                checkpoint != null
                    ? org.fourz.RVNKQuests.party.PartyBeatContext.of(checkpoint, 0.0, requiredState)
                    : null);
        } catch (RuntimeException e) {
            inFlight.remove(playerId);
            throw e;
        }
        if (advance == null) {
            advance = CompletableFuture.completedFuture(false);
        }

        NpcInteractionCoordinator coordinator = plugin.getNpcCoordinator();
        if (coordinator != null) {
            coordinator.recordAdvance(event, this, advanceState, advance);
        }

        advance.whenComplete((committed, ex) -> {
            boolean take = ex == null && Boolean.TRUE.equals(committed);
            try {
                mainThread.execute(() -> {
                    try {
                        if (take) takeItems(player);
                    } finally {
                        inFlight.remove(playerId);
                    }
                });
            } catch (RuntimeException e) {
                // The scheduler refuses tasks while the plugin disables.
                inFlight.remove(playerId);
                if (take) {
                    logger.warning("DELIVER: " + player.getName() + " advanced quest " + questId()
                        + " but the item could not be taken: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Takes {@link #amount} matching items: from the main hand first, then from the rest of the
     * inventory if the player moved the item before the advance resolved. Main thread.
     *
     * @return how many were taken
     */
    int takeItems(Player player) {
        PlayerInventory inventory = player.getInventory();
        if (inventory == null) return 0;
        int remaining = amount;

        ItemStack hand = inventory.getItemInMainHand();
        if (item.matches(hand)) {
            int take = Math.min(hand.getAmount(), remaining);
            int left = hand.getAmount() - take;
            if (left <= 0) {
                inventory.setItemInMainHand(null);
            } else {
                hand.setAmount(left);
                inventory.setItemInMainHand(hand);
            }
            remaining -= take;
        }

        if (remaining > 0) {
            ItemStack[] contents = inventory.getContents();
            for (int slot = 0; contents != null && slot < contents.length && remaining > 0; slot++) {
                ItemStack stack = contents[slot];
                if (!item.matches(stack)) continue;
                int take = Math.min(stack.getAmount(), remaining);
                int left = stack.getAmount() - take;
                if (left <= 0) {
                    inventory.setItem(slot, null);
                } else {
                    stack.setAmount(left);
                    inventory.setItem(slot, stack);
                }
                remaining -= take;
            }
        }

        int taken = amount - remaining;
        if (remaining > 0) {
            logger.warning("DELIVER: " + player.getName() + " advanced quest " + questId() + " at NPC '"
                + npcKey + "' but only " + taken + " of " + amount + " " + item.describe()
                + " were left to take");
        } else {
            logger.debug("DELIVER: took " + taken + "x " + item.describe() + " from " + player.getName()
                + " for quest " + questId() + " -> " + advanceState);
        }
        return taken;
    }

    private static String describe(ItemStack stack) {
        if (stack == null || stack.getType() == null) return "nothing";
        return stack.getAmount() + "x " + stack.getType().name();
    }

    // ==================== NpcQuestComponent ====================

    @Override
    public DataDrivenQuest getQuest() {
        return quest;
    }

    @Override
    public String getNpcKey() {
        return npcKey;
    }

    @Override
    public boolean isTrigger() {
        return false;
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    public boolean acceptsClick(RvnkNpcInteractEvent.ClickType clickType) {
        return click.accepts(clickType);
    }

    public DeliverItemMatch getItemMatch() {
        return item;
    }

    public int getAmount() {
        return amount;
    }

    public String getWrongItemMessage() {
        return wrongItemMessage;
    }

    public QuestState getRequiredState() {
        return requiredState;
    }

    public QuestState getAdvanceState() {
        return advanceState;
    }

    private String questId() {
        return quest != null ? quest.getId() : "?";
    }

    private QuestState parseState(String name, QuestState fallback) {
        try {
            return QuestState.valueOf(name);
        } catch (IllegalArgumentException e) {
            logger.warning("Quest '" + questId() + "': unknown quest state '" + name
                + "' - falling back to " + fallback);
            return fallback;
        }
    }
}
