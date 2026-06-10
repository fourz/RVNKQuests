package org.fourz.RVNKQuests.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.rvnkcore.util.chat.ChatService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Collections;
import java.util.List;

/**
 * Abstract base class for all RVNKQuests subcommands.
 * Provides common functionality for subcommand execution, permission checking,
 * and validation.
 *
 * <p>Implements only {@link SubCommand} (which extends {@link ICommand}).
 * Subcommands are leaf nodes in the command tree — they have no children to
 * register or dispatch, so they intentionally do NOT implement
 * {@link ICommandRouter}.</p>
 *
 * <p>This follows the RVNK ecosystem command framework pattern with:
 * <ul>
 *   <li>Standardised permission checking with inheritance from parent</li>
 *   <li>Player-only command support</li>
 *   <li>Consistent error messaging and validation helpers</li>
 *   <li>Integrated logging with proper context</li>
 * </ul>
 */
public abstract class BaseSubCommand implements SubCommand {

    protected final RVNKQuests plugin;
    /** Parent command — typed as {@link ICommand} (execution/metadata only). */
    protected final ICommand parent;
    protected final String name;
    protected final String description;
    protected final String usage;
    protected final String permission;
    protected final boolean playerOnly;
    protected final LogManager logger;
    protected final ChatService chatService;

    /**
     * Constructor for BaseSubCommand.
     *
     * @param plugin      The RVNKQuests plugin instance
     * @param parent      The parent command
     * @param name        The subcommand name
     * @param description The subcommand description
     * @param usage       The subcommand usage string
     * @param permission  The permission required to use the subcommand (can be null)
     * @param playerOnly  Whether this subcommand is restricted to players only
     */
    public BaseSubCommand(RVNKQuests plugin, ICommand parent, String name, String description,
                         String usage, String permission, boolean playerOnly) {
        this.plugin = plugin;
        this.parent = parent;
        this.name = name;
        this.description = description;
        this.usage = usage;
        this.permission = permission;
        this.playerOnly = playerOnly;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.chatService = new ChatService();
    }

    /**
     * Constructor for BaseSubCommand with default permission (inherits from parent).
     *
     * @param plugin      The RVNKQuests plugin instance
     * @param parent      The parent command
     * @param name        The subcommand name
     * @param description The subcommand description
     * @param usage       The subcommand usage string
     * @param playerOnly  Whether this subcommand is restricted to players only
     */
    public BaseSubCommand(RVNKQuests plugin, ICommand parent, String name, String description,
                         String usage, boolean playerOnly) {
        this(plugin, parent, name, description, usage,
             parent != null && parent.getPermission() != null
                 ? parent.getPermission() + "." + name.toLowerCase()
                 : null,
             playerOnly);
    }

    /**
     * Constructor for BaseSubCommand with default permission and not player-only.
     *
     * @param plugin      The RVNKQuests plugin instance
     * @param parent      The parent command
     * @param name        The subcommand name
     * @param description The subcommand description
     * @param usage       The subcommand usage string
     */
    public BaseSubCommand(RVNKQuests plugin, ICommand parent, String name, String description, String usage) {
        this(plugin, parent, name, description, usage, false);
    }

    /**
     * Constructor for standalone BaseSubCommand (no parent).
     *
     * @param plugin      The RVNKQuests plugin instance
     * @param name        The subcommand name
     * @param description The subcommand description
     * @param usage       The subcommand usage string
     * @param permission  The permission required
     * @param playerOnly  Whether this subcommand is restricted to players only
     */
    public BaseSubCommand(RVNKQuests plugin, String name, String description, String usage,
                         String permission, boolean playerOnly) {
        this(plugin, (ICommand) null, name, description, usage, permission, playerOnly);
    }

