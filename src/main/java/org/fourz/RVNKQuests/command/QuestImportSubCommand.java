package org.fourz.RVNKQuests.command;

import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.IQuestRepository;
import org.fourz.RVNKQuests.data.QuestYamlRepository;
import org.fourz.RVNKQuests.data.dto.QuestDTO;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /quest import <filename|all> — import quest definitions from YAML files.
 */
public class QuestImportSubCommand extends BaseSubCommand {

    public QuestImportSubCommand(RVNKQuests plugin) {
        super(plugin, "import", "Import quest definitions from YAML",
              "/quest import <filename|all>", "rvnkquests.admin.import", false);
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

        // Load from YAML files
        QuestYamlRepository yamlRepo = new QuestYamlRepository(plugin);

        if (target.equalsIgnoreCase("all")) {
            sendInfoMessage(sender, "Importing all quest definitions from YAML...");
            yamlRepo.findAll().thenAccept(quests -> {
                int imported = 0;
                int failed = 0;
                for (QuestDTO quest : quests) {
                    // Isolate per-quest failures so one bad save doesn't abort the batch
                    // and, crucially, doesn't escape this lambda as a swallowed exception (#1425)
                    try {
                        if (Boolean.TRUE.equals(repo.save(quest).join())) {
                            imported++;
                        } else {
                            failed++;
                            sendErrorMessage(sender, "Failed to import quest: " + quest.questId()
                                + " (save returned false — check DB schema/fallback state)");
                        }
                    } catch (Exception e) {
                        failed++;
                        logger.error("Exception importing quest: " + quest.questId(), e);
                        sendErrorMessage(sender, "Error importing quest " + quest.questId() + ": " + rootMessage(e));
                    }
                }
                // Reload quests in the quest manager
                plugin.getQuestManager().loadQuestsFromRepository();
                sendSuccessMessage(sender, "Imported " + imported + " quest(s) from YAML files"
                    + (failed > 0 ? " (" + failed + " failed — see above)" : "") + ".");
            }).exceptionally(ex -> {
                logger.error("Import-all failed before completion", (Throwable) ex);
                sendErrorMessage(sender, "Import failed: " + rootMessage(ex));
                return null;
            });
        } else {
            // Remove .yml extension if provided
            String questId = target.endsWith(".yml") ? target.substring(0, target.length() - 4) : target;

            yamlRepo.findById(questId).thenAccept(opt -> {
                if (opt.isEmpty()) {
                    sendErrorMessage(sender, "Quest file not found: " + questId + ".yml");
                    return;
                }
                repo.save(opt.get()).thenAccept(success -> {
                    if (success) {
                        plugin.getQuestManager().reloadQuest(questId);
                        sendSuccessMessage(sender, "Imported quest: " + questId);
                    } else {
                        sendErrorMessage(sender, "Failed to import quest: " + questId
                            + " (save returned false — check DB schema/fallback state)");
                    }
                }).exceptionally(ex -> {
                    logger.error("Save failed while importing quest: " + questId, (Throwable) ex);
                    sendErrorMessage(sender, "Failed to import quest " + questId + ": " + rootMessage(ex));
                    return null;
                });
            }).exceptionally(ex -> {
                logger.error("Failed to read quest YAML for import: " + questId, (Throwable) ex);
                sendErrorMessage(sender, "Failed to read " + questId + ".yml: " + rootMessage(ex));
                return null;
            });
        }

        return true;
    }

    /** Unwraps CompletionException/wrapper layers to the most useful message for the operator. */
    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return (msg != null ? msg : cur.getClass().getSimpleName());
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> options = new java.util.ArrayList<>(List.of("all"));

            // List available YAML files
            File questsDir = new File(plugin.getDataFolder(), "quests");
            if (questsDir.exists()) {
                File[] files = questsDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
                if (files != null) {
                    for (File file : files) {
                        String name = file.getName();
                        options.add(name.substring(0, name.lastIndexOf('.')));
                    }
                }
            }

            return options.stream()
                .filter(o -> o.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
