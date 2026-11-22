package org.fourz.RVNKQuests.dungeon;

import org.fourz.rvnkcore.api.dungeon.DungeonInfo;
import org.fourz.rvnkcore.api.dungeon.Dungeons;
import org.fourz.rvnkcore.api.dungeon.IDungeonService;

import java.util.Optional;

/**
 * Preflight text for a dungeon reference (#2371). Touches RVNKCore types, so call it only after
 * {@link DungeonApi#isPresent()}. Returns {@code "ok: ..."} or {@code "blocker: ..."}.
 */
public final class DungeonPreflight {

    private DungeonPreflight() { }

    public static String describe(String id) {
        IDungeonService svc = Dungeons.resolve();
        if (!svc.isAvailable()) return "blocker: no dungeon provider (RVNKWorlds 1.6.290+ is not enabled)";
        Optional<DungeonInfo> d = svc.find(id);
        if (d.isEmpty()) return "blocker: dungeon '" + id + "' is not defined on this server (/world dungeon list)";
        DungeonInfo i = d.get();
        return "ok: dungeon " + i.id() + " in " + i.world() + " (" + svc.state(i.id()) + ", "
                + svc.remainingRoster(i.id()) + " alive)";
    }
}
