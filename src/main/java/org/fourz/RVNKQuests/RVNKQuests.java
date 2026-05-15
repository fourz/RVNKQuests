package org.fourz.RVNKQuests;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.RVNKQuests.command.CommandManager;
import org.fourz.RVNKQuests.config.ConfigManager;
import org.fourz.RVNKQuests.data.DatabaseManager;
import org.fourz.RVNKQuests.data.IQuestRepository;
import org.fourz.RVNKQuests.data.QuestChainDefinitionSeeder;
import org.fourz.RVNKQuests.data.QuestDefinitionSeeder;
import org.fourz.RVNKQuests.data.QuestRepositoryImpl;
import org.fourz.RVNKQuests.data.QuestYamlRepository;
import org.fourz.rvnkcore.data.FallbackTracker;
import org.fourz.RVNKQuests.data.repository.IPreferenceRepository;
import org.fourz.RVNKQuests.data.repository.PreferenceRepositoryImpl;
import org.fourz.RVNKQuests.event.ChainProgressListener;
import org.fourz.RVNKQuests.event.PlayerJoinQuitListener;
import org.fourz.RVNKQuests.quest.QuestManager;
import org.fourz.RVNKQuests.service.IObjectiveService;
import org.fourz.RVNKQuests.service.IQuestDatabaseService;
import org.fourz.RVNKQuests.service.IQuestProgressService;
import org.fourz.RVNKQuests.service.IQuestService;
import org.fourz.RVNKQuests.service.IQuestChainService;
import org.fourz.RVNKQuests.service.IRewardService;
import org.fourz.RVNKQuests.service.IJournalService;
import org.fourz.RVNKQuests.service.INotificationService;
import org.fourz.RVNKQuests.service.IRepeatableQuestService;
import org.fourz.RVNKQuests.service.ObjectiveServiceImpl;
import org.fourz.RVNKQuests.service.QuestProgressServiceImpl;
import org.fourz.RVNKQuests.service.QuestChainServiceImpl;
import org.fourz.RVNKQuests.service.RewardServiceImpl;
import org.fourz.RVNKQuests.service.JournalServiceImpl;
import org.fourz.RVNKQuests.service.NotificationServiceImpl;
import org.fourz.RVNKQuests.service.RepeatableQuestServiceImpl;
import org.fourz.RVNKQuests.ui.QuestMenuListener;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.RVNKQuests.lore.LoreDatabase;
import org.fourz.RVNKQuests.reward.QuestItem;
import org.fourz.RVNKQuests.integration.ILoreIntegration;
import org.fourz.RVNKQuests.integration.LoreIntegrationImpl;

import java.util.logging.Level;

/**
 * Main plugin class for RVNKQuests, a dynamic narrative quest system for Bukkit/Spigot servers.
 *
 * <p>The plugin architecture follows a manager-based approach:</p>
 * <ul>
 *   <li>ConfigManager handles all configuration access</li>
 *   <li>DatabaseManager handles database connections and schema</li>
 *   <li>QuestProgressService manages per-player quest state persistence</li>
 *   <li>QuestManager manages quest registration, state tracking and event handling</li>
 *   <li>CommandManager handles player commands and subcommands</li>
 *   <li>JournalService provides quest history and statistics</li>
 *   <li>RepeatableQuestService manages quest repeatability and cooldowns</li>
 *   <li>PreferenceRepository manages player notification preferences</li>
 *   <li>LoreDatabase (optional) stores narrative content for quests</li>
 * </ul>
 *
 * <p>Each manager is independent but can be accessed through this main class,
 * providing a clean API for extensions or add-ons.</p>
 */
public class RVNKQuests extends JavaPlugin {
    // Unified logging system (RVNKCore)
    private LogManager logger;

    // Configuration
    private ConfigManager configManager;

    // Database and persistence
    private FallbackTracker fallbackTracker;
    private DatabaseManager databaseManager;
    private IQuestRepository questRepository;
    private IQuestProgressService questProgressService;
    private IPreferenceRepository preferenceRepository;

