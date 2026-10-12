package org.fourz.RVNKQuests.waypoint;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.service.IRVNKWorldsApiService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * World display names from RVNKWorlds, for the cross-world waypoint line (#2264).
 *
 * <p>The service is looked up through RVNKCore's registry by its RVNKCore-declared interface, the
 * same reflection-free softdepend path as {@code WorldActivationService}. {@code listWorlds()} is
 * the call behind {@code GET /api/v1/worlds}; its payload is read in that JSON form (fields
 * {@code name} and {@code displayName}), so this class never names an RVNKWorlds type.</p>
 *
 * <p>Never blocks: the cache is filled asynchronously and read from the main thread. A miss returns
 * the world name and, at most once a minute, asks for a refresh. Without RVNKWorlds every lookup
 * returns the world name.</p>
 */
public class WorldDisplayNames {

    private static final long REFRESH_MIN_MILLIS = 60_000L;

    private final LogManager logger;
    private final Gson gson = new Gson();

    private volatile Map<String, String> names = Collections.emptyMap();
    private volatile long lastRequest = 0L;
    private volatile boolean inFlight = false;

    public WorldDisplayNames(LogManager logger) {
        this.logger = logger;
    }

    /**
     * The display name of a world, or the world name itself when RVNKWorlds does not know it.
     * Main-thread safe; never blocks.
     */
    public String displayName(String worldName) {
        if (worldName == null) return "";
        String hit = names.get(worldName.toLowerCase(Locale.ROOT));
        if (hit != null) return hit;
        refreshIfStale();
        return worldName;
    }

    /** Asks RVNKWorlds for the world list now, unless a request is running. */
    public void refresh() {
        if (inFlight) return;
        IRVNKWorldsApiService service;
        try {
            service = RVNKCore.getServiceSafe(IRVNKWorldsApiService.class);
        } catch (Throwable t) {
            service = null;
        }
        lastRequest = System.currentTimeMillis();
        if (service == null) return;
        inFlight = true;
        try {
            service.listWorlds().whenComplete((response, ex) -> {
                try {
                    if (ex == null && response != null && response.success() && response.data() != null) {
                        Map<String, String> parsed = parse(gson.toJsonTree(response.data()));
                        if (!parsed.isEmpty()) {
                            names = Collections.unmodifiableMap(parsed);
                        }
                    }
                } catch (RuntimeException e) {
                    logger.debug("World display names not read from RVNKWorlds: " + e.getMessage());
                } finally {
                    inFlight = false;
                }
            });
        } catch (RuntimeException e) {
            inFlight = false;
            logger.debug("World display names not requested from RVNKWorlds: " + e.getMessage());
        }
    }

    private void refreshIfStale() {
        if (System.currentTimeMillis() - lastRequest >= REFRESH_MIN_MILLIS) {
            refresh();
        }
    }

    /** Reads {@code [{name, displayName}, ...]}. Package-private for tests. */
    static Map<String, String> parse(JsonElement data) {
        Map<String, String> out = new HashMap<>();
        if (data == null || !data.isJsonArray()) return out;
        JsonArray array = data.getAsJsonArray();
        for (JsonElement el : array) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            JsonElement name = o.get("name");
            JsonElement display = o.get("displayName");
            if (name == null || display == null || !name.isJsonPrimitive() || !display.isJsonPrimitive()) continue;
            String n = name.getAsString();
            String d = display.getAsString();
            if (n.isBlank() || d.isBlank()) continue;
            out.put(n.toLowerCase(Locale.ROOT), d);
        }
        return out;
    }
}
