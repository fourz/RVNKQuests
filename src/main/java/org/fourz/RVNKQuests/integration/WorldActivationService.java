package org.fourz.RVNKQuests.integration;

import org.bukkit.Bukkit;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.model.response.ApiResponse;
import org.fourz.rvnkcore.api.service.IRVNKWorldsApiService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.concurrent.CompletableFuture;

/**
 * Softdepend bridge to RVNKWorlds for making a quest's worlds usable (#1875).
 *
 * <p>A quest whose trigger sits in a world RVNKWorlds left in {@code IMPORTED} state is unplayable,
 * and nothing said so: {@code quest validate} checks definition integrity, not whether the world is
 * up. The Tales From A Hat Chapter 1 chain (#1767) sat dormant on Event that way after every
 * restart, reporting {@code [VALID]} the whole time.</p>
 *
 * <h3>Why this has no reflection, unlike {@link LoreServiceFacade}</h3>
 * RVNKLore's services live in RVNKLore's own packages, so that facade must reflect. Here the
 * contract — {@link IRVNKWorldsApiService} — is declared in <b>RVNKCore</b>, which RVNKQuests
 * hard-depends on. RVNKWorlds merely registers an implementation of it. So this compiles directly
 * against the interface and resolves the instance at runtime; absent RVNKWorlds, the lookup simply
 * returns null. That is softdepend behaviour without the reflection tax.
 *
 * <h3>Resolution is per-call, deliberately</h3>
 * The service is <b>not</b> cached at enable. RVNKWorlds may enable after RVNKQuests, and a PlugMan
 * reload swaps the registered instance — a cached reference would then point at a dead classloader.
 *
 * @since 1.1.17
 */
public class WorldActivationService {

    /**
     * RVNKWorlds answers {@code CONFLICT} when asked to load a world that is already active. That is
     * the desired end state, not a failure — and because the lazy re-check calls this on trigger
     * evaluation, treating it as an error would produce a log storm on every already-loaded world.
     */
    private static final String CODE_ALREADY_LOADED = "CONFLICT";

    /**
     * Identifies this plugin's holds to RVNKWorlds (#1883). Holds are per-holder, so this must stay
     * stable across calls or a release will not match the hold that placed it.
     */
    private static final String HOLDER_ID = "RVNKQuests";

    private final LogManager logger;

    /** Logged once, not per call — an absent RVNKWorlds is a steady state, not an incident. */
    private boolean loggedUnavailable = false;

    public WorldActivationService(RVNKQuests plugin) {
        this.logger = LogManager.getInstance(plugin, getClass());
    }

    /**
     * @return true when RVNKWorlds has registered its API service
     */
    public boolean isAvailable() {
        return resolve() != null;
    }

    /**
     * Whether a world is loaded and therefore usable by a quest.
     *
     * <p>Reads Bukkit directly rather than asking RVNKWorlds: it is synchronous, free, and the only
     * thing a quest actually needs to know. RVNKWorlds' {@code IMPORTED}/{@code ACTIVE} distinction
     * is a lifecycle concern; a quest only cares whether {@code Bukkit.getWorld} resolves.</p>
     *
     * @param worldName World name (case-insensitive — Paper 26.2 lowercased migrated world names, #1627)
     * @return true when the world is loaded
     */
    public boolean isActive(String worldName) {
        return resolveBukkitWorld(worldName) != null;
    }

    /**
     * Makes a world usable, loading it through RVNKWorlds when it is not already.
     *
     * <p>Never blocks: world loading must happen on the main thread and RVNKWorlds already schedules
     * it there, so this returns the future rather than waiting on it. (Blocking {@code future.get()}
     * calls are an open defect class in this ecosystem — #1552.)</p>
     *
     * @param worldName World to activate
     * @return future completing true when the world is usable afterwards
     */
    public CompletableFuture<Boolean> ensureActive(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        IRVNKWorldsApiService worlds = resolve();

        // Already loaded is still worth holding, and this is the COMMON case: RVNKWorlds auto-loads
        // previously-active worlds at boot, so a quest's world is usually up before the quest asks.
        // Returning early without a hold left exactly those worlds reclaimable — which is how the
        // #1883 failure was reached on Event, where alphac was auto-loaded and then swept anyway.
        if (isActive(worldName)) {
            if (worlds != null) hold(worlds, worldName);
            return CompletableFuture.completedFuture(true);
        }

        if (worlds == null) {
            logUnavailableOnce(worldName);
            return CompletableFuture.completedFuture(false);
        }

        return worlds.loadWorld(worldName)
            .thenApply(response -> interpret(worldName, response))
            .thenApply(active -> {
                if (active) hold(worlds, worldName);
                return active;
            })
            .exceptionally(ex -> {
                logger.warning("World activation failed for '" + worldName + "': " + ex.getMessage());
                return false;
            });
    }

