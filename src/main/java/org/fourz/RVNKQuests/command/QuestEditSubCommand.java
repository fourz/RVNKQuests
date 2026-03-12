package org.fourz.RVNKQuests.command;

import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.IQuestRepository;
import org.fourz.RVNKQuests.data.dto.QuestDTO;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /quest edit <id> <property> <value> — update quest definition properties.
 * Properties: description, category, repeatable, cooldown
 */
public class QuestEditSubCommand extends BaseSubCommand {

    private static final List<String> PROPERTIES = List.of("description", "category", "repeatable", "cooldown");

    public QuestEditSubCommand(RVNKQuests plugin) {
        super(plugin, "edit", "Edit a quest definition property",
              "/quest edit <id> <description|category|repeatable|cooldown> <value>",
              "rvnkquests.admin.edit", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args, 3)) return true;

        String questId = args[0];
        String property = args[1].toLowerCase();
        String value = stripQuotes(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));

        if (!PROPERTIES.contains(property)) {
            sendErrorMessage(sender, "Unknown property: " + property + ". Valid: " + String.join(", ", PROPERTIES));
            return true;
        }

        IQuestRepository repo = plugin.getQuestRepository();
        if (repo == null) {
            sendErrorMessage(sender, "Quest repository not available.");
            return true;
        }

        repo.findById(questId).thenAccept(opt -> {
            if (opt.isEmpty()) {
                sendErrorMessage(sender, "Quest not found: " + questId);
                return;
            }

            QuestDTO quest = opt.get();
            QuestDTO updated = switch (property) {
                case "description" -> new QuestDTO(quest.questId(), quest.name(), value, quest.category(),
                    quest.repeatable(), quest.cooldownMinutes(), quest.objectives(), quest.rewards(),
                    quest.prerequisites(), quest.createdAt(), quest.metadata());
                case "category" -> quest.withCategory(value);
                case "repeatable" -> quest.asRepeatable(quest.cooldownMinutes())
                    .asRepeatable(Boolean.parseBoolean(value) ? quest.cooldownMinutes() : 0);
                case "cooldown" -> {
                    try {
                        int minutes = Integer.parseInt(value);
                        yield new QuestDTO(quest.questId(), quest.name(), quest.description(), quest.category(),
                            quest.repeatable(), minutes, quest.objectives(), quest.rewards(),
                            quest.prerequisites(), quest.createdAt(), quest.metadata());
                    } catch (NumberFormatException e) {
                        sendErrorMessage(sender, "Cooldown must be a number (minutes).");
                        yield null;
                    }
                }
                default -> null;
            };

            if (updated == null) return;

            repo.save(updated).thenAccept(success -> {
                if (success) {
                    // Hot-reload the quest so in-memory state matches DB
                    plugin.getQuestManager().reloadQuest(questId);
                    sendSuccessMessage(sender, "Updated " + property + " for quest " + questId);
                } else {
                    sendErrorMessage(sender, "Failed to save changes.");
                }
            });
        });

        return true;
    }

    private String stripQuotes(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return plugin.getQuestManager().getQuestIds().stream()
                .filter(id -> id.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return PROPERTIES.stream()
                .filter(p -> p.startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("repeatable")) {
            return List.of("true", "false");
        }
        return Collections.emptyList();
    }
}
