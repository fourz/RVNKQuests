package org.fourz.RVNKQuests.command;

import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.IOnceRewardStore;
import org.fourz.RVNKQuests.data.IQuestRepository;
import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.data.dto.RewardType;
import org.fourz.RVNKQuests.service.OnceRewards;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * /quest reward add <quest_id> <type> <value> [amount] — add a reward to a quest.
 * /quest reward remove <quest_id> <reward_id> — remove a reward from a quest.
 * /quest reward list <quest_id> — list rewards, with once:server state.
 * /quest reward reset-once <quest_id> [reward_id] — let once:server rewards fire again (#2268).
 */
public class QuestRewardSubCommand extends BaseSubCommand {

    public QuestRewardSubCommand(RVNKQuests plugin) {
        super(plugin, "reward", "Manage quest rewards",
              "/quest reward <add|remove|list|reset-once> <quest_id> ...",
              "rvnkquests.admin.edit", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args, 2)) return true;

        String action = args[0].toLowerCase();
        String questId = args[1];

        if (action.equals("reset-once")) {
            handleResetOnce(sender, questId, args.length > 2 ? args[2] : null);
            return true;
        }

        IQuestRepository repo = plugin.getQuestRepository();
        if (repo == null) {
            sendErrorMessage(sender, "Quest repository not available.");
            return true;
        }

        switch (action) {
            case "add" -> handleAdd(sender, questId, args, repo);
            case "remove" -> handleRemove(sender, questId, args, repo);
            case "list" -> handleList(sender, questId, repo);
            default -> sendErrorMessage(sender, "Unknown action: " + action
                + ". Use 'add', 'remove', 'list' or 'reset-once'.");
        }

        return true;
    }

    private void handleAdd(CommandSender sender, String questId, String[] args, IQuestRepository repo) {
        // /quest reward add <quest_id> <type> <value> [amount]
        if (args.length < 4) {
            sendMessage(sender, "&c\u25b6 Usage: /quest reward add <quest_id> <type> <value> [amount]");
            return;
        }

        RewardType type;
        try {
            type = RewardType.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            sendErrorMessage(sender, "Unknown reward type: " + args[2] +
                ". Valid: " + Arrays.stream(RewardType.values()).map(Enum::name).collect(Collectors.joining(", ")));
            return;
        }

        // COMMAND reward values are full command lines (spaces + placeholders like %player%),
        // so join the remaining args; a single-token value silently truncated them (#reward-command).
        // Other value-bearing types (LORE_ITEM, ITEM) can also be multi-word — e.g.
        // "The Book of Open Gates" was truncated to "The" by taking args[3] alone (#1640). Treat a
        // trailing integer as the optional [amount] and join everything before it as the value.
        String value;
        int amount;
        if (type == RewardType.COMMAND) {
            value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            amount = 1;
        } else {
            int end = args.length;
            amount = 1;
            if (args.length > 4 && isInteger(args[args.length - 1])) {
                amount = parseIntSafe(args[args.length - 1], 1);
                end = args.length - 1;
            }
            value = String.join(" ", Arrays.copyOfRange(args, 3, end));
        }

        String rewardId = questId + "_" + type.name().toLowerCase() + "_" + System.currentTimeMillis() % 10000;
        RewardDTO reward = RewardDTO.create(rewardId, type, value, amount);

        // RNG_ITEM uses value as pool_id — store in metadata where RngItemRewardProcessor reads it
        if (type == RewardType.RNG_ITEM) {
            reward = reward.withMetadata(Map.of("pool_id", value));
        }
        // LORE reads metadata["loreId"], not value — same shape as RNG_ITEM above (#1650). Without
        // this mirror, every LORE reward created through this command failed delivery with
        // INVALID_LORE_ID, because the command wrote one field and the processor read another.
        // That is almost certainly why no quest in the repo had a LORE reward authored.
        if (type == RewardType.LORE) {
            reward = reward.withMetadata(Map.of("loreId", value));
        }

        final String finalValue = value;
        final int finalAmount = amount;
        repo.addReward(questId, reward).thenAccept(success -> {
            if (success) {
                // Refresh the live quest so onComplete sees the new reward — the in-memory
                // DataDrivenQuest.definition is final and otherwise keeps its stale reward list,
                // so a reward added mid-session was silently never delivered (#1640).
                plugin.getQuestManager().reloadQuest(questId);
                sendSuccessMessage(sender, "Added " + type + " reward to quest " + questId
                    + " (value: '" + finalValue + "', amount: " + finalAmount + ", id: " + rewardId + ") (hot-reloaded)");
            } else {
                sendErrorMessage(sender, "Failed to add reward. Does quest '" + questId + "' exist?");
            }
        });
    }

    private void handleList(CommandSender sender, String questId, IQuestRepository repo) {
        repo.findRewards(questId).thenAccept(rewards -> {
            if (rewards.isEmpty()) {
                sendMessage(sender, "&eNo rewards configured for quest " + questId);
                return;
            }
            sendMessage(sender, "&6Rewards for &f" + questId + " &7(" + rewards.size() + "):");
            for (RewardDTO reward : rewards) {
                sendMessage(sender, "&7  - &f" + reward.rewardId() + " &7| " + reward.type() + " &7| value=&f" + reward.value() + " &7| amount=&f" + reward.amount()
                    + (OnceRewards.isOnceServer(reward) ? " &e[once: server]" : ""));
            }
            listFiredOnce(sender, questId);
        });
    }

    /** Shows which once:server rewards have fired on this server (#2268). */
    private void listFiredOnce(CommandSender sender, String questId) {
        IOnceRewardStore store = plugin.getOnceRewardStore();
        if (store == null) return;
        store.list(questId).whenComplete((records, ex) -> {
            if (ex != null) {
                sendErrorMessage(sender, "Could not read once:server records: " + ex.getMessage());
                return;
            }
            if (records.isEmpty()) return;
            sendMessage(sender, "&6Fired once:server rewards &7(" + records.size() + "):");
            for (IOnceRewardStore.OnceRecord r : records) {
                sendMessage(sender, "&7  - &f" + r.rewardKey() + " &7by &f" + r.firedBy()
                    + " &7at &f" + java.time.Instant.ofEpochMilli(r.firedAtMillis()));
            }
        });
    }

    /**
     * {@code /quest reward reset-once <quest_id> [reward_id]} (#2268): forget that once:server
     * rewards fired, so the next completion fires them again. Console-safe. It changes no world
     * state and gives nothing; it only clears records.
     */
    private void handleResetOnce(CommandSender sender, String questId, String rewardKey) {
        IOnceRewardStore store = plugin.getOnceRewardStore();
        if (store == null) {
            sendErrorMessage(sender, "Once-reward store is not available.");
            return;
        }
        store.reset(questId, rewardKey).whenComplete((removed, ex) -> {
            if (ex != null) {
                sendErrorMessage(sender, "reset-once failed: " + ex.getMessage());
                return;
            }
            String what = rewardKey == null ? "every once:server reward of " + questId
                : "once:server reward " + rewardKey + " of " + questId;
            if (removed == 0) {
                sendMessage(sender, "&e⚠ No fired record for " + what + " - nothing to reset.");
            } else {
                sendSuccessMessage(sender, "Cleared " + removed + " record(s) for " + what
                    + ". The next completion fires it again.");
            }
            plugin.getLogger().info("[quest reward reset-once] sender=" + sender.getName() + " quest=" + questId
                + " reward=" + (rewardKey == null ? "*" : rewardKey) + " removed=" + removed);
        });
    }

    /** Store keys of the once:server rewards a live quest declares, for tab completion. */
    private List<String> onceKeys(String questId) {
        List<String> keys = new java.util.ArrayList<>();
        java.util.Optional<org.fourz.RVNKQuests.quest.Quest> quest = plugin.getQuestManager().getQuest(questId);
        if (quest.isEmpty() || !(quest.get() instanceof org.fourz.RVNKQuests.quest.DataDrivenQuest ddq)) {
            return keys;
        }
        for (RewardDTO r : ddq.getDefinition().rewards()) {
            if (OnceRewards.isOnceServer(r)) keys.add(OnceRewards.key(null, r.rewardId()));
        }
        Object components = ddq.getDefinition().metadata().get("components");
        if (components instanceof Map<?, ?> map) {
            for (Object componentId : map.keySet()) {
                for (RewardDTO r : ddq.getOnAdvanceRewards(String.valueOf(componentId))) {
                    if (OnceRewards.isOnceServer(r)) keys.add(OnceRewards.key(String.valueOf(componentId), r.rewardId()));
                }
            }
        }
        return keys;
    }

    private void handleRemove(CommandSender sender, String questId, String[] args, IQuestRepository repo) {
        // /quest reward remove <quest_id> <reward_id>
        if (args.length < 3) {
            sendMessage(sender, "&c\u25b6 Usage: /quest reward remove <quest_id> <reward_id>");
            return;
        }

        String rewardId = args[2];
        repo.removeReward(questId, rewardId).thenAccept(success -> {
            if (success) {
                // Refresh the live quest so the removed reward stops being delivered (#1640).
                plugin.getQuestManager().reloadQuest(questId);
                sendSuccessMessage(sender, "Removed reward " + rewardId + " from quest " + questId + " (hot-reloaded)");
            } else {
                sendErrorMessage(sender, "Reward not found: " + rewardId);
            }
        });
    }

    private int parseIntSafe(String s, int defaultValue) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean isInteger(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("add", "remove", "list", "reset-once").stream()
                .filter(a -> a.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return plugin.getQuestManager().getQuestIds().stream()
                .filter(id -> id.startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("reset-once")) {
            return onceKeys(args[1]).stream()
                .filter(k -> k.toLowerCase().startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("add")) {
            return Arrays.stream(RewardType.values())
                .map(Enum::name)
                .filter(t -> t.startsWith(args[2].toUpperCase()))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * Worked examples for {@code /quest help reward} (#1981).
     */
    @Override
    public java.util.List<String> getExamples() {
        return java.util.List.of(
                "/quest reward add tfah_ch1_journey ITEM diamond 3",
                "  <quest_id> <type> <value> [amount]",
                "/quest reward add tfah_ch1_journey EXPERIENCE 500",
                "/quest reward remove tfah_ch1_journey 4",
                "  remove takes the reward id, not the type",
                "/quest reward reset-once tfah_cavern",
                "/quest reward reset-once tfah_cavern cavern_wake",
                "  forgets that once:server rewards fired, so the next completion fires them again",
                "  an on_advance entry's id is <component>/<reward_id>",
                "Rewards fire from advanceStateForPlayer(COMPLETED), so they land whether the",
                "quest was completed by a trigger component or by /quest complete.");
    }
}
