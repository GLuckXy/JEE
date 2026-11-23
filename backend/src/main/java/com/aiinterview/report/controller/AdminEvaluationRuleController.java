package com.aiinterview.report.controller;

import com.aiinterview.common.ApiResponse;
import com.aiinterview.report.service.EvaluationRules;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/evaluation-rules")
public class AdminEvaluationRuleController {
    private final EvaluationRules rules;

    public AdminEvaluationRuleController(EvaluationRules rules) { this.rules = rules; }

    @GetMapping public ApiResponse<List<EvaluationRules.Rule>> list() { return ApiResponse.ok(rules.list()); }
    @GetMapping("/{id}") public ApiResponse<EvaluationRules.Rule> find(@PathVariable String id) { return ApiResponse.ok(rules.find(id)); }
    @PostMapping public ApiResponse<EvaluationRules.Rule> create(@Valid @RequestBody EvaluationRules.Write input) {
        return ApiResponse.ok(rules.save(null, input));
    }
    @PutMapping("/{id}") public ApiResponse<EvaluationRules.Rule> update(@PathVariable String id, @Valid @RequestBody EvaluationRules.Write input) {
        return ApiResponse.ok(rules.save(id, input));
    }
    @PutMapping("/{id}/status") public ApiResponse<EvaluationRules.Rule> status(@PathVariable String id, @RequestParam boolean enabled,
                                                                                @RequestParam boolean published) {
        return ApiResponse.ok(rules.status(id, enabled, published));
    }
}
