package org.fourz.RVNKQuests.util;

import org.bukkit.event.Listener;
import java.util.UUID;

public interface PlayerAwareListener extends Listener {
    void clearPlayerData(UUID playerUuid);
}
