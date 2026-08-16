package org.fourz.RVNKQuests.command;

import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.IQuestRepository;
import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.data.dto.RewardType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * /quest reward add <quest_id> <type> <value> [amount] — add a reward to a quest.
 * /quest reward remove <quest_id> <reward_id> — remove a reward from a quest.
 */
public class QuestRewardSubCommand extends BaseSubCommand {

    public QuestRewardSubCommand(RVNKQuests plugin) {
        super(plugin, "reward", "Manage quest rewards",
              "/quest reward <add|remove> <quest_id> ...",
              "rvnkquests.admin.edit", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args, 2)) return true;

        String action = args[0].toLowerCase();
        String questId = args[1];

        IQuestRepository repo = plugin.getQuestRepository();
        if (repo == null) {
            sendErrorMessage(sender, "Quest repository not available.");
            return true;
        }

        switch (action) {
            case "add" -> handleAdd(sender, questId, args, repo);
            case "remove" -> handleRemove(sender, questId, args, repo);
            case "list" -> handleList(sender, questId, repo);
            default -> sendErrorMessage(sender, "Unknown action: " + action + ". Use 'add', 'remove', or 'list'.");
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
                sendMessage(sender, "&7  - &f" + reward.rewardId() + " &7| " + reward.type() + " &7| value=&f" + reward.value() + " &7| amount=&f" + reward.amount());
            }
        });
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
            return List.of("add", "remove", "list").stream()
                .filter(a -> a.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return plugin.getQuestManager().getQuestIds().stream()
                .filter(id -> id.startsWith(args[1].toLowerCase()))
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
                "Rewards fire from advanceStateForPlayer(COMPLETED), so they land whether the",
                "quest was completed by a trigger component or by /quest complete.");
    }
}
