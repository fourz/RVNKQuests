package org.fourz.RVNKQuests.command;

import org.bukkit.command.CommandSender;
import java.util.List;

/**
 * Execution and metadata contract for all RVNKQuests commands and subcommands.
 *
 * <p>This interface intentionally omits subcommand-registry methods; those
 * belong on {@link ICommandRouter}.  Leaf commands (subcommands that have no
 * children) implement only this interface — they are never forced to carry
 * routing machinery they do not use (Interface Segregation Principle).</p>
 */
public interface ICommand {

    /**
     * Execute the command logic.
     *
     * @param sender The command sender
     * @param args   Command arguments (excluding the base command name)
     * @return true if the command was handled successfully
     */
    boolean execute(CommandSender sender, String[] args);

    /**
     * Provide tab completion for the command.
     *
     * @param sender The command sender
     * @param args   Command arguments being completed
     * @return List of possible completions
     */
    List<String> tabComplete(CommandSender sender, String[] args);

    /**
     * Get the command name.
     *
     * @return The command name
     */
    String getName();

    /**
     * Get the command description.
     *
     * @return The command description
     */
    String getDescription();

    /**
     * Get the command usage string.
     *
     * @return The usage string
     */
    String getUsage();

    /**
     * Get the permission required to use this command.
     *
     * @return The permission string, or null if no permission is required
     */
    String getPermission();

    /**
     * Check if the sender has permission to use this command.
     *
     * @param sender The command sender
     * @return true if the sender has permission
     */
    boolean hasPermission(CommandSender sender);

    /**
     * Send help information to the command sender.
     *
     * @param sender The command sender
     */
    void sendHelp(CommandSender sender);
}
