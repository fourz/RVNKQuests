package org.fourz.RVNKQuests.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.service.IQuestChainService;
import org.fourz.RVNKQuests.service.IQuestChainService.ChainProgress;
import org.fourz.RVNKQuests.service.IQuestChainService.ChainStartResult;
import org.fourz.RVNKQuests.service.IQuestChainService.ChainStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Subcommand for quest chain management.
 * Usage: /quest chain list [player]
 *        /quest chain start <chain_id> [player]
 *        /quest chain status <chain_id> [player]
 *        /quest chain reset <chain_id> <player>
 */
public class QuestChainSubCommand extends BaseSubCommand {

    private static final List<String> SUB_ACTIONS = Arrays.asList(
        "list", "start", "status", "reset"
    );

    public QuestChainSubCommand(RVNKQuests plugin) {
        super(plugin, "chain", "Quest chain management",
              "/quest chain <list|start|status|reset> [chain_id] [player]",
              "rvnkquests.command.chain", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        IQuestChainService chainService = plugin.getQuestChainService();
        if (chainService == null) {
            sendErrorMessage(sender, "Quest chain service is not available");
            return true;
        }

        if (args.length == 0) {
            showUsage(sender);
            return true;
        }

        String action = args[0].toLowerCase();
        String[] subArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];

        switch (action) {
            case "list", "ls" -> handleList(sender, subArgs, chainService);
            case "start" -> handleStart(sender, subArgs, chainService);
            case "status", "info" -> handleStatus(sender, subArgs, chainService);
            case "reset" -> handleReset(sender, subArgs, chainService);
            default -> {
                sendErrorMessage(sender, "Unknown chain action: " + action);
                showUsage(sender);
            }
        }

        return true;
    }

    private void showUsage(CommandSender sender) {
        sendMessage(sender, "&6=== Quest Chain Commands ===");
        sendMessage(sender, "&7/quest chain list [player] &8- List all chains");
        sendMessage(sender, "&7/quest chain start <chain_id> [player] &8- Start a chain");
        sendMessage(sender, "&7/quest chain status <chain_id> [player] &8- View chain progress");
        sendMessage(sender, "&7/quest chain reset <chain_id> <player> &8- Reset chain progress");
    }

