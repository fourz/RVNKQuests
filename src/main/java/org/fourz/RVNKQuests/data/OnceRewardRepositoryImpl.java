package org.fourz.RVNKQuests.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * SQL store for {@code once: server} rewards (#2268), table {@code quest_once_rewards}.
 *
 * <p>The claim is one statement: {@code INSERT IGNORE} on MySQL, {@code INSERT OR IGNORE} on
 * SQLite, against the primary key {@code (quest_id, reward_id)}. The database decides the race:
 * the statement's update count is 1 for the one caller that inserted and 0 for everyone else. No
 * read-then-write, so there is no window between a check and an insert.</p>
 */
public class OnceRewardRepositoryImpl implements IOnceRewardStore {

    /** Supplies a pooled connection; {@code DatabaseManager::getConnection} in the plugin. */
    @FunctionalInterface
    public interface ConnectionSource {
        Connection get() throws SQLException;
    }

    private final ConnectionSource connections;
    private final String table;
    private final boolean mysql;
    private final Executor executor;

    /**
     * @param connections the connection source
     * @param table       the table name, with any configured prefix applied
     * @param mysql       true for MySQL syntax, false for SQLite
     * @param executor    the database executor
     */
    public OnceRewardRepositoryImpl(ConnectionSource connections, String table, boolean mysql, Executor executor) {
        this.connections = connections;
        this.table = table;
        this.mysql = mysql;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Boolean> tryClaim(String questId, String rewardKey, UUID firedBy) {
        String sql = (mysql ? "INSERT IGNORE INTO " : "INSERT OR IGNORE INTO ") + table
                + " (quest_id, reward_id, fired_by, fired_at) VALUES (?, ?, ?, ?)";
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connections.get();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, questId);
                ps.setString(2, rewardKey);
                ps.setString(3, firedBy.toString());
                ps.setLong(4, System.currentTimeMillis());
                return ps.executeUpdate() == 1;
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> reset(String questId, String rewardKey) {
        String sql = "DELETE FROM " + table + " WHERE quest_id = ?" + (rewardKey != null ? " AND reward_id = ?" : "");
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connections.get();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, questId);
                if (rewardKey != null) ps.setString(2, rewardKey);
                return ps.executeUpdate();
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<OnceRecord>> list(String questId) {
        String sql = "SELECT quest_id, reward_id, fired_by, fired_at FROM " + table
                + " WHERE quest_id = ? ORDER BY reward_id";
        return CompletableFuture.supplyAsync(() -> {
            List<OnceRecord> out = new ArrayList<>();
            try (Connection conn = connections.get();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, questId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID by;
                        try {
                            by = UUID.fromString(rs.getString("fired_by"));
                        } catch (IllegalArgumentException e) {
                            by = null;
                        }
                        out.add(new OnceRecord(rs.getString("quest_id"), rs.getString("reward_id"),
                                by, rs.getLong("fired_at")));
                    }
                }
                return out;
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }
}
