package com.aicoach.report;

import com.aicoach.report.service.engine.AnswerEvaluationSnapshot;
import com.aicoach.report.service.engine.DimensionWeights;
import com.aicoach.report.service.engine.ReportAggregator;
import com.aicoach.report.service.engine.ReportSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportAggregatorTest {

    private final ReportAggregator aggregator = new ReportAggregator();

    private final DimensionWeights weights = new DimensionWeights(30, 30, 15, 15, 10);

    @Test
    void shouldKeepScoreEmptyWhenNoAnswer() {
        ReportSummary summary = aggregator.aggregate(List.of(), weights, List.of(), 5, 0, "NORMAL");
        assertNull(summary.getTotalScore());
        assertTrue(summary.getSummary().contains("没有可评价的回答"));
    }

    @Test
    void shouldAggregateDimensionAverages() {
        List<AnswerEvaluationSnapshot> snapshots = List.of(
                snapshot(80, 70, 60, 65, 70, 71, "Java基础"),
                snapshot(40, 30, 50, 45, 60, 41, "MySQL索引"));
        ReportSummary summary = aggregator.aggregate(snapshots, weights, List.of("Java基础", "MySQL索引"),
                4, 2, "EARLY_END");

        assertEquals(56, summary.getTotalScore());
        assertTrue(summary.getDimensionScores().contains("\"accuracy\":50"));
        assertTrue(summary.getWeaknessTags().contains("技术准确性"));
        assertTrue(summary.getSummary().contains("有效作答 2/4 题"));
        assertTrue(summary.getSummary().contains("提前结束"));
    }

    @Test
    void shouldMarkInterruptedTrainingAsNotAbilityPenalty() {
        ReportSummary summary = aggregator.aggregate(
                List.of(snapshot(70, 70, 70, 70, 70, 70, "并发编程")), weights,
                List.of("并发编程"), 5, 1, "INTERRUPTED");
        assertTrue(summary.getSummary().contains("中断"));
        assertTrue(summary.getSuggestions().contains("设备恢复"));
    }

    private AnswerEvaluationSnapshot snapshot(int completeness, int accuracy, int structure, int relevance,
                                              int expression, int total, String tags) {
        return new AnswerEvaluationSnapshot(completeness, accuracy, structure, relevance, expression, total,
                "", "", "", tags);
    }
}