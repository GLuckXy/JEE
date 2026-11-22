package com.aicoach.report.service.engine;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationResult {

    private int completenessScore;

    private int accuracyScore;

    private int structureScore;

    private int relevanceScore;

    private int expressionScore;

    private int totalScore;

    private String coveredPoints;

    private String missingPoints;

    private String evidence;

    private String suggestions;

    private String source;
}