    /**
     * Claims the world so RVNKWorlds' inactivity sweep does not reclaim it (#1883).
     *
     * <p>Loading is not keeping. {@code WorldCleanupScheduler} unloads any unprotected world left
     * empty past the inactivity threshold and writes it back to {@code IMPORTED} — and a world just
     * activated for a quest has, by definition, nobody standing in it. Observed on Event
     * 2026-08-01: {@code zeal} activated at 08:32 was {@code IMPORTED} and un-teleportable by 09:22,
     * so the quest that asked for it had gone quietly unplayable again.</p>
     *
     * <p>Best-effort by design. An older RVNKWorlds answers {@code NOT_SUPPORTED} via the interface
     * default; the world is still loaded and the quest still works, it is merely reclaimable again.
     * That is strictly better than failing the activation, so a hold failure is logged at debug and
     * never propagates.</p>
     */
    private void hold(IRVNKWorldsApiService worlds, String worldName) {
        try {
            worlds.holdWorld(worldName, HOLDER_ID).thenAccept(response -> {
                if (response != null && response.success()) {
                    logger.debug("Holding world '" + worldName + "' against inactivity cleanup");
                } else {
                    String msg = (response != null && response.error() != null)
                        ? response.error().message() : "no response";
                    logger.debug("Could not hold world '" + worldName + "' (" + msg
                        + ") - it stays loaded but may be reclaimed by cleanup");
                }
            });
        } catch (Exception e) {
            logger.debug("Hold request failed for '" + worldName + "': " + e.getMessage());
        }
    }

    /**
     * Drops this plugin's hold on a world.
     *
     * <p>Called when a quest's declared worlds change on reload, so a world nobody declares any more
     * becomes cleanup-eligible again rather than being pinned in memory forever.</p>
     *
     * @param worldName World to release
     */
    public void release(String worldName) {
        IRVNKWorldsApiService worlds = resolve();
        if (worlds == null || worldName == null || worldName.isBlank()) return;
        try {
            worlds.releaseWorld(worldName, HOLDER_ID);
        } catch (Exception e) {
            logger.debug("Release request failed for '" + worldName + "': " + e.getMessage());
        }
    }

    /**
     * Collapses RVNKWorlds' response envelope to a plain boolean.
     *
     * <p>This is the only place {@link ApiResponse} is touched — the REST envelope must not leak
     * into the quest engine.</p>
     */
    private boolean interpret(String worldName, ApiResponse<?> response) {
        if (response == null) {
            logger.warning("World activation returned no response for '" + worldName + "'");
            return false;
        }
        if (response.success()) {
            logger.info("Activated world '" + worldName + "' for a quest requirement");
            return true;
        }

        String code = response.error() != null ? response.error().code() : null;
        if (CODE_ALREADY_LOADED.equals(code)) {
            // Already active — the goal, reached by someone else first.
            return true;
        }

        String message = response.error() != null ? response.error().message() : "unknown error";
        logger.warning("Could not activate world '" + worldName + "': " + message);
        return false;
    }

    /**
     * Case-insensitive Bukkit world lookup.
     *
     * <p>{@code Bukkit.getWorld(String)} is case-sensitive, and world-name case genuinely varies on
     * these servers — RVNKWorlds logs mismatches like {@code Diaspora_the_end} against a live
     * {@code diaspora_the_end} (#1627). An exact-match-only check would report a loaded world as
     * missing and trigger a pointless activation attempt.</p>
     */
    private org.bukkit.World resolveBukkitWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) return null;

        org.bukkit.World exact = Bukkit.getWorld(worldName);
        if (exact != null) return exact;

        for (org.bukkit.World world : Bukkit.getWorlds()) {
            if (world.getName().equalsIgnoreCase(worldName)) {
                return world;
            }
        }
        return null;
    }

    private IRVNKWorldsApiService resolve() {
        return RVNKCore.getServiceSafe(IRVNKWorldsApiService.class);
    }

    private void logUnavailableOnce(String worldName) {
        if (loggedUnavailable) return;
        loggedUnavailable = true;
        logger.warning("RVNKWorlds is not available - cannot activate quest world '" + worldName
            + "'. Quests targeting unloaded worlds will stay unplayable until it is installed "
            + "or the world is loaded manually.");
    }
}
