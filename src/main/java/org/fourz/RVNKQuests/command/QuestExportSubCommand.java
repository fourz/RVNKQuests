package org.fourz.RVNKQuests.command;

import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.IQuestRepository;
import org.fourz.RVNKQuests.data.QuestYamlRepository;
import org.fourz.RVNKQuests.data.dto.QuestDTO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /quest export <quest_id|all> — export quest definitions to YAML files.
 */
public class QuestExportSubCommand extends BaseSubCommand {

    public QuestExportSubCommand(RVNKQuests plugin) {
        super(plugin, "export", "Export quest definitions to YAML",
              "/quest export <quest_id|all>", "rvnkquests.admin.export", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args, 1)) return true;

        String target = args[0];
        IQuestRepository repo = plugin.getQuestRepository();
        if (repo == null) {
            sendErrorMessage(sender, "Quest repository not available.");
            return true;
        }

        // Create a YAML repository for export
        QuestYamlRepository yamlRepo = new QuestYamlRepository(plugin);

        if (target.equalsIgnoreCase("all")) {
            sendInfoMessage(sender, "Exporting all quest definitions...");
            repo.findAll().thenAccept(quests -> {
                int exported = 0;
                for (QuestDTO quest : quests) {
                    Boolean result = yamlRepo.save(quest).join();
                    if (Boolean.TRUE.equals(result)) {
                        exported++;
                    }
                }
                sendSuccessMessage(sender, "Exported " + exported + " quest(s) to plugins/RVNKQuests/quests/");
            });
        } else {
            repo.findById(target).thenAccept(opt -> {
                if (opt.isEmpty()) {
                    sendErrorMessage(sender, "Quest not found: " + target);
                    return;
                }
                yamlRepo.save(opt.get()).thenAccept(success -> {
                    if (success) {
                        sendSuccessMessage(sender, "Exported quest " + target + " to plugins/RVNKQuests/quests/" + target + ".yml");
                    } else {
                        sendErrorMessage(sender, "Failed to export quest.");
                    }
                });
            });
        }

        return true;
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> options = new java.util.ArrayList<>(List.of("all"));
            options.addAll(plugin.getQuestManager().getQuestIds());
            return options.stream()
                .filter(o -> o.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * Worked examples for {@code /quest help export} (#1981).
     */
    @Override
    public java.util.List<String> getExamples() {
        return java.util.List.of(
                "/quest export tfah_ch1_journey",
                "/quest export all",
                "  writes definition JSON you can re-import elsewhere");
    }
}
