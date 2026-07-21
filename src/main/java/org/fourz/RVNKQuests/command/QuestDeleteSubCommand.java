package org.fourz.RVNKQuests.command;

import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.IQuestRepository;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /quest delete &lt;id&gt; confirm — permanently delete a quest definition (#1639).
 *
 * <p>The inverse of {@code /quest create}. Before this existed, a quest created live (during
 * authoring or QA) could not be removed by any supported path: no command, and the DB is
 * read-only to the operator tooling on Event/prod. Test quests piled up in {@code /quest list}
 * with no way to clear them.</p>
 *
 * <p>Requires an explicit {@code confirm} argument because it is destructive. On confirm it
 * removes the definition (objectives and rewards cascade via FK) and evicts the live quest from
 * {@link org.fourz.RVNKQuests.quest.QuestManager} — unregistering its Bukkit listeners — so it
 * stops firing and disappears from the list immediately, without a reload.</p>
 */
public class QuestDeleteSubCommand extends BaseSubCommand {

    public QuestDeleteSubCommand(RVNKQuests plugin) {
        super(plugin, "delete", "Permanently delete a quest definition (admin)",
              "/quest delete <id> confirm", "rvnkquests.admin.delete", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args, 1)) return true;

        String questId = args[0].toLowerCase();
        boolean confirmed = args.length >= 2 && args[1].equalsIgnoreCase("confirm");

        IQuestRepository repo = plugin.getQuestRepository();
        if (repo == null) {
            sendErrorMessage(sender, "Quest repository not available.");
            return true;
        }

        repo.exists(questId).thenAccept(exists -> {
            if (!exists) {
                sendErrorMessage(sender, "Quest '" + questId + "' does not exist.");
                return;
            }

            if (!confirmed) {
                sendMessage(sender, "&e⚠ Permanently deletes quest '" + questId
                        + "' (definition, objectives, rewards). This cannot be undone.");
                sendMessage(sender, "&c▶ Re-run to confirm: &e/quest delete " + questId + " confirm");
                return;
            }

            repo.deleteById(questId).thenAccept(success -> {
                if (success) {
                    // Evict from the live registry and unregister its Bukkit listeners, so the
                    // quest stops firing and leaves /quest list immediately — no reload needed.
                    plugin.getQuestManager().unregisterQuest(questId);
                    sendSuccessMessage(sender, "Deleted quest: " + questId);
                    logger.info("Quest deleted: " + questId + " by " + sender.getName());
                } else {
                    sendErrorMessage(sender, "Failed to delete quest '" + questId + "'.");
                }
            });
        });

        return true;
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return plugin.getQuestManager().getQuestIds().stream()
                    .filter(id -> id.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return List.of("confirm");
        }
        return Collections.emptyList();
    }
}
