package org.fourz.RVNKQuests.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.trigger.TriggerCommand;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Subcommand for triggering quests via admin command.
 *
 * Usage:
 *   quest trigger <player> <quest_id> [--force]
 *   quest trigger --all <quest_id> [--world <name>] [--force]
 *
 * --force bypasses the required_state check (admin override).
 * Console-capable.
 */
public class QuestTriggerSubCommand extends BaseSubCommand {

    private final TriggerCommand triggerCommand;

    public QuestTriggerSubCommand(RVNKQuests plugin) {
        super(plugin, "trigger",
              "Trigger a quest for a player or all online players",
              "/rvnkquests trigger <player|--all> <quest_id> [--world <name>] [--force]",
              "rvnkquests.admin.trigger",
              false);
        this.triggerCommand = new TriggerCommand(plugin);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        List<String> argList = new ArrayList<>(Arrays.asList(args));
        boolean force = argList.remove("--force");

        // --all [--world <name>] <quest_id>
        if (argList.get(0).equalsIgnoreCase("--all")) {
            argList.remove(0);

            String worldFilter = null;
            int worldIdx = argList.indexOf("--world");
            if (worldIdx >= 0 && worldIdx + 1 < argList.size()) {
                worldFilter = argList.get(worldIdx + 1);
                argList.remove(worldIdx + 1);
                argList.remove(worldIdx);
            }

            if (argList.isEmpty()) {
                sendUsage(sender);
                return true;
            }

            String questId = argList.get(0).toLowerCase();
            Collection<? extends Player> targets = Bukkit.getOnlinePlayers();
            if (worldFilter != null) {
                final String wf = worldFilter;
                targets = targets.stream()
                        .filter(p -> p.getWorld().getName().equalsIgnoreCase(wf))
                        .collect(Collectors.toList());
            }

            if (targets.isEmpty()) {
                sendErrorMessage(sender, "No online players" + (worldFilter != null ? " in world '" + worldFilter + "'" : "") + ".");
                return true;
            }

            int count = 0;
            for (Player target : targets) {
                if (triggerCommand.triggerQuestForPlayer(sender, target, questId, force)) count++;
            }
            sendSuccessMessage(sender, "Triggered '" + questId + "' for " + count + "/" + targets.size() + " players.");
            return true;
        }

        // <player> <quest_id>
        String targetName = argList.get(0);
        if (argList.size() < 2) {
            sendUsage(sender);
            return true;
        }
        String questId = argList.get(1).toLowerCase();

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sendErrorMessage(sender, "Player not found or not online: " + targetName);
            return true;
        }

        triggerCommand.triggerQuestForPlayer(sender, target, questId, force);
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sendInfoMessage(sender, "Usage: /rvnkquests trigger <player|--all> <quest_id> [--world <name>] [--force]");
        sendInfoMessage(sender, "  --force  bypass required_state check");
        sendInfoMessage(sender, "  --all    target all online players");
        sendInfoMessage(sender, "  --world  filter --all to a specific world");
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> opts = new ArrayList<>();
            opts.add("--all");
            Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .forEach(opts::add);
            return opts.stream().filter(o -> o.toLowerCase().startsWith(partial)).collect(Collectors.toList());
        }
        if (args.length == 2) {
            return plugin.getQuestManager().getQuestIds().stream()
                    .filter(id -> id.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("--all")) {
            return List.of("--world", "--force").stream()
                    .filter(f -> f.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
