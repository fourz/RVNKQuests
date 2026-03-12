package org.fourz.RVNKQuests.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.event.Listener;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.objective.generic.GenericEncounterObjective;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the /quest mobs command to manage quest mobs.
 * Scans GenericEncounterObjective listeners for tracked mobs.
 */
public class QuestMobsSubCommand extends BaseSubCommand {
    private final List<String> VALID_OPERATIONS = Arrays.asList("kill", "list");

    public QuestMobsSubCommand(RVNKQuests plugin) {
        super(plugin, "mobs", "Manage quest mobs (kill, list)",
              "/quest mobs <operation>", "rvnkquests.admin", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendErrorMessage(sender, "Please specify an operation: kill, list");
            return true;
        }

        String operation = args[0].toLowerCase();

        if (!VALID_OPERATIONS.contains(operation)) {
            sendErrorMessage(sender, "Unknown operation: " + operation);
            sendErrorMessage(sender, "Valid operations: " + String.join(", ", VALID_OPERATIONS));
            return true;
        }

        if (operation.equals("kill")) {
            return killQuestMobs(sender);
        } else if (operation.equals("list")) {
            return listQuestMobs(sender);
        }

        return true;
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return VALID_OPERATIONS.stream()
                .filter(op -> op.startsWith(partial))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnkquests.admin") || sender.isOp();
    }

    private boolean killQuestMobs(CommandSender sender) {
        int killedCount = 0;

        for (Quest quest : plugin.getQuestManager().getAllQuests()) {
            for (GenericEncounterObjective encounter : findEncounterObjectives(quest)) {
                for (Entity mob : new ArrayList<>(encounter.getAllSpawnedMobs())) {
                    if (mob != null && mob.isValid()) {
                        logger.debug("Removing quest mob: " +
                            (mob.getCustomName() != null ? mob.getCustomName() : mob.getType().toString()));
                        mob.remove();
                        killedCount++;
                    }
                }
            }
        }

        if (killedCount > 0) {
            sendSuccessMessage(sender, "Successfully removed " + killedCount + " quest mobs.");
            logger.info("Admin " + sender.getName() + " removed " + killedCount + " quest mobs");
        } else {
            sendInfoMessage(sender, "No active quest mobs found to remove.");
        }

        return true;
    }

    private boolean listQuestMobs(CommandSender sender) {
        int totalCount = 0;

        sender.sendMessage(ChatColor.GOLD + "===== Active Quest Mobs =====");

        for (Quest quest : plugin.getQuestManager().getAllQuests()) {
            for (GenericEncounterObjective encounter : findEncounterObjectives(quest)) {
                List<Entity> mobs = encounter.getAllSpawnedMobs();
                if (!mobs.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "Quest: " +
                        ChatColor.WHITE + quest.getName() +
                        ChatColor.GRAY + " (" + mobs.size() + " mobs)");

                    for (Entity mob : mobs) {
                        String mobName = mob.getCustomName() != null ? mob.getCustomName() : mob.getType().toString();
                        sender.sendMessage(ChatColor.GRAY + " - " +
                            ChatColor.WHITE + mobName +
                            ChatColor.GRAY + " at " + formatLocation(mob.getLocation()));
                        totalCount++;
                    }
                }
            }
        }

        if (totalCount == 0) {
            sendInfoMessage(sender, "No active quest mobs found.");
        } else {
            sendSuccessMessage(sender, "Total quest mobs: " + totalCount);
        }

        return true;
    }

    /**
     * Finds all GenericEncounterObjective listeners across all states of a quest.
     */
    private List<GenericEncounterObjective> findEncounterObjectives(Quest quest) {
        List<GenericEncounterObjective> result = new ArrayList<>();
        for (QuestState state : QuestState.values()) {
            for (Listener listener : quest.createListenersForState(state)) {
                if (listener instanceof GenericEncounterObjective geo) {
                    result.add(geo);
                }
            }
        }
        return result;
    }

    private String formatLocation(org.bukkit.Location loc) {
        if (loc == null) return "unknown";
        return String.format("%s (%d,%d,%d)",
            loc.getWorld().getName(),
            loc.getBlockX(),
            loc.getBlockY(),
            loc.getBlockZ());
    }
}