    // Quest system
    private QuestManager questManager;
    private CommandManager commandManager;

    // Service layer
    private IRewardService rewardService;
    private IQuestChainService questChainService;
    private IObjectiveService objectiveService;
    private IJournalService journalService;
    private INotificationService notificationService;
    private IRepeatableQuestService repeatableQuestService;

    // Optional features
    private LoreDatabase loreDatabase;
    private ILoreIntegration loreIntegration;

    // RVNKCore integration
    private boolean rvnkCoreAvailable = false;
    private Object rvnkCoreInstance = null;

    @Override
    public void onEnable() {
        // Initialize both new and legacy loggers
        logger = LogManager.getInstance(this, getClass());

        logger.info("Initializing RVNKQuests plugin");

        try {
            // Load configuration
            configManager = new ConfigManager(this);

            // Update log level from config
            updateGlobalLogLevel(configManager.getLogLevel());

            // Initialize database layer
            fallbackTracker = new FallbackTracker(
                    configManager.getConfig().getInt("database.fallback.consecutive_failures", 3),
                    configManager.getConfig().getInt("database.fallback.recovery_minutes", 5) * 60 * 1000L,
                    LogManager.getInstance(this, "FallbackTracker"));
            databaseManager = new DatabaseManager(this, fallbackTracker);

            if (!databaseManager.initialize()) {
                logger.warning("Database initialization failed - using YAML fallback");
            }

            // Initialize quest definition repository (SQL primary, YAML fallback)
            if (databaseManager.isAvailable()) {
                questRepository = new QuestRepositoryImpl(this, databaseManager);
                logger.debug("Quest definition repository initialized (SQL)");
            } else {
                questRepository = new QuestYamlRepository(this);
                logger.debug("Quest definition repository initialized (YAML fallback)");
            }

            // Initialize quest progress service
            questProgressService = new QuestProgressServiceImpl(this, databaseManager);

            // Initialize preference repository
            preferenceRepository = new PreferenceRepositoryImpl(this, databaseManager);

            // Initialize managers in correct dependency order
            questManager = new QuestManager(this);
            commandManager = new CommandManager(this);
            commandManager.initialize();

            // Initialize service layer — RewardService receives questManager so that
            // QuestUnlockRewardProcessor can start quests without a post-construction cast.
            rewardService = new RewardServiceImpl(this, questManager);
            questChainService = new QuestChainServiceImpl(this, questProgressService, rewardService);
            objectiveService = new ObjectiveServiceImpl(this);
            journalService = new JournalServiceImpl(this);
            notificationService = new NotificationServiceImpl(this);
            repeatableQuestService = new RepeatableQuestServiceImpl(this, databaseManager, questProgressService);

            // Register player join/quit listener for progress loading/saving
            getServer().getPluginManager().registerEvents(new PlayerJoinQuitListener(this), this);

            // Register quest menu listener for GUI interactions (feat-24)
            getServer().getPluginManager().registerEvents(new QuestMenuListener(this), this);

            // Register chain progress listener — bridges quest completion to chain service
            getServer().getPluginManager().registerEvents(new ChainProgressListener(this), this);

            // Initialize lore database if enabled
            if (configManager.isLoreDatabaseEnabled()) {
                loreDatabase = new LoreDatabase(this, databaseManager);
            }

            // Seed default quest definitions on first run
            new QuestDefinitionSeeder(this).seedIfNeeded();

            // Register quests (loads from repository + hardcoded fallbacks)
            questManager.initializeQuests();

            // Seed quest chain definitions (requires quests to be loaded first)
            logger.debug("About to seed quest chain definitions...");
            new QuestChainDefinitionSeeder(this).seedIfNeeded();
            logger.debug("Quest chain seeding complete");

            // Initialize lore integration and pre-populate quest books from lore DB
            loreIntegration = new LoreIntegrationImpl(this);
            QuestItem.setLoreIntegration(loreIntegration);
            // Seed known quest books (async, falls back to hardcoded if RVNKLore unavailable)
            QuestItem.populateFromLoreAsync("grotsnouts_journal",
                    "DIS AIN'T RIGHT!", "GrotSnout's journal about being stuck in the overworld.");
            QuestItem.populateFromLoreAsync("grotsnouts_last_stand",
                    "GrotSnout's Last Stand", "GrotSnout's final journal entry before facing the portal guardians.");
            logger.info("Lore integration initialized (available: " + loreIntegration.isLoreAvailable() + ")");

            // Register services with RVNKCore if available
            registerWithRVNKCore();

            logger.info("RVNKQuests plugin enabled successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize RVNKQuests plugin", e);
        }
    }

