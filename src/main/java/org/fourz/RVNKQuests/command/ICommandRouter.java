package org.fourz.RVNKQuests.command;

/**
 * Subcommand-registry contract for commands that act as routers (top-level
 * dispatchers).
 *
 * <p>Only commands that own child subcommands need to implement this interface.
 * Leaf commands ({@link BaseSubCommand} and its concrete subclasses) are never
 * required to implement it, keeping their API surface minimal
 * (Interface Segregation Principle).</p>
 *
 * <p>{@link BaseCommand} implements both {@link ICommand} and
 * {@code ICommandRouter}.  {@link BaseSubCommand} implements only
 * {@code ICommand}.</p>
 */
public interface ICommandRouter {

    /**
     * Register a subcommand with this command.
     *
     * @param name       The subcommand name (case-insensitive)
     * @param subCommand The subcommand implementation
     */
    void registerSubCommand(String name, SubCommand subCommand);

    /**
     * Get a subcommand by name.
     *
     * @param name The subcommand name (case-insensitive)
     * @return The subcommand, or null if not found
     */
    SubCommand getSubCommand(String name);
}
