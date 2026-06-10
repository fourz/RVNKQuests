package org.fourz.RVNKQuests.command;

/**
 * Legacy unified command interface for RVNKQuests.
 *
 * <p>This interface is retained for backward compatibility. New code should
 * prefer the segregated interfaces directly:</p>
 * <ul>
 *   <li>{@link ICommand} — execution and metadata (all commands)</li>
 *   <li>{@link ICommandRouter} — subcommand registry (router/top-level commands only)</li>
 * </ul>
 *
 * <p>{@link BaseCommand} implements both {@code ICommand} and
 * {@code ICommandRouter} and therefore satisfies this interface.
 * {@link BaseSubCommand} implements only {@code ICommand}; leaf subcommands
 * are no longer required to carry routing methods they do not use.</p>
 *
 * @deprecated Prefer {@link ICommand} and {@link ICommandRouter} individually.
 */
@Deprecated
public interface RVNKCommand extends ICommand, ICommandRouter {
    // Intentionally empty: all methods are inherited from ICommand and ICommandRouter.
}