    @Override
    public void onDisable() {
        logger.info("Disabling RVNKQuests plugin");

        try {
            // Unregister from RVNKCore first
            unregisterFromRVNKCore();

            // Shut down commands before tearing down services they may reference
            if (commandManager != null) {
                commandManager.shutdown();
            }

            // Clean up quests first
            if (questManager != null) {
                questManager.cleanupQuests();
            }

            // Flush chain progress to DB before shutting down the connection pool
            if (questChainService instanceof QuestChainServiceImpl chainServiceImpl) {
                try {
                    chainServiceImpl.flush().get(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    logger.warning("Chain progress flush timed out or failed: " + e.getMessage());
                }
            }

            // Shutdown quest progress service (flushes pending saves)
            if (questProgressService != null) {
                questProgressService.shutdown();
            }

            // Shutdown repeatable quest service
            if (repeatableQuestService != null) {
                repeatableQuestService.shutdown();
            }

            // Shutdown database
            if (databaseManager != null) {
                databaseManager.shutdown();
            }

            // Close lore database
            if (loreDatabase != null) {
                loreDatabase.close();
            }

            // Shutdown notification service
            if (notificationService != null) {
                notificationService.shutdown();
            }

            logger.info("RVNKQuests plugin disabled successfully");
        } catch (Exception e) {
            logger.error("Error during plugin shutdown", e);
        }

        // Clean up loggers on shutdown
        LogManager.clearLoggers(this);
    }

    /**
     * Gets the LogManager instance for this plugin.
     * @return The LogManager instance
     */
    public LogManager getLogManager() {
        return logger;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public QuestManager getQuestManager() {
        return questManager;
    }

    /**
     * Gets the database manager instance.
     * @return The database manager
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    /**
     * Gets the quest definition repository.
     * @return The quest repository (SQL or YAML fallback)
     */
    public IQuestRepository getQuestRepository() {
        return questRepository;
    }

    /**
     * Gets the quest progress service for per-player state management.
     * @return The quest progress service
     */
    public IQuestProgressService getQuestProgressService() {
        return questProgressService;
    }

    /**
     * Gets the preference repository for player notification preferences.
     * @return The preference repository
     */
    public IPreferenceRepository getPreferenceRepository() {
        return preferenceRepository;
    }

    /**
     * Gets the fallback tracker for database failure handling.
     * @return The fallback tracker
     */
    public FallbackTracker getFallbackTracker() {
        return fallbackTracker;
    }

    /**
     * Gets the lore database instance
     * @return The lore database or null if not enabled
     */
    public LoreDatabase getLoreDatabase() {
        return loreDatabase;
    }

    /**
     * Checks if this plugin has an active lore database
     * @return true if the lore database is enabled and initialized
     */
    public boolean hasLoreDatabase() {
        return loreDatabase != null;
    }

    /**
     * Gets the reward service for reward delivery.
     * @return The reward service
     */
    public IRewardService getRewardService() {
        return rewardService;
    }

    /**
     * Gets the quest chain service for chain management.
     * @return The quest chain service
     */
    public IQuestChainService getQuestChainService() {
        return questChainService;
    }

    /**
     * Gets the objective service for objective management.
     * @return The objective service
     */
    public IObjectiveService getObjectiveService() {
        return objectiveService;
    }

    /**
     * Gets the journal service for quest history and statistics.
     * @return The journal service
     */
    public IJournalService getJournalService() {
        return journalService;
    }

    /**
     * Gets the notification service for quest notifications.
     * @return The notification service
     */
    public INotificationService getNotificationService() {
        return notificationService;
    }

    /**
     * Gets the repeatable quest service for repeatability and cooldowns.
     * @return The repeatable quest service
     */
    public IRepeatableQuestService getRepeatableQuestService() {
        return repeatableQuestService;
    }

    /**
     * Updates the log level across all plugin components.
     * This ensures consistent logging behavior throughout the plugin.
     *
     * @param level The new logging level to apply
     */
    public void updateGlobalLogLevel(Level level) {
        // Set plugin-wide log level for all existing and future LogManager instances
        LogManager.setPluginLogLevel(this, level);
    }

    // ==================== RVNKCore Integration ====================

    /**
     * Checks if RVNKCore integration is available.
     * @return true if RVNKCore services are registered
     */
    public boolean isRVNKCoreAvailable() {
        return rvnkCoreAvailable;
    }

    /**
     * Registers services with RVNKCore ServiceRegistry if available.
     * Uses reflection to avoid hard dependency on RVNKCore classes.
     */
    private void registerWithRVNKCore() {
        Plugin rvnkCorePlugin = getServer().getPluginManager().getPlugin("RVNKCore");
        if (rvnkCorePlugin == null || !rvnkCorePlugin.isEnabled()) {
            logger.info("RVNKCore not found - running in standalone mode");
            return;
        }

        try {
            // Get RVNKCore instance via static getInstance() method
            Class<?> rvnkCoreClass = Class.forName("org.fourz.rvnkcore.RVNKCore");
            Object coreInstance = rvnkCoreClass.getMethod("getInstance").invoke(null);
            if (coreInstance == null) {
                logger.warning("RVNKCore instance is null - services not registered");
                return;
            }

            // Get the ServiceRegistry from RVNKCore
            Object serviceRegistry = rvnkCoreClass.getMethod("getServiceRegistry").invoke(coreInstance);
            if (serviceRegistry == null) {
                logger.warning("RVNKCore ServiceRegistry is null - services not registered");
                return;
            }

            // Get the registerService method
            Class<?> registryClass = serviceRegistry.getClass();
            java.lang.reflect.Method registerMethod = registryClass.getMethod("registerService", Class.class, Object.class);

            // Register all services with RVNKCore ServiceRegistry
            registerMethod.invoke(serviceRegistry, IQuestService.class, questManager);
            registerMethod.invoke(serviceRegistry, IQuestProgressService.class, questProgressService);
            registerMethod.invoke(serviceRegistry, IQuestDatabaseService.class, databaseManager);
            registerMethod.invoke(serviceRegistry, IRewardService.class, rewardService);
            registerMethod.invoke(serviceRegistry, IQuestChainService.class, questChainService);
            registerMethod.invoke(serviceRegistry, IObjectiveService.class, objectiveService);
            registerMethod.invoke(serviceRegistry, IJournalService.class, journalService);
            registerMethod.invoke(serviceRegistry, IRepeatableQuestService.class, repeatableQuestService);

            rvnkCoreAvailable = true;
            rvnkCoreInstance = coreInstance;
            logger.info("RVNKCore integration enabled — 8 services registered");

            // Register notification types with PlayerPreferencesService
            registerNotificationTypes();

        } catch (ClassNotFoundException e) {
            logger.info("RVNKCore classes not found - running in standalone mode");
        } catch (Exception e) {
            logger.warning("Failed to register with RVNKCore: " + e.getMessage());
            logger.warning("Running in standalone mode");
        }
    }

    /**
     * Registers notification types with PlayerPreferencesService so players can control
     * which quest notifications they receive via /pref rvnkquests.
     *
     * <p>Wrapped in a ClassNotFoundException / NoClassDefFoundError catch so that this
     * method is safe when RVNKCore classes are absent from the classpath (standalone mode).</p>
     */
    private void registerNotificationTypes() {
        try {
            org.fourz.rvnkcore.RVNKCore core = org.fourz.rvnkcore.RVNKCore.getInstance();
            if (core == null) return;

            org.fourz.rvnkcore.service.registry.ServiceRegistry registry = core.getServiceRegistry();
            if (registry == null) return;

            org.fourz.rvnkcore.api.service.PlayerPreferencesService prefsService =
                    registry.getService(org.fourz.rvnkcore.api.service.PlayerPreferencesService.class);
            if (prefsService == null) {
                logger.debug("PlayerPreferencesService not available - notification types not registered");
                return;
            }

            java.util.List<org.fourz.rvnkcore.api.model.NotificationTypeDefinition> types =
                    java.util.Arrays.asList(
                            new org.fourz.rvnkcore.api.model.NotificationTypeDefinition(
                                    "rvnkquests", "quest_start", "New quest started", true),
                            new org.fourz.rvnkcore.api.model.NotificationTypeDefinition(
                                    "rvnkquests", "quest_complete", "Quest completed", true),
                            new org.fourz.rvnkcore.api.model.NotificationTypeDefinition(
                                    "rvnkquests", "quest_failed", "Quest failed or abandoned", true),
                            new org.fourz.rvnkcore.api.model.NotificationTypeDefinition(
                                    "rvnkquests", "objective_progress", "Objective progress updates", true),
                            new org.fourz.rvnkcore.api.model.NotificationTypeDefinition(
                                    "rvnkquests", "objective_complete", "Objective completed", true),
                            new org.fourz.rvnkcore.api.model.NotificationTypeDefinition(
                                    "rvnkquests", "quest_available", "New quest available notifications", true),
                            new org.fourz.rvnkcore.api.model.NotificationTypeDefinition(
                                    "rvnkquests", "milestone", "Quest milestone reached", true),
                            new org.fourz.rvnkcore.api.model.NotificationTypeDefinition(
                                    "rvnkquests", "chain_progress", "Quest chain progress", true)
                    );

            prefsService.registerNotificationTypes("rvnkquests", types);
            logger.debug("Registered " + types.size() + " notification types with PlayerPreferencesService");

        } catch (NoClassDefFoundError e) {
            logger.debug("RVNKCore not available for notification type registration");
        } catch (Exception e) {
            logger.debug("Failed to register notification types: " + e.getMessage());
        }
    }

    /**
     * Unregisters services from RVNKCore ServiceRegistry.
     */
    private void unregisterFromRVNKCore() {
        if (!rvnkCoreAvailable || rvnkCoreInstance == null) {
            return;
        }

        try {
            Class<?> rvnkCoreClass = rvnkCoreInstance.getClass();
            Object serviceRegistry = rvnkCoreClass.getMethod("getServiceRegistry").invoke(rvnkCoreInstance);
            if (serviceRegistry == null) {
                return;
            }

            Class<?> registryClass = serviceRegistry.getClass();
            java.lang.reflect.Method unregisterMethod = registryClass.getMethod("unregisterService", Class.class);

            // Unregister services in reverse order
            unregisterMethod.invoke(serviceRegistry, IRepeatableQuestService.class);
            unregisterMethod.invoke(serviceRegistry, IJournalService.class);
            unregisterMethod.invoke(serviceRegistry, IObjectiveService.class);
            unregisterMethod.invoke(serviceRegistry, IQuestChainService.class);
            unregisterMethod.invoke(serviceRegistry, IRewardService.class);
            unregisterMethod.invoke(serviceRegistry, IQuestDatabaseService.class);
            unregisterMethod.invoke(serviceRegistry, IQuestProgressService.class);
            unregisterMethod.invoke(serviceRegistry, IQuestService.class);

            logger.info("Services unregistered from RVNKCore");

        } catch (Exception e) {
            logger.warning("Failed to unregister from RVNKCore: " + e.getMessage());
        }

        rvnkCoreAvailable = false;
        rvnkCoreInstance = null;
    }
}
