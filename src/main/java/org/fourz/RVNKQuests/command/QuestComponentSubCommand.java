package org.fourz.RVNKQuests.command;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.IQuestRepository;
import org.fourz.RVNKQuests.data.dto.QuestDTO;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * /quest component <add|del|list> <quest_id> [comp_id] [json]
 *
 * Manages individual components inside the quest metadata.components map without
 * replacing the entire metadata JSON. Each component is a trigger, objective, or
 * XOR branch entry keyed by its component ID.
 *
 * <ul>
 *   <li>add  — add or overwrite a component: quest component add <id> <comp_id> <json></li>
 *   <li>del  — remove a component:           quest component del <id> <comp_id></li>
 *   <li>list — show all component IDs:       quest component list <id></li>
 * </ul>
 */
public class QuestComponentSubCommand extends BaseSubCommand {

    private static final Gson GSON = new Gson();
    private static final Type COMP_TYPE = new TypeToken<Map<String, Object>>(){}.getType();
    private static final List<String> ACTIONS = List.of("add", "del", "list");

    public QuestComponentSubCommand(RVNKQuests plugin) {
        super(plugin, "component", "Manage quest metadata components",
              "/quest component <add|del|list> <quest_id> [comp_id] [json]",
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
        repo.findById(questId).thenAccept(opt -> {
            if (opt.isEmpty()) { sendErrorMessage(sender, "Quest not found: " + questId); return; }
            Map<?, ?> components = getComponents(opt.get());
            if (components.isEmpty()) {
                sendInfoMessage(sender, "No components defined for quest " + questId);
                return;
            }
            sendMessage(sender, "&6Components for &f" + questId + " &7(" + components.size() + "):");
            for (Object key : components.keySet()) {
                Object comp = components.get(key);
                String typeStr = "";
                if (comp instanceof Map<?, ?> m && m.containsKey("type")) {
                    typeStr = " &7[type=" + m.get("type") + "]";
                } else if (comp instanceof Map<?, ?> m && m.containsKey("objective_type")) {
                    typeStr = " &7[objective_type=" + m.get("objective_type") + "]";
                }
                sendMessage(sender, "&7  &f" + key + typeStr);
            }
        });
    }

    private void handleAdd(CommandSender sender, String questId, String[] args, IQuestRepository repo) {
        if (args.length < 4) {
            sendMessage(sender, "&c▶ Usage: /quest component add <quest_id> <comp_id> <json>");
            return;
        }
        String compId = args[2];
        String json = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

        Map<String, Object> compDef;
        try {
            compDef = GSON.fromJson(json, COMP_TYPE);
        } catch (Exception e) {
            sendErrorMessage(sender, "Invalid component JSON: " + e.getMessage());
            return;
        }
        if (compDef == null) {
            sendErrorMessage(sender, "Component JSON parsed to null — check syntax.");
            return;
        }

        repo.findById(questId).thenAccept(opt -> {
            if (opt.isEmpty()) { sendErrorMessage(sender, "Quest not found: " + questId); return; }
            QuestDTO quest = opt.get();

            // Dry-construct before saving — a config that fails listener construction would
            // otherwise save "successfully" and strip its state's listeners on reload (#1424)
            DataDrivenQuest probe = new DataDrivenQuest(plugin, quest);
            String constructionError = new QuestComponentFactory(plugin, probe)
                .validateComponentConfig(compId, compDef);
            if (constructionError != null) {
                sendErrorMessage(sender, "Component rejected (not saved): " + constructionError);
                return;
            }

            Map<String, Object> metadata = new HashMap<>(quest.metadata());
            @SuppressWarnings("unchecked")
            Map<String, Object> components = metadata.containsKey("components")
                ? new HashMap<>((Map<String, Object>) metadata.get("components"))
                : new HashMap<>();
            components.put(compId, compDef);
            metadata.put("components", components);

            QuestDTO updated = new QuestDTO(quest.questId(), quest.name(), quest.description(),
                quest.category(), quest.repeatable(), quest.cooldownMinutes(),
                quest.objectives(), quest.rewards(), quest.prerequisites(),
                quest.createdAt(), metadata);

            repo.save(updated).thenAccept(success -> {
                if (success) {
                    plugin.getQuestManager().reloadQuest(questId);
                    sendSuccessMessage(sender, "Component '" + compId + "' added to quest " + questId + " (hot-reloaded)");
                } else {
                    sendErrorMessage(sender, "Failed to save component.");
                }
            });
        });
    }

    private void handleDel(CommandSender sender, String questId, String[] args, IQuestRepository repo) {
        if (args.length < 3) {
            sendMessage(sender, "&c▶ Usage: /quest component del <quest_id> <comp_id>");
            return;
        }
        String compId = args[2];

        repo.findById(questId).thenAccept(opt -> {
            if (opt.isEmpty()) { sendErrorMessage(sender, "Quest not found: " + questId); return; }
            QuestDTO quest = opt.get();
            Map<String, Object> metadata = new HashMap<>(quest.metadata());
            @SuppressWarnings("unchecked")
            Map<String, Object> components = metadata.containsKey("components")
                ? new HashMap<>((Map<String, Object>) metadata.get("components"))
                : new HashMap<>();

            if (!components.containsKey(compId)) {
                sendErrorMessage(sender, "Component '" + compId + "' not found in quest " + questId);
                return;
            }
            components.remove(compId);
            metadata.put("components", components);

            QuestDTO updated = new QuestDTO(quest.questId(), quest.name(), quest.description(),
                quest.category(), quest.repeatable(), quest.cooldownMinutes(),
                quest.objectives(), quest.rewards(), quest.prerequisites(),
                quest.createdAt(), metadata);

            repo.save(updated).thenAccept(success -> {
                if (success) {
                    plugin.getQuestManager().reloadQuest(questId);
                    sendSuccessMessage(sender, "Component '" + compId + "' removed from quest " + questId + " (hot-reloaded)");
                } else {
                    sendErrorMessage(sender, "Failed to save changes.");
                }
            });
        });
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> getComponents(QuestDTO quest) {
        Object c = quest.metadata().get("components");
        return (c instanceof Map<?, ?>) ? (Map<?, ?>) c : Map.of();
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
        return Collections.emptyList();
    }

    /**
     * Worked examples for {@code /quest help component} (#1981).
     */
    @Override
    public java.util.List<String> getExamples() {
        return java.util.List.of(
                "/quest component list tfah_ch1_journey",
                "/quest component add tfah_ch1_journey start_lectern {\"type\":\"STRUCTURE_INTERACT\"}",
                "  the JSON is the component metadata, quoted as one argument",
                "/quest component del tfah_ch1_journey start_lectern",
                "Components are what QuestComponentFactory turns into trigger and objective",
                "listeners. Adding one needs a /quest reload to take effect.");
    }
}
