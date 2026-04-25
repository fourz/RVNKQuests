package org.fourz.RVNKQuests.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.Quest;

import java.util.Collections;
import java.util.List;

/**
 * Handles the /quest list command to display available quests.
 * Extends BaseSubCommand to provide standardized subcommand functionality.
 */
public class QuestListSubCommand extends BaseSubCommand {

    public QuestListSubCommand(RVNKQuests plugin) {
        super(plugin, "list", "List all available quests",
              "/quest list", "rvnkquests.list", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.GOLD + "===== Available Quests =====");

        List<String> questIds = plugin.getQuestManager().getQuestIds();
        if (questIds.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No quests are currently available.");
            return true;
        }

        int count = 0;
        for (String questId : questIds) {
            Quest quest = plugin.getQuestManager().getQuest(questId).orElse(null);
            if (quest != null && isQuestEnabled(questId)) {
                count++;
                sender.sendMessage(
                    ChatColor.YELLOW + "• " + quest.getName() +
                    ChatColor.GRAY + " (" + questId + ")"
                );
                // Show start trigger if available
                String startTrigger = quest.getStartTrigger();
                if (startTrigger != null && !startTrigger.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "  Start: " + startTrigger);
                }
            }
        }

        if (count == 0) {
            sender.sendMessage(ChatColor.YELLOW + "No enabled quests found.");
        } else {
            sender.sendMessage(ChatColor.GRAY + "Total: " + count + " quest(s) available");
        }

        sender.sendMessage(ChatColor.GRAY + "Use /quest start <quest_id> to begin a quest.");

        return true;
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        // Allow all players to list quests
        return sender.hasPermission("rvnkquests.list") ||
               sender.hasPermission("rvnkquests.player") ||
               sender.isOp();
    }

    /**
     * Check if a quest is enabled in the configuration.
     * @param questId The quest ID to check
     * @return true if the quest is enabled (defaults to true)
     */
    private boolean isQuestEnabled(String questId) {
        return plugin.getConfigManager().getConfig().getBoolean("quests." + questId + ".enable", true);
    }
}
