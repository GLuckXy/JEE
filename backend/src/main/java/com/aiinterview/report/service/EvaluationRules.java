package com.aiinterview.report.service;

import com.aiinterview.common.BusinessException;
import com.aiinterview.common.JsonCodec;
import com.aiinterview.report.service.engine.DimensionWeightParser;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class EvaluationRules {
    public record Rule(String id, String name, String modeScope, JsonNode weights, boolean enabled, boolean published, long updatedAt) {}
    public record Write(@NotBlank @Size(max = 80) String name, @NotNull @Pattern(regexp = "ALL|NORMAL|SPECIAL|FORMAL") String modeScope,
                        @NotNull Map<String, Integer> weights, boolean enabled, boolean published) {}

    private final JdbcTemplate jdbc;
    private final JsonCodec json;
    private final DimensionWeightParser weights;
    private final RowMapper<Rule> mapper = (rows, index) -> new Rule(rows.getString("id"), rows.getString("name"),
            rows.getString("mode_scope"), jsonTree(rows.getString("dimension_weights")), rows.getBoolean("enabled"),
            rows.getBoolean("published"), rows.getLong("updated_at"));

    public EvaluationRules(JdbcTemplate jdbc, JsonCodec json, DimensionWeightParser weights) {
        this.jdbc = jdbc;
        this.json = json;
        this.weights = weights;
    }

    public List<Rule> list() {
        return jdbc.query("SELECT * FROM rp_evaluation_rule ORDER BY updated_at DESC, id DESC", mapper);
    }

    public Rule find(String id) {
        return jdbc.query("SELECT * FROM rp_evaluation_rule WHERE id = ?", mapper, id).stream().findFirst()
                .orElseThrow(() -> BusinessException.missing("RULE_NOT_FOUND"));
    }

    public Rule resolve(String mode) {
        return jdbc.query("""
                SELECT * FROM rp_evaluation_rule WHERE enabled = TRUE AND published = TRUE AND mode_scope IN (?, 'ALL')
                ORDER BY CASE WHEN mode_scope = ? THEN 0 ELSE 1 END, updated_at DESC, id DESC LIMIT 1
                """, mapper, mode, mode).stream().findFirst().orElseGet(() -> new Rule(null, "默认五维规则", "ALL",
                jsonTree(weights.format(weights.defaultWeights())), true, true, 0));
    }

    public Rule save(String id, Write input) {
        String normalized = weights.validateAndSerialize(input.weights());
        long now = System.currentTimeMillis();
        if (id == null) {
            id = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO rp_evaluation_rule (id, name, mode_scope, dimension_weights, enabled, published, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    id, input.name().strip(), input.modeScope(), normalized, input.enabled(), input.published(), now);
        } else if (jdbc.update("UPDATE rp_evaluation_rule SET name = ?, mode_scope = ?, dimension_weights = ?, enabled = ?, published = ?, updated_at = ? WHERE id = ?",
                input.name().strip(), input.modeScope(), normalized, input.enabled(), input.published(), now, id) == 0) {
            throw BusinessException.missing("RULE_NOT_FOUND");
        }
        return find(id);
    }

    public Rule status(String id, boolean enabled, boolean published) {
        if (jdbc.update("UPDATE rp_evaluation_rule SET enabled = ?, published = ?, updated_at = ? WHERE id = ?",
                enabled, published, System.currentTimeMillis(), id) == 0) throw BusinessException.missing("RULE_NOT_FOUND");
        return find(id);
    }

    private JsonNode jsonTree(String value) { return json.tree(value); }
}