    private void handleList(CommandSender sender, String[] args, IQuestChainService chainService) {
        // Determine target player for progress display
        UUID targetId = null;
        String targetName = null;

        if (args.length > 0) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sendErrorMessage(sender, "Player not found: " + args[0]);
                return;
            }
            targetId = target.getUniqueId();
            targetName = target.getName();
        } else if (sender instanceof Player player) {
            targetId = player.getUniqueId();
            targetName = player.getName();
        }

        final UUID playerId = targetId;
        final String playerName = targetName;

        chainService.getAllChains()
            .thenAccept(chains -> Bukkit.getScheduler().runTask(plugin, () -> {
                sendMessage(sender, "&7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                sendMessage(sender, "&6Quest Chains &7(" + chains.size() + " registered)");
                sendMessage(sender, "&7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                if (chains.isEmpty()) {
                    sendMessage(sender, "&7No quest chains registered.");
                    return;
                }

                if (playerId != null) {
                    // Show with player progress
                    chainService.getAllProgress(playerId).thenAccept(progressList ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            for (var chain : chains) {
                                ChainProgress progress = progressList.stream()
                                    .filter(p -> p.chainId().equals(chain.chainId()))
                                    .findFirst()
                                    .orElse(null);

                                String statusIcon = getStatusIcon(progress);
                                String statusText = getStatusText(progress);

                                sendMessage(sender, statusIcon + " &f" + chain.chainId()
                                    + " &7- &f" + chain.name());
                                sendMessage(sender, "  &7Status: " + statusText
                                    + (playerName != null ? " &7(" + playerName + ")" : ""));
                            }
                            sendMessage(sender, "&7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        })
                    );
                } else {
                    // Console without player — just list chains
                    for (var chain : chains) {
                        sendMessage(sender, "&7● &f" + chain.chainId() + " &7- &f" + chain.name());
                        if (chain.description() != null && !chain.description().isEmpty()) {
                            sendMessage(sender, "  &7" + chain.description());
                        }
                    }
                    sendMessage(sender, "&7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                }
            }))
            .exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin, () ->
                    sendErrorMessage(sender, "Failed to list chains: " + ex.getMessage()));
                return null;
            });
    }

    private void handleStart(CommandSender sender, String[] args, IQuestChainService chainService) {
        if (args.length < 1) {
            sendMessage(sender, "&c\u25b6 Usage: /quest chain start <chain_id> [player]");
            return;
        }

        String chainId = args[0];
        Player target = resolveTarget(sender, args, 1);
        if (target == null) return;

        chainService.startChain(target.getUniqueId(), chainId)
            .thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (result.success()) {
                    sendSuccessMessage(sender, "Started chain '" + chainId + "' for " + target.getName());
                    if (!result.availableQuests().isEmpty()) {
                        sendMessage(sender, "&7Available quests:");
                        for (String questId : result.availableQuests()) {
                            sendMessage(sender, "  &a\u25b6 &f" + questId);
                        }
                    }
                } else {
                    sendErrorMessage(sender, result.message());
                }
            }))
            .exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin, () ->
                    sendErrorMessage(sender, "Failed to start chain: " + ex.getMessage()));
                return null;
            });
    }

    private void handleStatus(CommandSender sender, String[] args, IQuestChainService chainService) {
        if (args.length < 1) {
            sendMessage(sender, "&c\u25b6 Usage: /quest chain status <chain_id> [player]");
            return;
        }

        String chainId = args[0];
        Player target = resolveTarget(sender, args, 1);
        if (target == null) return;

        chainService.getProgress(target.getUniqueId(), chainId)
            .thenAccept(progress -> Bukkit.getScheduler().runTask(plugin, () -> {
                sendMessage(sender, "&7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                sendMessage(sender, "&6Chain: &f" + chainId + " &7(" + target.getName() + ")");
                sendMessage(sender, "&7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                String statusText = getStatusText(progress);
                sendMessage(sender, "&7Status: " + statusText);
                sendMessage(sender, "&7Progress: &f"
                    + String.format("%.0f%%", progress.getCompletionPercentage()));
                sendMessage(sender, "&7Completions: &f" + progress.completionCount());

                if (!progress.completedQuests().isEmpty()) {
                    sendMessage(sender, "");
                    sendMessage(sender, "&aCompleted Quests:");
                    for (String q : progress.completedQuests()) {
                        sendMessage(sender, "  &a\u2713 &f" + q);
                    }
                }

                if (!progress.activeQuests().isEmpty()) {
                    sendMessage(sender, "");
                    sendMessage(sender, "&eActive Quests:");
                    for (String q : progress.activeQuests()) {
                        sendMessage(sender, "  &e\u25b6 &f" + q);
                    }
                }

                if (!progress.lockedQuests().isEmpty()) {
                    sendMessage(sender, "");
                    sendMessage(sender, "&7Locked Quests:");
                    for (String q : progress.lockedQuests()) {
                        sendMessage(sender, "  &8\u25cf &7" + q);
                    }
                }

                sendMessage(sender, "&7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            }))
            .exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin, () ->
                    sendErrorMessage(sender, "Failed to get chain status: " + ex.getMessage()));
                return null;
            });
    }

    private void handleReset(CommandSender sender, String[] args, IQuestChainService chainService) {
        if (!sender.hasPermission("rvnkquests.admin")) {
            sendErrorMessage(sender, "You don't have permission to reset chain progress");
            return;
        }

        if (args.length < 2) {
            sendMessage(sender, "&c\u25b6 Usage: /quest chain reset <chain_id> <player>");
            return;
        }

        String chainId = args[0];
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sendErrorMessage(sender, "Player not found: " + args[1]);
            return;
        }

        chainService.resetProgress(target.getUniqueId(), chainId)
            .thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    sendSuccessMessage(sender, "Reset chain '" + chainId
                        + "' progress for " + target.getName());
                } else {
                    sendErrorMessage(sender, "Failed to reset chain progress (chain may not exist)");
                }
            }))
            .exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin, () ->
                    sendErrorMessage(sender, "Failed to reset chain: " + ex.getMessage()));
                return null;
            });
    }

    /**
     * Resolve target player from args or sender.
     * Returns null and sends error if no valid target found.
     */
    private Player resolveTarget(CommandSender sender, String[] args, int playerArgIndex) {
        if (args.length > playerArgIndex) {
            Player target = Bukkit.getPlayer(args[playerArgIndex]);
            if (target == null) {
                sendErrorMessage(sender, "Player not found: " + args[playerArgIndex]);
                return null;
            }
            return target;
        } else if (sender instanceof Player player) {
            return player;
        } else {
            sendErrorMessage(sender, "Console must specify a player");
            return null;
        }
    }

    private String getStatusIcon(ChainProgress progress) {
        if (progress == null) return "&7\u25cb";
        return switch (progress.status()) {
            case NOT_STARTED -> "&7\u25cb";
            case IN_PROGRESS -> "&e\u25d0";
            case COMPLETED -> "&a\u2713";
            case ON_COOLDOWN -> "&b\u231b";
            case LOCKED -> "&c\u2717";
        };
    }

    private String getStatusText(ChainProgress progress) {
        if (progress == null) return "&7Not Started";
        return switch (progress.status()) {
            case NOT_STARTED -> "&7Not Started";
            case IN_PROGRESS -> "&eIn Progress &7("
                + String.format("%.0f%%", progress.getCompletionPercentage()) + ")";
            case COMPLETED -> "&aCompleted";
            case ON_COOLDOWN -> "&bOn Cooldown";
            case LOCKED -> "&cLocked";
        };
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return SUB_ACTIONS.stream()
                .filter(a -> a.startsWith(partial))
                .collect(Collectors.toList());
        }

        String action = args[0].toLowerCase();

        // Chain ID argument for start/status/reset
        if (args.length == 2 && (action.equals("start") || action.equals("status")
                || action.equals("reset") || action.equals("info"))) {
            IQuestChainService chainService = plugin.getQuestChainService();
            if (chainService != null) {
                try {
                    List<String> chainIds = chainService.getAllChains().join().stream()
                        .map(c -> c.chainId())
                        .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                    return chainIds;
                } catch (Exception e) {
                    return Collections.emptyList();
                }
            }
        }

        // Player argument
        boolean isPlayerArg = (args.length == 2 && action.equals("list"))
            || (args.length == 3 && (action.equals("start") || action.equals("status")
                || action.equals("reset")));
        if (isPlayerArg) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