    /**
     * Constructor for standalone BaseSubCommand with default permission.
     *
     * @param plugin      The RVNKQuests plugin instance
     * @param name        The subcommand name
     * @param description The subcommand description
     * @param usage       The subcommand usage string
     */
    public BaseSubCommand(RVNKQuests plugin, String name, String description, String usage) {
        this(plugin, name, description, usage, "rvnkquests." + name.toLowerCase(), false);
    }

    // ==================== ICommand ====================

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getUsage() {
        return usage;
    }

    @Override
    public String getPermission() {
        return permission;
    }

    @Override
    public boolean isPlayerOnly() {
        return playerOnly;
    }

    @Override
    public ICommand getParent() {
        return parent;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        if (permission == null || permission.isEmpty()) {
            return true;
        }
        return sender.hasPermission(permission);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        logger.debug("Subcommand executed: " + getName() + " with " + args.length + " arguments");

        // Check permission
        if (!hasPermission(sender)) {
            sendNoPermissionMessage(sender);
            return true;
        }

        // Check player-only restriction
        if (isPlayerOnly() && !validatePlayer(sender)) {
            return true;
        }

        return executeSubCommand(sender, args);
    }

    /**
     * Execute the subcommand logic. Subclasses must implement this method.
     *
     * @param sender The command sender
     * @param args   Subcommand arguments
     * @return true if the subcommand was handled successfully
     */
    protected abstract boolean executeSubCommand(CommandSender sender, String[] args);

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return getTabCompletions(sender, args);
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            return Collections.emptyList();
        }

        if (isPlayerOnly() && !(sender instanceof Player)) {
            return Collections.emptyList();
        }

        return getTabCompletionOptions(sender, args);
    }

    /**
     * Get tab completions for this subcommand. Subclasses can override this method.
     *
     * @param sender The command sender
     * @param args   Subcommand arguments
     * @return List of possible completions
     */
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    @Override
    public void sendHelp(CommandSender sender) {
        chatService.sendInfo(sender, getUsage());
    }

    // ==================== Messaging helpers ====================

    /**
     * Send a message when the sender doesn't have permission.
     *
     * @param sender The command sender
     */
    protected void sendNoPermissionMessage(CommandSender sender) {
        chatService.sendError(sender, "You don't have permission to use this command.");
        String parentName = parent != null ? parent.getName() : "unknown";
        logger.warning("Permission denied for " + sender.getName() + " attempting to use subcommand: " +
                      parentName + " " + getName());
    }

    /**
     * Validate that the sender is a player.
     *
     * @param sender The command sender
     * @return true if the sender is a player
     */
    protected boolean validatePlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            chatService.sendError(sender, "This command can only be used by players.");
            return false;
        }
        return true;
    }

    /**
     * Validate the number of arguments.
     *
     * @param sender  The command sender
     * @param args    The arguments
     * @param minArgs Minimum number of arguments required
     * @return true if validation passes
     */
    protected boolean validateArgs(CommandSender sender, String[] args, int minArgs) {
        if (args.length < minArgs) {
            chatService.sendUsage(sender, "Usage: " + getUsage());
            return false;
        }
        return true;
    }

    /**
     * Send a formatted message to the sender.
     *
     * @param sender  The command sender
     * @param message The message to send (supports color codes with &amp;)
     */
    protected void sendMessage(CommandSender sender, String message) {
        chatService.sendMessage(sender, message);
    }

    /**
     * Send a success message to the sender.
     *
     * @param sender  The command sender
     * @param message The success message
     */
    protected void sendSuccessMessage(CommandSender sender, String message) {
        chatService.sendSuccess(sender, message);
    }

    /**
     * Send an error message to the sender.
     *
     * @param sender  The command sender
     * @param message The error message
     */
    protected void sendErrorMessage(CommandSender sender, String message) {
        chatService.sendError(sender, message);
    }

    /**
     * Send an info message to the sender.
     *
     * @param sender  The command sender
     * @param message The info message
     */
    protected void sendInfoMessage(CommandSender sender, String message) {
        chatService.sendInfo(sender, message);
    }
}
