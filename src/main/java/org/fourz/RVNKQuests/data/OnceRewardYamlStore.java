package org.fourz.RVNKQuests.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * {@code once: server} records for the YAML storage mode (#2268): {@code once_rewards.yml} in the
 * plugin folder.
 *
 * <p>Every operation holds this object's lock across read, decide and write, so the claim is
 * atomic within the server, which is the only writer of the file. A save that fails leaves the
 * claim unrecorded and completes exceptionally, so the reward does not fire.</p>
 */
public class OnceRewardYamlStore implements IOnceRewardStore {

    private final File file;

    /** @param file the YAML file; created on the first claim */
    public OnceRewardYamlStore(File file) {
        this.file = file;
    }

    private static String path(String questId, String rewardKey) {
        // YAML path separator is '.', so it is escaped out of ids that carry one.
        return "fired." + questId.replace('.', '_') + "." + rewardKey.replace('.', '_');
    }

    @Override
    public synchronized CompletableFuture<Boolean> tryClaim(String questId, String rewardKey, UUID firedBy) {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String p = path(questId, rewardKey);
            if (yaml.contains(p)) {
                return CompletableFuture.completedFuture(false);
            }
            yaml.set(p + ".reward_id", rewardKey);
            yaml.set(p + ".fired_by", firedBy.toString());
            yaml.set(p + ".fired_at", System.currentTimeMillis());
            yaml.save(file);
            return CompletableFuture.completedFuture(true);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(new UncheckedIOException(e));
        }
    }

    @Override
    public synchronized CompletableFuture<Integer> reset(String questId, String rewardKey) {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            int removed;
            if (rewardKey != null) {
                String p = path(questId, rewardKey);
                removed = yaml.contains(p) ? 1 : 0;
                yaml.set(p, null);
            } else {
                String p = "fired." + questId.replace('.', '_');
                ConfigurationSection section = yaml.getConfigurationSection(p);
                removed = section == null ? 0 : section.getKeys(false).size();
                yaml.set(p, null);
            }
            if (removed > 0) yaml.save(file);
            return CompletableFuture.completedFuture(removed);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(new UncheckedIOException(e));
        }
    }

    @Override
    public synchronized CompletableFuture<List<OnceRecord>> list(String questId) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<OnceRecord> out = new ArrayList<>();
        ConfigurationSection section = yaml.getConfigurationSection("fired." + questId.replace('.', '_'));
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection r = section.getConfigurationSection(key);
                if (r == null) continue;
                UUID by;
                try {
                    by = UUID.fromString(r.getString("fired_by", ""));
                } catch (IllegalArgumentException e) {
                    by = null;
                }
                out.add(new OnceRecord(questId, r.getString("reward_id", key), by, r.getLong("fired_at")));
            }
        }
        out.sort(Comparator.comparing(OnceRecord::rewardKey));
        return CompletableFuture.completedFuture(out);
    }
}
