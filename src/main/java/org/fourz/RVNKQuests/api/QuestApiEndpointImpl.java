package org.fourz.RVNKQuests.api;

import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.IQuestRepository;
import org.fourz.RVNKQuests.data.dto.ObjectiveDTO;
import org.fourz.RVNKQuests.data.dto.QuestDTO;
import org.fourz.RVNKQuests.data.dto.QuestObjectiveProgressDTO;
import org.fourz.RVNKQuests.data.dto.QuestProgressDTO;
import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.service.IQuestProgressService;
import org.fourz.rvnkcore.api.model.response.ApiResponse;
import org.fourz.rvnkcore.api.service.IQuestsApiService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST endpoint implementation for RVNKQuests (#2043) — the plugin half of the
 * Quests API. RVNKCore's QuestsController routes {@code /api/quests/*} here once
 * this class is registered against {@link IQuestsApiService} in ServiceRegistry.
 *
 * <p>Read surface only for now: definitions, one definition, player progress.
 * The {@code assignQuest} default (NOT_SUPPORTED) is deliberately not overridden —
 * assignment crosses the quest state machine and lands separately (#2042 notes).</p>
 *
 * <p>All data comes from the existing repositories/services, never raw SQL, and
 * every method returns without touching the main thread. DTOs are flattened to
 * plain maps so serialization never depends on Gson's record or Instant handling.</p>
 */
public class QuestApiEndpointImpl implements IQuestsApiService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final RVNKQuests plugin;

    public QuestApiEndpointImpl(RVNKQuests plugin) {
        this.plugin = plugin;
    }

    // ── IQuestsApiService ────────────────────────────────────────────────────

    @Override
    public CompletableFuture<ApiResponse<?>> getQuests(Map<String, String> params) {
        IQuestRepository repo = plugin.getQuestRepository();
        if (repo == null) {
            return unavailable("quest repository");
        }
        String category = params.get("category");
        int page = parseIntOrDefault(params.get("page"), 1);
        int limit = clamp(parseIntOrDefault(params.get("limit"), DEFAULT_LIMIT), 1, MAX_LIMIT);

        CompletableFuture<List<QuestDTO>> source =
            (category != null && !category.isBlank())
                ? repo.findByCategory(category)
                : repo.findAll();

        return source.<ApiResponse<?>>thenApply(quests -> {
            int total = quests.size();
            int from = Math.min((page - 1) * limit, total);
            int to = Math.min(from + limit, total);
            List<Map<String, Object>> out = new ArrayList<>();
            for (QuestDTO q : quests.subList(from, to)) {
                out.add(questSummary(q));
            }
            return (ApiResponse<?>) ApiResponse.success((Object) out, page, limit, total);
        }).exceptionally(this::internalError);
    }

    @Override
    public CompletableFuture<ApiResponse<?>> getQuestById(String questId) {
        IQuestRepository repo = plugin.getQuestRepository();
        if (repo == null) {
            return unavailable("quest repository");
        }
        return repo.findById(questId).thenCompose(opt -> {
            if (opt.isEmpty()) {
                return CompletableFuture.completedFuture(
                    ApiResponse.error("NOT_FOUND", "No quest with id '" + questId + "'"));
            }
            QuestDTO quest = opt.get();
            // findById may not hydrate the child lists; fetch them explicitly.
            CompletableFuture<List<ObjectiveDTO>> objectives = repo.findObjectives(questId);
            CompletableFuture<List<RewardDTO>> rewards = repo.findRewards(questId);
            return objectives.thenCombine(rewards, (objs, rews) -> {
                Map<String, Object> out = questSummary(quest);
                out.put("objectives", objs.stream().map(this::objective).toList());
                out.put("rewards", rews.stream().map(this::reward).toList());
                return (ApiResponse<?>) ApiResponse.success(out);
            });
        }).exceptionally(this::internalError);
    }

    @Override
    public CompletableFuture<ApiResponse<?>> getPlayerProgress(String playerUuid) {
        IQuestProgressService progress = plugin.getQuestProgressService();
        if (progress == null) {
            return unavailable("quest progress service");
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(playerUuid);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(
                ApiResponse.error("INVALID_REQUEST", "Not a UUID: '" + playerUuid + "'"));
        }
        return progress.getAllProgress(uuid).<ApiResponse<?>>thenCompose(records -> {
            List<CompletableFuture<Map<String, Object>>> rows = new ArrayList<>();
            for (QuestProgressDTO rec : records) {
                rows.add(progress.getAllObjectives(uuid, rec.questId())
                    .thenApply(objs -> {
                        Map<String, Object> row = progressRow(rec);
                        row.put("objectives", objs.stream().map(this::objectiveProgress).toList());
                        return row;
                    }));
            }
            return CompletableFuture.allOf(rows.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> {
                    List<Map<String, Object>> out = rows.stream().map(CompletableFuture::join).toList();
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("playerUuid", uuid.toString());
                    body.put("quests", out);
                    return (ApiResponse<?>) ApiResponse.success(body);
                });
        }).exceptionally(this::internalError);
    }

    // ── DTO flattening ───────────────────────────────────────────────────────

    private Map<String, Object> questSummary(QuestDTO q) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("questId", q.questId());
        out.put("name", q.name());
        out.put("description", q.description());
        out.put("category", q.category());
        out.put("repeatable", q.repeatable());
        out.put("cooldownMinutes", q.cooldownMinutes());
        out.put("prerequisites", q.prerequisites());
        out.put("createdAt", iso(q.createdAt()));
        out.put("metadata", q.metadata());
        return out;
    }

    private Map<String, Object> objective(ObjectiveDTO o) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("objectiveId", o.objectiveId());
        out.put("type", o.type() != null ? o.type().name() : null);
        out.put("target", o.target());
        out.put("requiredAmount", o.requiredAmount());
        out.put("description", o.description());
        out.put("order", o.order());
        return out;
    }

    private Map<String, Object> reward(RewardDTO r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rewardId", r.rewardId());
        out.put("type", r.type() != null ? r.type().name() : null);
        out.put("value", r.value());
        out.put("amount", r.amount());
        out.put("description", r.description());
        return out;
    }

    private Map<String, Object> progressRow(QuestProgressDTO rec) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("questId", rec.questId());
        out.put("state", rec.state() != null ? rec.state().name() : null);
        out.put("pathChoice", rec.pathChoice());
        out.put("startedAt", iso(rec.startedAt()));
        out.put("completedAt", iso(rec.completedAt()));
        return out;
    }

    private Map<String, Object> objectiveProgress(QuestObjectiveProgressDTO o) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("objectiveId", o.objectiveId());
        out.put("progressCount", o.progressCount());
        out.put("targetCount", o.targetCount());
        out.put("completed", o.completed());
        out.put("completedAt", iso(o.completedAt()));
        return out;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String iso(Instant t) {
        return t == null ? null : t.toString();
    }

    private static int parseIntOrDefault(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private CompletableFuture<ApiResponse<?>> unavailable(String what) {
        return CompletableFuture.completedFuture(
            ApiResponse.error("INTERNAL_ERROR", "RVNKQuests " + what + " is not initialised"));
    }

    private ApiResponse<?> internalError(Throwable t) {
        plugin.getLogger().warning("Quest API request failed: " + t.getMessage());
        return ApiResponse.error("INTERNAL_ERROR", "Quest data lookup failed");
    }
}
