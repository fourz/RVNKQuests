package org.fourz.RVNKQuests.command;

import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.DatabaseManager;
import org.fourz.RVNKQuests.data.QuestsTestDataGenerator;
import org.fourz.rvnkcore.testing.TestDataGenerator.DataCategory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Debug subcommand for seeding test data into the RVNKQuests database.
 *
 * <p>Usage:
 * <ul>
 *   <li>/quest debug seed minimal|standard|stress - Seed test data</li>
 *   <li>/quest debug seed cleanup - Remove all test data</li>
 *   <li>/quest debug seed cleanup [player-uuid] - Remove data for specific player</li>
 *   <li>/quest debug seed status - Show seeding status</li>
 * </ul>
 * </p>
 */
public class SeedSubCommand extends BaseSubCommand {

    private static final List<String> CATEGORIES = Arrays.asList("minimal", "standard", "stress");
    private static final List<String> ACTIONS = Arrays.asList("minimal", "standard", "stress", "cleanup", "status");

    private QuestsTestDataGenerator generator;
    private boolean seeding = false;

    public SeedSubCommand(RVNKQuests plugin) {
        super(plugin, "seed", "Seed test data into database",
              "/quest debug seed <minimal|standard|stress|cleanup|status>",
              "rvnkquests.admin.seed", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showUsage(sender);
            return true;
        }

        String action = args[0].toLowerCase();

        // Initialize generator if needed
        DatabaseManager dbManager = plugin.getDatabaseManager();
        if (dbManager == null || !dbManager.isAvailable()) {
            sendErrorMessage(sender, "Database is not available. Cannot perform seed operations.");
            return true;
        }

        if (generator == null) {
            generator = new QuestsTestDataGenerator(dbManager);
        }

        switch (action) {
            case "minimal":
            case "standard":
            case "stress":
                return executeSeed(sender, DataCategory.valueOf(action.toUpperCase()));
            case "cleanup":
                if (args.length > 1) {
                    return executeCleanupPlayer(sender, args[1]);
                }
                return executeCleanup(sender);
            case "status":
                return executeStatus(sender);
            default:
                sendErrorMessage(sender, "Unknown action: " + action);
                showUsage(sender);
                return true;
        }
    }

    private void showUsage(CommandSender sender) {
        sendMessage(sender, "&6=== Quest Seed Commands ===");
        sendMessage(sender, "&7/quest debug seed minimal &8- Seed 10 base records");
        sendMessage(sender, "&7/quest debug seed standard &8- Seed 100 base records");
        sendMessage(sender, "&7/quest debug seed stress &8- Seed 1000 base records");
        sendMessage(sender, "&7/quest debug seed cleanup &8- Remove all test data");
        sendMessage(sender, "&7/quest debug seed cleanup <uuid> &8- Remove player's test data");
        sendMessage(sender, "&7/quest debug seed status &8- Show current status");
    }

    private boolean executeSeed(CommandSender sender, DataCategory category) {
        if (seeding) {
            sendErrorMessage(sender, "A seed operation is already in progress.");
            return true;
        }

        seeding = true;
        sendMessage(sender, "&6⚙ Seeding " + category.name() + " test data...");

        generator.seed(category).thenAccept(count -> {
            seeding = false;
            if (count > 0) {
                sendMessage(sender, "&a✓ Seed complete: " + count + " total records created");
            } else {
                sendErrorMessage(sender, "Seed failed. Check console for details.");
            }
        }).exceptionally(ex -> {
            seeding = false;
            sendErrorMessage(sender, "Seed failed: " + ex.getMessage());
            logger.error("Seed operation failed", (Exception) ex);
            return null;
        });

        return true;
    }

    private boolean executeCleanup(CommandSender sender) {
        if (seeding) {
            sendErrorMessage(sender, "A seed operation is in progress. Wait for it to complete.");
            return true;
        }

        seeding = true;
        sendMessage(sender, "&6⚙ Cleaning up all test data...");

        generator.cleanup().thenAccept(success -> {
            seeding = false;
            if (success) {
                sendMessage(sender, "&a✓ Cleanup complete");
            } else {
                sendErrorMessage(sender, "Cleanup failed. Check console for details.");
            }
        }).exceptionally(ex -> {
            seeding = false;
            sendErrorMessage(sender, "Cleanup failed: " + ex.getMessage());
            logger.error("Cleanup operation failed", (Exception) ex);
            return null;
        });

        return true;
    }

    private boolean executeCleanupPlayer(CommandSender sender, String uuidStr) {
        UUID playerUuid;
        try {
            playerUuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            sendErrorMessage(sender, "Invalid UUID format: " + uuidStr);
            return true;
        }

        if (seeding) {
            sendErrorMessage(sender, "A seed operation is in progress. Wait for it to complete.");
            return true;
        }

        seeding = true;
        sendMessage(sender, "&6⚙ Cleaning up data for player: " + uuidStr.substring(0, 8) + "...");

        generator.cleanupByPlayer(playerUuid).thenAccept(count -> {
            seeding = false;
            sendMessage(sender, "&a✓ Cleaned up " + count + " records for player");
        }).exceptionally(ex -> {
            seeding = false;
            sendErrorMessage(sender, "Cleanup failed: " + ex.getMessage());
            logger.error("Player cleanup operation failed", (Exception) ex);
            return null;
        });

        return true;
    }

    private boolean executeStatus(CommandSender sender) {
        DatabaseManager dbManager = plugin.getDatabaseManager();

        sendMessage(sender, "&6=== Quest Seed Status ===");
        sendMessage(sender, "&7Database Type: &f" + (dbManager != null ? dbManager.getType() : "N/A"));
        sendMessage(sender, "&7Database Available: " + (dbManager != null && dbManager.isAvailable() ? "&aYes" : "&cNo"));
        sendMessage(sender, "&7Generator Initialized: " + (generator != null ? "&aYes" : "&7No"));
        sendMessage(sender, "&7Seeding In Progress: " + (seeding ? "&eYes" : "&7No"));

        if (dbManager != null && dbManager.isAvailable()) {
            sendMessage(sender, "&7Table Prefix: &f" + (dbManager.getTablePrefix().isEmpty() ? "(none)" : dbManager.getTablePrefix()));
        }

        return true;
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String action : ACTIONS) {
                if (action.startsWith(partial)) {
                    completions.add(action);
                }
            }
        }

        return completions;
    }
}
