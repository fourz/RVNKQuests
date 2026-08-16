package org.fourz.RVNKQuests.command;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.IQuestRepository;
import org.fourz.RVNKQuests.data.dto.QuestDTO;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * /quest edit <id> <property> <value> — update quest definition properties.
 * Properties: description, category, repeatable, cooldown, metadata
 */
public class QuestEditSubCommand extends BaseSubCommand {

    private static final List<String> PROPERTIES = List.of("description", "category", "repeatable", "cooldown", "metadata", "prerequisite");
    private static final List<String> PREREQ_ACTIONS = List.of("add", "remove", "clear", "list");
    private static final Gson GSON = new Gson();
    private static final Type METADATA_TYPE = new TypeToken<Map<String, Object>>(){}.getType();

    public QuestEditSubCommand(RVNKQuests plugin) {
        super(plugin, "edit", "Edit a quest definition property",
              "/quest edit <id> <description|category|repeatable|cooldown|metadata|prerequisite> <value>",
              "rvnkquests.admin.edit", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args, 3)) return true;

        String questId = args[0];
        String property = args[1].toLowerCase();
        // metadata JSON may contain spaces — join everything from arg 2 onward
        String value = stripQuotes(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));

        if (!PROPERTIES.contains(property)) {
            sendErrorMessage(sender, "Unknown property: " + property + ". Valid: " + String.join(", ", PROPERTIES));
            return true;
        }

        // Metadata is handled separately — validate JSON before touching the repository
        if ("metadata".equals(property)) {
            handleMetadata(sender, questId, value);
            return true;
        }

        // Prerequisite has sub-actions (add/remove/clear/list) rather than a single value
        if ("prerequisite".equals(property) || "prerequisites".equals(property)) {
            handlePrerequisite(sender, questId, args);
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

    private void handleMetadata(CommandSender sender, String questId, String json) {
        Map<String, Object> newMeta;
        try {
            newMeta = GSON.fromJson(json, METADATA_TYPE);
        } catch (Exception e) {
            sendErrorMessage(sender, "Invalid JSON: " + e.getMessage());
            return;
        }
        if (newMeta == null || !newMeta.containsKey("state_mapping")) {
            sendErrorMessage(sender, "Metadata JSON must contain a 'state_mapping' key.");
            return;
        }

        IQuestRepository repo = plugin.getQuestRepository();
        if (repo == null) {
            sendErrorMessage(sender, "Quest repository not available.");
            return;
        }

        repo.findById(questId).thenAccept(opt -> {
            if (opt.isEmpty()) {
                sendErrorMessage(sender, "Quest not found: " + questId);
                return;
            }
            QuestDTO quest = opt.get();
            QuestDTO updated = new QuestDTO(quest.questId(), quest.name(), quest.description(),
                quest.category(), quest.repeatable(), quest.cooldownMinutes(),
                quest.objectives(), quest.rewards(), quest.prerequisites(),
                quest.createdAt(), newMeta);
            repo.save(updated).thenAccept(success -> {
                if (success) {
                    plugin.getQuestManager().reloadQuest(questId);
                    sendSuccessMessage(sender, "Metadata updated and hot-reloaded for quest " + questId);
                    sendInfoMessage(sender, "Components: " + (newMeta.containsKey("components") ? ((Map<?,?>) newMeta.get("components")).size() + " defined" : "none"));
                } else {
                    sendErrorMessage(sender, "Failed to save metadata.");
                }
            });
        });
    }

    /**
     * Sets quest prerequisites directly, bypassing the YAML export→edit→import round-trip
     * that silently drops the field on schema-drifted servers (#1425).
     *
     * <p>Usage: {@code /quest edit <id> prerequisite <add|remove|clear|list> [questId]}</p>
     */
    private void handlePrerequisite(CommandSender sender, String questId, String[] args) {
        String action = args.length >= 3 ? args[2].toLowerCase() : "list";
        if (!PREREQ_ACTIONS.contains(action)) {
            sendErrorMessage(sender, "Unknown prerequisite action: " + action + ". Valid: " + String.join(", ", PREREQ_ACTIONS));
            return;
        }
        if (("add".equals(action) || "remove".equals(action)) && args.length < 4) {
            sendErrorMessage(sender, "Usage: /quest edit " + questId + " prerequisite " + action + " <questId>");
            return;
        }
        String targetQuest = args.length >= 4 ? args[3] : null;

        IQuestRepository repo = plugin.getQuestRepository();
        if (repo == null) {
            sendErrorMessage(sender, "Quest repository not available.");
            return;
        }

        repo.findById(questId).thenAccept(opt -> {
            if (opt.isEmpty()) {
                sendErrorMessage(sender, "Quest not found: " + questId);
                return;
            }
            QuestDTO quest = opt.get();
            List<String> current = quest.prerequisites();

            if ("list".equals(action)) {
                if (current.isEmpty()) {
                    sendInfoMessage(sender, "Quest " + questId + " has no prerequisites.");
                } else {
                    sendMessage(sender, "&6Prerequisites for &f" + questId + " &7(" + current.size() + "):");
                    for (String p : current) sendMessage(sender, "&7  &f" + p);
                }
                return;
            }

            List<String> updated = new java.util.ArrayList<>(current);
            switch (action) {
                case "add" -> {
                    if (updated.contains(targetQuest)) {
                        sendErrorMessage(sender, "Prerequisite already present: " + targetQuest);
                        return;
                    }
                    updated.add(targetQuest);
                }
                case "remove" -> {
                    if (!updated.remove(targetQuest)) {
                        sendErrorMessage(sender, "Prerequisite not present: " + targetQuest);
                        return;
                    }
                }
                case "clear" -> updated.clear();
                default -> { return; }
            }

            QuestDTO saved = quest.withPrerequisites(updated);
            repo.save(saved).thenAccept(success -> {
                if (success) {
                    plugin.getQuestManager().reloadQuest(questId);
                    sendSuccessMessage(sender, "Prerequisites for " + questId + " now: "
                        + (updated.isEmpty() ? "(none)" : String.join(", ", updated)));
                } else {
                    sendErrorMessage(sender, "Failed to save prerequisites (save returned false - check DB schema/fallback state).");
                }
            }).exceptionally(ex -> {
                Throwable cur = ex;
                while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
                logger.error("Failed to save prerequisites for quest: " + questId, cur);
                sendErrorMessage(sender, "Failed to save prerequisites: "
                    + (cur.getMessage() != null ? cur.getMessage() : cur.getClass().getSimpleName()));
                return null;
            });
        });
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
        if (args.length == 3 && (args[1].equalsIgnoreCase("prerequisite") || args[1].equalsIgnoreCase("prerequisites"))) {
            return PREREQ_ACTIONS.stream()
                .filter(a -> a.startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 4 && (args[1].equalsIgnoreCase("prerequisite") || args[1].equalsIgnoreCase("prerequisites"))
                && (args[2].equalsIgnoreCase("add") || args[2].equalsIgnoreCase("remove"))) {
            return plugin.getQuestManager().getQuestIds().stream()
                .filter(id -> id.startsWith(args[3].toLowerCase()))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * Worked examples for {@code /quest help edit} (#1981).
     */
    @Override
    public java.util.List<String> getExamples() {
        return java.util.List.of(
                "/quest edit tfah_ch1_journey description The long road to the Nocturne",
                "/quest edit tfah_ch1_journey category exploration",
                "/quest edit tfah_ch1_journey repeatable true",
                "/quest edit tfah_ch1_journey cooldown 86400",
                "  cooldown is in seconds",
                "/quest edit tfah_ch1_journey prerequisite tfah_zeal_arrival",
                "/quest edit tfah_ch1_journey metadata state_mapping {\"COMPLETED\":\"done\"}");
    }
}
