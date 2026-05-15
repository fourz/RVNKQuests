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
}
