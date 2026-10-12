package org.fourz.RVNKQuests.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.service.ISiteLocatorService;

/**
 * Resolves a quest component's {@code site} key (for example {@code drowned:trail:2}) to the centre of the site's
 * block in its world, through RVNKCore's {@link ISiteLocatorService} (#2316). The site's position comes from the
 * plugin that generated the world - RVNKWorlds for a drowned_world glyph trail - so a quest file names the site and
 * never its coordinates.
 *
 * <p>A found site is kept: sites are a pure function of the world's seed. A miss is not kept, so a world that loads
 * later (or a provider that registers later) is picked up on the next check.</p>
 */
public final class SiteResolver {

    private static final Map<String, double[]> CACHE = new ConcurrentHashMap<>();
    private static volatile BiFunction<String, String, double[]> lookup = SiteResolver::fromCore;

    private SiteResolver() {
    }

    /** The centre {x, y, z} of the site's block, or null when no provider knows it (yet). */
    public static double[] resolve(String world, String key) {
        if (world == null || key == null) return null;
        String k = world + "|" + key;
        double[] p = CACHE.get(k);
        if (p != null) return p;
        p = lookup.apply(world, key);
        if (p != null) CACHE.put(k, p);
        return p;
    }

    private static double[] fromCore(String world, String key) {
        ISiteLocatorService sites = RVNKCore.getServiceSafe(ISiteLocatorService.class);
        if (sites == null) return null;
        ISiteLocatorService.Site s = sites.locate(world, key);
        return s == null ? null : new double[] {s.x() + 0.5, s.y() + 0.5, s.z() + 0.5};
    }

    /** Test seam: replaces the lookup and clears what was kept; null restores RVNKCore. */
    public static void setLookup(BiFunction<String, String, double[]> l) {
        lookup = l != null ? l : SiteResolver::fromCore;
        CACHE.clear();
    }
}
