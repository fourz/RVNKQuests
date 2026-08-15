package org.fourz.RVNKQuests.command;

import org.bukkit.command.CommandSender;
import java.util.List;

/**
 * Interface for subcommands within the RVNKQuests command framework.
 * Subcommands are commands that are executed as part of a parent command.
 *
 * <p>Subcommands implement only {@link ICommand} — they never carry
 * subcommand-registry methods ({@link ICommandRouter}) because leaf commands
 * have no children to register or look up.</p>
 *
 * <p>This interface extends {@code ICommand} and adds the subcommand-specific
 * {@link #isPlayerOnly()} and {@link #getParent()} accessors.</p>
 */
public interface SubCommand extends ICommand {

    /**
     * Provide tab completion for the subcommand.
     *
     * <p>Named {@code getTabCompletions} to distinguish from the
     * {@link ICommand#tabComplete} method used by top-level commands.</p>
     *
     * @param sender The command sender
     * @param args   Subcommand arguments being completed
     * @return List of possible completions
     */
    List<String> getTabCompletions(CommandSender sender, String[] args);

    /**
     * Check if this subcommand is restricted to players only.
     *
     * @return true if only players can use this subcommand
     */
    boolean isPlayerOnly();

    /**
     * Get the parent command of this subcommand.
     *
     * <p>Returns {@link ICommand} rather than the legacy {@link RVNKCommand}
     * so that the parent reference is scoped to execution/metadata only and
     * does not pull in routing concerns.</p>
     *
     * @return The parent command, or null for standalone subcommands
     */
    ICommand getParent();

    /**
     * Worked examples for this subcommand, served by {@code /quest help &lt;verb&gt;}.
     *
     * <p>Return concrete, runnable lines with real-looking arguments — not a restatement of
     * {@link ICommand#getUsage()}, which the help already prints above them. A line beginning with
     * two spaces is rendered as a note under the example above it.</p>
     *
     * <p>Examples live here rather than in {@code docs/plugins/commands/} so that they ship with
     * the build and cannot drift from it. The doc pages carry what no command can print: sequence
     * diagrams, backend class references and changelogs. (#1981)</p>
     *
     * <p>Default is empty. A subcommand whose whole grammar fits in one usage line does not need
     * examples, and {@code /quest help} marks which verbs have them.</p>
     *
     * @return example lines, or an empty list when the usage string says everything
     */
    default List<String> getExamples() {
        return List.of();
    }
}
