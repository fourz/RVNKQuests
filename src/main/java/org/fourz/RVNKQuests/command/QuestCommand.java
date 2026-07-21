package org.fourz.RVNKQuests.command;

import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;

/**
 * Main command handler for the /quest command.
 * Extends BaseCommand to provide standardized command handling with subcommand support.
 *
 * This command serves as the primary entry point for all quest-related operations
 * and delegates to appropriate subcommands based on arguments.
 */
public class QuestCommand extends BaseCommand {

    public QuestCommand(RVNKQuests plugin) {
        super(plugin, "quest", "Main quest management command", "/quest <subcommand>", "rvnkquests.quest");
        registerCoreCommands();
    }

    /**
     * Registers the core subcommands that are always available
     */
    private void registerCoreCommands() {
        logger.debug("Registering core subcommands");

        // Create and register each subcommand directly
        registerSubCommand("list", new QuestListSubCommand(plugin));
        registerSubCommand("item", new QuestItemSubCommand(plugin));
        registerSubCommand("state", new QuestStateSubCommand(plugin));
        registerSubCommand("reload", new QuestReloadSubCommand(plugin));
        registerSubCommand("trigger", new QuestTriggerSubCommand(plugin));
        registerSubCommand("debug", new QuestDebugSubCommand(plugin));
        registerSubCommand("mobs", new QuestMobsSubCommand(plugin));
        registerSubCommand("config", new QuestConfigSubCommand(plugin));
        registerSubCommand("validate", new QuestValidateSubCommand(plugin));
        registerSubCommand("start", new QuestStartSubCommand(plugin));
        registerSubCommand("progress", new ProgressSubCommand(plugin));
        registerSubCommand("abandon", new QuestAbandonSubCommand(plugin));
        registerSubCommand("journal", new QuestJournalSubCommand(plugin));
        registerSubCommand("leaderboard", new QuestLeaderboardSubCommand(plugin));
        registerSubCommand("menu", new QuestMenuSubCommand(plugin, this));

        // Admin commands (require elevated permissions)
        registerSubCommand("reset", new QuestResetSubCommand(plugin));
        registerSubCommand("complete", new QuestCompleteSubCommand(plugin));
        // setstate moved under /quest debug setstate (#448)
        registerSubCommand("objective", new QuestObjectiveSubCommand(plugin));

        // Quest definition admin commands (generic quest engine)
        registerSubCommand("create", new QuestCreateSubCommand(plugin));
        registerSubCommand("delete", new QuestDeleteSubCommand(plugin));
        registerSubCommand("edit", new QuestEditSubCommand(plugin));
        registerSubCommand("component", new QuestComponentSubCommand(plugin));
        registerSubCommand("def-objective", new QuestDefObjectiveSubCommand(plugin));
        registerSubCommand("reward", new QuestRewardSubCommand(plugin));
        registerSubCommand("export", new QuestExportSubCommand(plugin));
        registerSubCommand("import", new QuestImportSubCommand(plugin));

        // Chain management commands
        registerSubCommand("chain", new QuestChainSubCommand(plugin));

        // Admin diagnostic commands
        registerSubCommand("probe", new QuestProbeSubCommand(plugin));

        // Player preference commands (Phase 4 - with database persistence)
        registerSubCommand("prefs", new QuestPrefsSubCommand(plugin, plugin.getPreferenceRepository()));

        logger.debug("Core subcommands registered");
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        // If we get here, no subcommand was found
        // The base class will handle showing the unknown subcommand message
        return super.executeCommand(sender, args);
    }
}
