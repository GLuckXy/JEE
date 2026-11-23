package com.aiinterview.report.service.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleEvaluationEngineTest {

    private final ReferencePointAnalyzer analyzer = new ReferencePointAnalyzer();

    private final RuleBasedEvaluator evaluator = new RuleBasedEvaluator(analyzer);

    private final DimensionWeights weights = new DimensionWeights(30, 30, 15, 15, 10);

    @Test
    void shouldSplitReferencePointsIntoSeparatedItems() {
        assertEquals(3, analyzer.splitPoints("要说清线程池核心参数；说明拒绝策略；补充队列长度设置").size());
    }

    @Test
    void shouldMarkCoveredAndMissingPoints() {
        PointAnalysis analysis = analyzer.analyze(
                "首先我会设置核心线程数和最大线程数，其次配置任务队列长度避免任务丢失。",
                "核心线程数与最大线程数配置；任务队列长度与拒绝策略；线程复用带来的开销降低");
        assertTrue(analysis.isSufficientEvidence());
        assertEquals(1, analysis.getCovered().size());
        assertEquals(2, analysis.getMissing().size());
        assertTrue(analysis.getCovered().get(0).contains("核心线程数"));
        assertTrue(analysis.getMissing().contains("线程复用带来的开销降低"));
        assertEquals(33, analysis.getCoverageRate());
    }

    @Test
    void shouldDegradeWhenReferencePointsMissing() {
        PointAnalysis analysis = analyzer.analyze("我会先说明使用的技术栈，再解释职责。", "");
        assertFalse(analysis.isSufficientEvidence());
        EvaluationResult result = evaluator.evaluate("我会先说明使用的技术栈，再解释职责。", "", null, weights, 60);
        assertTrue(result.getEvidence().contains("依据不足"));
        assertEquals("RULE", result.getSource());
    }

    @Test
    void shouldGiveZeroForEmptyAnswer() {
        EvaluationResult result = evaluator.evaluate("", "参考要点一；参考要点二", null, weights, 0);
        assertEquals(0, result.getTotalScore());
    }

    @Test
    void shouldScoreHigherForCompleteAnswer() {
        EvaluationResult weak = evaluator.evaluate("不知道。", "线程池参数；拒绝策略", null, weights, 30);
        EvaluationResult strong = evaluator.evaluate(
                "首先，线程池的核心线程数与最大线程数需要按任务类型设置；其次，任务队列长度与拒绝策略要配合，队列过大会导致任务堆积，"
                        + "因此我们选择了有界队列并设置拒绝策略，最后通过线程复用降低了创建销毁开销。",
                "线程池参数；拒绝策略", null, weights, 60);
        assertTrue(strong.getTotalScore() > weak.getTotalScore());
    }
}
