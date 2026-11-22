package com.aicoach.report.service.engine;

public record AnswerEvaluationSnapshot(
        Integer completenessScore,
        Integer accuracyScore,
        Integer structureScore,
        Integer relevanceScore,
        Integer expressionScore,
        Integer totalScore,
        String coveredPoints,
        String missingPoints,
        String suggestions,
        String knowledgeTags
) {
}