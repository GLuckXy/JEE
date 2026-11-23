package com.aiinterview.report.persistence;

import com.aiinterview.common.JsonCodec;
import com.aiinterview.common.PageResult;
import com.aiinterview.report.domain.ReportInput;
import com.aiinterview.report.service.EvaluationRules;
import com.aiinterview.report.service.ReportService;
import com.aiinterview.report.service.engine.EvaluationResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ReportStore {
    public record Row(String id, String status, String rule, String summary, long generatedAt) {}
    public record Evaluation(String id, ReportInput.Answer answer, EvaluationResult score) {}

    private static final String HISTORY = """
            SELECT id AS session_id, mode, role_id, created_at, ended_at, NULL AS end_reason FROM training_session
              WHERE owner_id = ? AND state = 'ENDED'
            UNION ALL
            SELECT session_id, 'FORMAL' AS mode, role_id, created_at, ended_at, end_reason FROM fi_session
              WHERE student_id = ? AND ended_at > 0
            """;

    private final JdbcTemplate jdbc;
    private final JsonCodec json;
    private final RowMapper<Evaluation> evaluations = (rows, index) -> new Evaluation(rows.getString("id"),
            decode(rows.getString("snapshot"), ReportInput.Answer.class), decode(rows.getString("score"), EvaluationResult.class));

    ReportStore(JdbcTemplate jdbc, JsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Optional<Row> find(String user, String session) {
        return jdbc.query("SELECT id, status, rule_snapshot, summary, generated_at FROM rp_report WHERE user_id = ? AND session_id = ?",
                (rows, index) -> new Row(rows.getString("id"), rows.getString("status"), rows.getString("rule_snapshot"),
                        rows.getString("summary"), rows.getLong("generated_at")), user, session).stream().findFirst();
    }

    public List<Evaluation> evaluations(String user, String report) {
        return jdbc.query("""
                SELECT evaluation.id, evaluation.snapshot, evaluation.score FROM rp_answer_evaluation evaluation
                JOIN rp_report report ON report.id = evaluation.report_id WHERE report.user_id = ? AND report.id = ? ORDER BY evaluation.ordinal
                """, evaluations, user, report);
    }

    public Optional<Evaluation> evaluation(String user, String id) {
        return jdbc.query("""
                SELECT evaluation.id, evaluation.snapshot, evaluation.score FROM rp_answer_evaluation evaluation
                JOIN rp_report report ON report.id = evaluation.report_id WHERE report.user_id = ? AND evaluation.id = ?
                """, evaluations, user, id).stream().findFirst();
    }

    public String save(String user, String session, String status, EvaluationRules.Rule rule, ReportService.Summary summary,
                List<Evaluation> results) {
        var existing = find(user, session);
        if (existing.filter(row -> "COMPLETED".equals(row.status())).isPresent()) return existing.get().id();
        long now = System.currentTimeMillis();
        String ruleJson = rule == null ? null : json.write(rule);
        String summaryJson = summary == null ? null : json.write(summary);
        String id = existing.map(Row::id).orElseGet(() -> UUID.randomUUID().toString());
        if (existing.isEmpty()) {
            jdbc.update("INSERT INTO rp_report (id, user_id, session_id, status, rule_snapshot, summary, generated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    id, user, session, status, ruleJson, summaryJson, now);
        } else {
            jdbc.update("UPDATE rp_report SET status = ?, rule_snapshot = ?, summary = ?, generated_at = ? WHERE id = ? AND user_id = ?",
                    status, ruleJson, summaryJson, now, id, user);
        }
        for (int ordinal = 0; ordinal < results.size(); ordinal++) {
            var evaluation = results.get(ordinal);
            jdbc.update("INSERT INTO rp_answer_evaluation (id, report_id, ordinal, snapshot, score) VALUES (?, ?, ?, ?, ?)",
                    evaluation.id(), id, ordinal, json.write(evaluation.answer()), evaluation.score() == null ? null : json.write(evaluation.score()));
        }
        return id;
    }

    public PageResult<ReportService.HistoryItem> history(String user, String mode, int page, int size) {
        int offset = PageResult.offset(page, size);
        String sessions = " FROM (" + HISTORY + ") history ";
        long total = jdbc.queryForObject("SELECT COUNT(*)" + sessions + "WHERE (? IS NULL OR history.mode = ?)",
                Long.class, user, user, mode, mode);
        var records = jdbc.query("SELECT history.*, report.status" + sessions + """
                LEFT JOIN rp_report report ON report.session_id = history.session_id AND report.user_id = ?
                WHERE (? IS NULL OR history.mode = ?) ORDER BY history.ended_at DESC, history.session_id DESC LIMIT ? OFFSET ?
                """, (rows, index) -> new ReportService.HistoryItem(rows.getString("session_id"), rows.getString("mode"),
                rows.getLong("role_id"), rows.getLong("created_at"), rows.getLong("ended_at"), rows.getString("end_reason"),
                Optional.ofNullable(rows.getString("status")).orElse("PENDING")), user, user, user, mode, mode, size, offset);
        return new PageResult<>(total, page, size, records);
    }

    public ReportService.RetrainLink link(String user, String report, String evaluation, String target) {
        var link = new ReportService.RetrainLink(UUID.randomUUID().toString(), evaluation, target, System.currentTimeMillis());
        jdbc.update("INSERT INTO rp_retrain_link (id, user_id, report_id, answer_evaluation_id, target_session_id, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                link.id(), user, report, evaluation, target, link.createdAt());
        return link;
    }

    public List<ReportService.RetrainLink> links(String user, String report) {
        return jdbc.query("SELECT * FROM rp_retrain_link WHERE user_id = ? AND report_id = ? ORDER BY created_at DESC, id",
                (rows, index) -> new ReportService.RetrainLink(rows.getString("id"), rows.getString("answer_evaluation_id"),
                        rows.getString("target_session_id"), rows.getLong("created_at")), user, report);
    }

    private <T> T decode(String value, Class<T> type) { return json.read(value, type); }
}
