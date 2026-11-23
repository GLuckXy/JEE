package com.aiinterview.report.service;

import com.aiinterview.common.BusinessException;
import com.aiinterview.common.JsonCodec;
import com.aiinterview.common.PageResult;
import com.aiinterview.report.domain.ReportInput;
import com.aiinterview.report.domain.SessionCompleted;
import com.aiinterview.report.integration.AiEvaluationClient;
import com.aiinterview.report.persistence.ReportStore;
import com.aiinterview.report.service.engine.AnswerEvaluationSnapshot;
import com.aiinterview.report.service.engine.DimensionWeightParser;
import com.aiinterview.report.service.engine.DimensionWeights;
import com.aiinterview.report.service.engine.EvaluationResult;
import com.aiinterview.report.service.engine.ReportAggregator;
import com.aiinterview.report.service.engine.RuleBasedEvaluator;
import com.aiinterview.training.service.TrainingService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReportService {
    public record HistoryItem(String sessionId, String mode, Long roleId, long createdAt, long endedAt, String endReason, String status) {}
    public record Summary(Integer totalScore, JsonNode dimensionScores, String summary, String weaknessTags, String suggestions, int answeredCount) {}
    public record AnswerView(String id, String sourceId, Long questionId, Long categoryId, String topic, String stage, boolean followUp,
                             int position, int round, String question, List<String> referencePoints, String text, String audioId,
                             long effectiveMs, boolean timedOut, String status, EvaluationResult evaluation) {}
    public record RetrainLink(String id, String answerId, String targetSessionId, long createdAt) {}
    public record Detail(String id, String sessionId, String mode, Long roleId, String projectId, long createdAt, long endedAt,
                         long effectiveMs, String endReason, int questionCount, String status, long generatedAt, Summary summary,
                         String failureReason, EvaluationRules.Rule rule, List<AnswerView> answers, List<RetrainLink> retrainLinks) {}

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private final ReportInputFacade inputs;
    private final ReportStore store;
    private final EvaluationRules rules;
    private final JsonCodec json;
    private final RuleBasedEvaluator ruleEvaluator;
    private final AiEvaluationClient ai;
    private final ReportAggregator aggregator;
    private final DimensionWeightParser weights;
    private final TrainingService training;
    private final TransactionTemplate transactions;
    private final List<ReentrantLock> locks = IntStream.range(0, 64).mapToObj(index -> new ReentrantLock()).toList();

    public ReportService(ReportInputFacade inputs, ReportStore store, EvaluationRules rules, JsonCodec json, RuleBasedEvaluator ruleEvaluator,
                         AiEvaluationClient ai, ReportAggregator aggregator, DimensionWeightParser weights, TrainingService training,
                         PlatformTransactionManager manager) {
        this.inputs = inputs;
        this.store = store;
        this.rules = rules;
        this.json = json;
        this.ruleEvaluator = ruleEvaluator;
        this.ai = ai;
        this.aggregator = aggregator;
        this.weights = weights;
        this.training = training;
        transactions = new TransactionTemplate(manager);
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onSessionCompleted(SessionCompleted event) {
        try {
            generate(event.userId(), event.sessionId());
        } catch (RuntimeException error) {
            log.warn("Automatic report generation unavailable; type={}", error.getClass().getSimpleName());
        }
    }

    public Detail generate(String user, String session) {
        var input = inputs.requireCompleted(user, session);
        var lock = locks.get(Math.floorMod(session.hashCode(), locks.size()));
        lock.lock();
        try {
            if (store.find(user, session).filter(row -> "COMPLETED".equals(row.status())).isEmpty()) {
                try {
                    var rule = rules.resolve(input.mode());
                    var dimensionWeights = weights.parse(rule.weights().toString());
                    var results = input.answers().stream().map(answer -> evaluate(answer, dimensionWeights)).toList();
                    var summary = summarize(input, results, dimensionWeights);
                    transactions.executeWithoutResult(transaction -> store.save(user, session, "COMPLETED", rule, summary, results));
                } catch (RuntimeException error) {
                    log.warn("Report generation failed; type={}", error.getClass().getSimpleName());
                    transactions.executeWithoutResult(transaction -> store.save(user, session, "FAILED", null, null, List.of()));
                }
            }
            return detail(user, input);
        } finally {
            lock.unlock();
        }
    }

    public Detail detail(String user, String session) {
        return detail(user, inputs.requireCompleted(user, session));
    }

    public AnswerView evaluation(String user, String id) {
        return store.evaluation(user, id).map(this::answerView).orElseThrow(() -> BusinessException.missing("EVALUATION_NOT_FOUND"));
    }

    public PageResult<HistoryItem> history(String user, String mode, int page, int size) {
        if (mode != null && !List.of("NORMAL", "SPECIAL", "FORMAL").contains(mode))
            throw BusinessException.bad("INVALID_REQUEST", "训练类型无效");
        return store.history(user, mode, page, size);
    }

    public RetrainLink retrain(String user, String session, String evaluationId) {
        var input = inputs.requireCompleted(user, session);
        var report = store.find(user, session).filter(row -> "COMPLETED".equals(row.status()))
                .orElseThrow(() -> BusinessException.conflict("REPORT_NOT_READY", "请先生成完整报告"));
        var source = store.evaluations(user, report.id()).stream().filter(item -> item.id().equals(evaluationId))
                .map(ReportStore.Evaluation::answer).filter(answer -> answer.answered() && answer.categoryId() != null)
                .findFirst().orElseThrow(() -> BusinessException.missing("RETRAIN_SOURCE_NOT_FOUND"));
        return transactions.execute(transaction -> {
            var target = training.retrainFromReport(user, input.roleId(), source.categoryId(), source.questionId());
            return store.link(user, report.id(), evaluationId, target.id());
        });
    }

    private Detail detail(String user, ReportInput input) {
        var row = store.find(user, input.sessionId()).orElse(null);
        return new Detail(row == null ? null : row.id(), input.sessionId(), input.mode(), input.roleId(), input.projectId(),
                input.createdAt(), input.endedAt(), input.effectiveMs(), input.endReason(), input.questionCount(),
                row == null ? "PENDING" : row.status(), row == null ? 0 : row.generatedAt(),
                row == null ? null : json.read(row.summary(), Summary.class),
                row != null && "FAILED".equals(row.status()) ? "报告暂未生成，你的作答已保存，可重试。" : null,
                row == null ? null : json.read(row.rule(), EvaluationRules.Rule.class),
                row == null ? List.of() : store.evaluations(user, row.id()).stream().map(this::answerView).toList(),
                row == null ? List.of() : store.links(user, row.id()));
    }

    private ReportStore.Evaluation evaluate(ReportInput.Answer answer, DimensionWeights weights) {
        EvaluationResult score = null;
        if (answer.answered()) {
            String points = String.join("\n", answer.referencePoints());
            score = ai.evaluate(answer.question(), points, answer.text(), weights);
            if (score == null) score = ruleEvaluator.evaluate(answer.text(), points, answer.topic(), weights, answer.effectiveMs() / 1000);
        }
        return new ReportStore.Evaluation(UUID.randomUUID().toString(), answer, score);
    }

    private Summary summarize(ReportInput input, List<ReportStore.Evaluation> results, DimensionWeights weights) {
        var latest = new LinkedHashMap<Integer, ReportStore.Evaluation>();
        for (var result : results) if (result.score() != null) latest.put(result.answer().position(), result);
        var snapshots = latest.values().stream().map(result -> {
            var score = result.score();
            return new AnswerEvaluationSnapshot(score.getCompletenessScore(), score.getAccuracyScore(), score.getStructureScore(),
                    score.getRelevanceScore(), score.getExpressionScore(), score.getTotalScore(), score.getCoveredPoints(),
                    score.getMissingPoints(), score.getSuggestions(), result.answer().topic());
        }).toList();
        var tags = latest.values().stream().map(result -> result.answer().topic()).filter(Objects::nonNull).distinct().toList();
        String finish = "EARLY_EXIT".equals(input.endReason()) ? "EARLY_END" : input.endReason();
        var aggregate = aggregator.aggregate(snapshots, weights, tags, input.questionCount(), latest.size(), finish);
        return new Summary(aggregate.getTotalScore(), json.tree(aggregate.getDimensionScores()), aggregate.getSummary(),
                aggregate.getWeaknessTags(), aggregate.getSuggestions(), latest.size());
    }

    private AnswerView answerView(ReportStore.Evaluation item) {
        var answer = item.answer();
        return new AnswerView(item.id(), answer.id(), answer.questionId(), answer.categoryId(), answer.topic(), answer.stage(),
                answer.followUp(), answer.position(), answer.round(), answer.question(), answer.answered() ? answer.referencePoints() : List.of(),
                answer.text(), answer.audioId(), answer.effectiveMs(), answer.timedOut(), answer.status(), item.score());
    }
}
