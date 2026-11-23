package com.aiinterview.report.service.engine;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class RuleBasedEvaluator {

    private static final int IDEAL_CHARS = 160;

    private static final int MIN_MEANINGFUL_CHARS = 20;

    private static final List<String> LOGIC_MARKERS = List.of(
            "首先", "其次", "然后", "接着", "最后", "第一", "第二", "第三",
            "因为", "所以", "因此", "由于", "导致", "从而", "结果", "目的是", "背景");

    private final ReferencePointAnalyzer analyzer;

    public EvaluationResult evaluate(String answerText, String referencePoints, String knowledgeTags,
                                     DimensionWeights weights, long expectedSeconds) {
        String answer = answerText == null ? "" : answerText.trim();
        int chars = analyzer.normalize(answer).length();
        PointAnalysis pointAnalysis = analyzer.analyze(answer, referencePoints);

        int completeness = scoreCompleteness(pointAnalysis, chars);
        int accuracy = scoreAccuracy(pointAnalysis, chars);
        int structure = scoreStructure(answer, weights);
        int relevance = scoreRelevance(answer, referencePoints, pointAnalysis, chars);
        int expression = scoreExpression(answer, weights, expectedSeconds);

        int total = weightedTotal(completeness, accuracy, structure, relevance, expression, weights);
        String evidence = buildEvidence(answer, pointAnalysis, expectedSeconds);
        String suggestions = buildSuggestions(pointAnalysis, structure, expression, relevance);

        return EvaluationResult.builder()
                .completenessScore(completeness)
                .accuracyScore(accuracy)
                .structureScore(structure)
                .relevanceScore(relevance)
                .expressionScore(expression)
                .totalScore(total)
                .coveredPoints(join(pointAnalysis.getCovered()))
                .missingPoints(join(pointAnalysis.getMissing()))
                .evidence(evidence)
                .suggestions(suggestions)
                .source("RULE")
                .build();
    }

    public int weightedTotal(int completeness, int accuracy, int structure, int relevance, int expression,
                             DimensionWeights weights) {
        int totalWeight = weights.total();
        if (totalWeight <= 0) {
            return 0;
        }
        double sum = completeness * weights.get(DimensionWeights.COMPLETENESS)
                + accuracy * weights.get(DimensionWeights.ACCURACY)
                + structure * weights.get(DimensionWeights.STRUCTURE)
                + relevance * weights.get(DimensionWeights.RELEVANCE)
                + expression * weights.get(DimensionWeights.EXPRESSION);
        return (int) Math.round(sum / totalWeight);
    }

    private int scoreCompleteness(PointAnalysis pointAnalysis, int chars) {
        if (chars == 0) {
            return 0;
        }
        int coverage = pointAnalysis.isSufficientEvidence() ? pointAnalysis.getCoverageRate() : 50;
        int lengthScore = Math.min(100, (int) Math.round(chars * 100.0 / IDEAL_CHARS));
        return clamp((int) Math.round(coverage * 0.8 + lengthScore * 0.2));
    }

    private int scoreAccuracy(PointAnalysis pointAnalysis, int chars) {
        if (chars == 0) {
            return 0;
        }
        if (!pointAnalysis.isSufficientEvidence()) {
            return 50;
        }
        int coverage = pointAnalysis.getCoverageRate();
        int penalty = 0;
        if (chars < MIN_MEANINGFUL_CHARS) {
            penalty = 30;
        } else if (chars < IDEAL_CHARS / 4) {
            penalty = 15;
        }
        int score = coverage - penalty;
        return clamp(Math.min(score, 60 + coverage / 5));
    }

    private int scoreStructure(String answer, DimensionWeights weights) {
        if (!StringUtils.hasText(answer)) {
            return 0;
        }
        int markerHits = 0;
        for (String marker : LOGIC_MARKERS) {
            if (answer.contains(marker)) {
                markerHits++;
            }
        }
        int sentenceCount = Math.max(1, answer.split("[。！？；;\\n]").length);
        if (weights.get(DimensionWeights.STRUCTURE) == 0) {
            return 50;
        }
        int base = 40 + Math.min(30, markerHits * 10) + Math.min(30, sentenceCount * 4);
        return clamp(base);
    }

    private int scoreRelevance(String answer, String referencePoints, PointAnalysis pointAnalysis, int chars) {
        if (chars == 0) {
            return 0;
        }
        if (!pointAnalysis.isSufficientEvidence()) {
            return 50;
        }
        int coverage = pointAnalysis.getCoverageRate();
        if (chars < MIN_MEANINGFUL_CHARS) {
            return clamp(coverage / 2);
        }
        return clamp(40 + coverage * 3 / 5);
    }

    private int scoreExpression(String answer, DimensionWeights weights, long expectedSeconds) {
        if (!StringUtils.hasText(answer)) {
            return 0;
        }
        int base = 60;
        String normalized = analyzer.normalize(answer);
        if (normalized.length() >= MIN_MEANINGFUL_CHARS) {
            base += 10;
        }
        if (normalized.length() >= IDEAL_CHARS / 2) {
            base += 10;
        }
        if (normalized.length() > IDEAL_CHARS * 3) {
            base -= 10;
        }
        if (expectedSeconds > 0) {
            long ratio = normalized.length() / expectedSeconds;
            if (ratio >= 1 && ratio <= 4) {
                base += 10;
            } else if (ratio < 0.3) {
                base -= 10;
            }
        }
        if (weights.get(DimensionWeights.EXPRESSION) == 0) {
            base = 50;
        }
        return clamp(base);
    }

    private String buildEvidence(String answer, PointAnalysis pointAnalysis, long expectedSeconds) {
        StringBuilder sb = new StringBuilder();
        sb.append("依据：本次回答文本共 ").append(analyzer.normalize(answer).length()).append(" 字");
        if (expectedSeconds > 0) {
            sb.append("，可观察作答用时约 ").append(expectedSeconds).append(" 秒");
        }
        sb.append("。");
        if (!pointAnalysis.isSufficientEvidence()) {
            sb.append("该题缺少可用参考要点，技术准确性依据不足，仅按表达与结构给出参考评价。");
            return sb.toString();
        }
        sb.append("参考要点匹配 ").append(pointAnalysis.getCovered().size()).append("/")
                .append(pointAnalysis.getCovered().size() + pointAnalysis.getMissing().size())
                .append(" 项");
        if (!pointAnalysis.getMissing().isEmpty()) {
            sb.append("，未覆盖内容见缺失要点。");
        } else {
            sb.append("。");
        }
        sb.append("以上评价不包含语调、情绪或心理状态推断。");
        return sb.toString();
    }

    private String buildSuggestions(PointAnalysis pointAnalysis, int structure, int expression, int relevance) {
        List<String> advice = new ArrayList<>();
        if (!pointAnalysis.isSufficientEvidence()) {
            advice.add("本题参考要点缺失，回答可用，但建议结合课程资料自行核对专业结论。");
        } else if (!pointAnalysis.getMissing().isEmpty()) {
            advice.add("补充缺失要点：" + String.join("、", firstN(pointAnalysis.getMissing(), 3)) + "。");
        }
        if (structure < 60) {
            advice.add("按“结论-依据-细节”的顺序组织回答，并使用首先/其次/最后等连接词明确层次。");
        }
        if (relevance < 60) {
            advice.add("围绕问题直接作答，减少与题目无关的经历铺垫，先给结论再展开。");
        }
        if (expression < 60) {
            advice.add("单题回答建议控制在 60-160 字或 1-2 分钟，先保证关键信息完整再补充细节。");
        }
        if (advice.isEmpty()) {
            advice.add("本题要点覆盖较好，下一步可练习在追问下进一步说明技术选择的原因与取舍。");
        }
        return String.join(" ", advice);
    }

    private List<String> firstN(List<String> source, int n) {
        return source.size() <= n ? source : source.subList(0, n);
    }

    private String join(List<String> items) {
        return items.isEmpty() ? "" : String.join("；", items);
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
