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

        String value = args[3];
        int amount = args.length >= 5 ? parseIntSafe(args[4], 1) : 1;

        String rewardId = questId + "_" + type.name().toLowerCase() + "_" + System.currentTimeMillis() % 10000;
        RewardDTO reward = RewardDTO.create(rewardId, type, value, amount);

        // RNG_ITEM uses value as pool_id — store in metadata where RngItemRewardProcessor reads it
        if (type == RewardType.RNG_ITEM) {
            reward = reward.withMetadata(Map.of("pool_id", value));
        }

        repo.addReward(questId, reward).thenAccept(success -> {
            if (success) {
                sendSuccessMessage(sender, "Added " + type + " reward to quest " + questId + " (id: " + rewardId + ")");
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
                sendSuccessMessage(sender, "Removed reward " + rewardId + " from quest " + questId);
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
}
