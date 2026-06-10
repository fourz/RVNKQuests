package org.fourz.RVNKQuests.command;

import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.IQuestRepository;
import org.fourz.RVNKQuests.data.dto.ObjectiveDTO;
import org.fourz.RVNKQuests.data.dto.ObjectiveType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /quest def-objective <add|del|list> <quest_id> [obj_id] [type] [target] [amount] [description]
 *
 * Manages the quest definition objectives table (journal UI entries), distinct from
 * the player-progress objective command (/quest objective) which edits per-player state.
 *
 * <ul>
 *   <li>add  — quest def-objective add <quest_id> <obj_id> <type> <target> <amount> [desc]</li>
 *   <li>del  — quest def-objective del <quest_id> <obj_id></li>
 *   <li>list — quest def-objective list <quest_id></li>
 * </ul>
 */
public class QuestDefObjectiveSubCommand extends BaseSubCommand {

    private static final List<String> ACTIONS = List.of("add", "del", "list");

    public QuestDefObjectiveSubCommand(RVNKQuests plugin) {
        super(plugin, "def-objective", "Manage quest definition objectives (journal entries)",
              "/quest def-objective <add|del|list> <quest_id> [obj_id] [type] [target] [amount] [desc]",
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
            case "list" -> handleList(sender, questId, repo);
            case "add"  -> handleAdd(sender, questId, args, repo);
            case "del"  -> handleDel(sender, questId, args, repo);
            default     -> sendErrorMessage(sender, "Unknown action: " + action + ". Use add, del, or list.");
        }

        return true;
    }

    private void handleList(CommandSender sender, String questId, IQuestRepository repo) {
        repo.findObjectives(questId).thenAccept(objectives -> {
            if (objectives.isEmpty()) {
                sendInfoMessage(sender, "No objectives defined for quest " + questId);
                return;
            }
            sendMessage(sender, "&6Objectives for &f" + questId + " &7(" + objectives.size() + "):");
            for (ObjectiveDTO obj : objectives) {
                sendMessage(sender, "&7  &f" + obj.objectiveId() + " &7| " + obj.type()
                    + " | target=&f" + obj.target() + " &7| amount=&f" + obj.requiredAmount()
                    + " &7| " + obj.description());
            }
        });
    }

    private void handleAdd(CommandSender sender, String questId, String[] args, IQuestRepository repo) {
        // quest def-objective add <quest_id> <obj_id> <type> <target> <amount> [desc...]
        if (args.length < 6) {
            sendMessage(sender, "&c▶ Usage: /quest def-objective add <quest_id> <obj_id> <type> <target> <amount> [description]");
            sendMessage(sender, "&7   Types: " + Arrays.stream(ObjectiveType.values()).map(Enum::name).collect(Collectors.joining(", ")));
            return;
        }

        String objId = args[2];
        ObjectiveType type;
        try {
            type = ObjectiveType.valueOf(args[3].toUpperCase());
        } catch (IllegalArgumentException e) {
            sendErrorMessage(sender, "Unknown objective type: " + args[3] +
                ". Valid: " + Arrays.stream(ObjectiveType.values()).map(Enum::name).collect(Collectors.joining(", ")));
            return;
        }

        String target = args[4];
        int amount;
        try {
            amount = Integer.parseInt(args[5]);
        } catch (NumberFormatException e) {
            sendErrorMessage(sender, "Amount must be a number, got: " + args[5]);
            return;
        }

        String description = args.length > 6 ? String.join(" ", Arrays.copyOfRange(args, 6, args.length)) : type.name() + " " + target;

        ObjectiveDTO objective = ObjectiveDTO.create(objId, type, target, amount)
            .withDescription(description);

        repo.addObjective(questId, objective).thenAccept(success -> {
            if (success) {
                sendSuccessMessage(sender, "Added objective '" + objId + "' (" + type + ") to quest " + questId);
            } else {
                sendErrorMessage(sender, "Failed to add objective. Does quest '" + questId + "' exist?");
            }
        });
    }

    private void handleDel(CommandSender sender, String questId, String[] args, IQuestRepository repo) {
        if (args.length < 3) {
            sendMessage(sender, "&c▶ Usage: /quest def-objective del <quest_id> <obj_id>");
            return;
        }
        String objId = args[2];
        repo.removeObjective(questId, objId).thenAccept(success -> {
            if (success) {
                sendSuccessMessage(sender, "Removed objective '" + objId + "' from quest " + questId);
            } else {
                sendErrorMessage(sender, "Objective '" + objId + "' not found for quest " + questId);
            }
        });
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return ACTIONS.stream().filter(a -> a.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2) {
            return plugin.getQuestManager().getQuestIds().stream()
                .filter(id -> id.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("add")) {
            return Arrays.stream(ObjectiveType.values())
                .map(Enum::name)
                .filter(t -> t.startsWith(args[3].toUpperCase()))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
