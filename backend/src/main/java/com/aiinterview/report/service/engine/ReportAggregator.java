package com.aiinterview.report.service.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ReportAggregator {

    private static final int WEAKNESS_THRESHOLD = 60;

    public ReportSummary aggregate(List<AnswerEvaluationSnapshot> evaluations,
                                   DimensionWeights weights,
                                   List<String> knowledgeTags,
                                   Integer questionCount,
                                   Integer answeredCount,
                                   String finishType) {
        ReportSummary summary = new ReportSummary();
        if (evaluations.isEmpty()) {
            summary.setTotalScore(null);
            summary.setDimensionScores("{}");
            summary.setSummary("本次训练没有可评价的回答，未生成分数。可重试生成或重新作答后再查看报告。");
            summary.setWeaknessTags("");
            summary.setSuggestions("建议完成至少一道题的作答后再生成报告。");
            return summary;
        }

        Map<String, List<Integer>> byDimension = new LinkedHashMap<>();
        List<Integer> totals = new ArrayList<>();
        for (AnswerEvaluationSnapshot evaluation : evaluations) {
            totals.add(evaluation.totalScore() == null ? 0 : evaluation.totalScore());
            add(byDimension, DimensionWeights.COMPLETENESS, evaluation.completenessScore());
            add(byDimension, DimensionWeights.ACCURACY, evaluation.accuracyScore());
            add(byDimension, DimensionWeights.STRUCTURE, evaluation.structureScore());
            add(byDimension, DimensionWeights.RELEVANCE, evaluation.relevanceScore());
            add(byDimension, DimensionWeights.EXPRESSION, evaluation.expressionScore());
        }

        summary.setTotalScore((int) Math.round(totals.stream().mapToInt(Integer::intValue).average().orElse(0)));

        Map<String, Integer> dimensionAverages = new LinkedHashMap<>();
        List<String> dimensionParts = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : byDimension.entrySet()) {
            int average = (int) Math.round(entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0));
            dimensionAverages.put(entry.getKey(), average);
            dimensionParts.add("\"" + entry.getKey() + "\":" + average);
        }
        summary.setDimensionScores("{" + String.join(",", dimensionParts) + "}");

        List<String> weaknessTags = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : dimensionAverages.entrySet()) {
            if (entry.getValue() < WEAKNESS_THRESHOLD && weights.get(entry.getKey()) > 0) {
                weaknessTags.add(dimensionLabel(entry.getKey()));
            }
        }
        List<String> distinctTags = knowledgeTags.stream().distinct().toList();
        summary.setWeaknessTags(String.join("；", weaknessTags));
        summary.setSummary(buildSummary(summary.getTotalScore(), dimensionAverages, questionCount, answeredCount,
                finishType, distinctTags));
        summary.setSuggestions(buildSuggestions(weaknessTags, distinctTags, finishType));
        return summary;
    }

    private String buildSummary(int totalScore, Map<String, Integer> dimensionAverages, Integer questionCount,
                                Integer answeredCount, String finishType, List<String> knowledgeTags) {
        StringBuilder sb = new StringBuilder();
        sb.append("整场平均得分 ").append(totalScore).append(" 分。");
        int total = questionCount == null ? (answeredCount == null ? 0 : answeredCount) : questionCount;
        int answered = answeredCount == null ? 0 : answeredCount;
        sb.append("有效作答 ").append(answered).append("/").append(total).append(" 题。");
        if (!knowledgeTags.isEmpty()) {
            sb.append("本次覆盖知识点：").append(String.join("、", knowledgeTags)).append("。");
        }
        int weakest = Integer.MAX_VALUE;
        String weakestDimension = null;
        for (Map.Entry<String, Integer> entry : dimensionAverages.entrySet()) {
            if (entry.getValue() < weakest) {
                weakest = entry.getValue();
                weakestDimension = entry.getKey();
            }
        }
        if (weakestDimension != null && weakest < 100) {
            sb.append("相对薄弱维度为").append(dimensionLabel(weakestDimension)).append("（").append(weakest).append("分）。");
        }
        if ("INTERRUPTED".equals(finishType)) {
            sb.append("本次训练被标记为中断，缺失题目未按正常完成计算，也不作为能力扣分依据。");
        } else if ("EARLY_END".equals(finishType)) {
            sb.append("本次训练提前结束，报告仅基于已作答内容。");
        }
        sb.append("以上结论基于可记录的回答文本与用时，不包含语调或心理状态推断。");
        return sb.toString();
    }

    private String buildSuggestions(List<String> weaknessTags, List<String> knowledgeTags, String finishType) {
        List<String> advice = new ArrayList<>();
        if (!weaknessTags.isEmpty()) {
            advice.add("优先复练以下维度对应的题目：" + String.join("、", weaknessTags) + "。");
        }
        if (!knowledgeTags.isEmpty()) {
            advice.add("可进入专项训练，按「" + String.join("」「", knowledgeTags) + "」逐个复练。");
        }
        if (advice.isEmpty()) {
            advice.add("整体表现稳定，可提高题量或难度继续训练，并在正式模拟中检验完整面试表现。");
        }
        if ("INTERRUPTED".equals(finishType)) {
            advice.add("技术中断的题目建议在设备恢复后重做，不必视为能力不足。");
        }
        return String.join(" ", advice);
    }

    private void add(Map<String, List<Integer>> map, String dimension, Integer score) {
        map.computeIfAbsent(dimension, key -> new ArrayList<>()).add(score == null ? 0 : score);
    }

    private String dimensionLabel(String dimension) {
        return switch (dimension) {
            case DimensionWeights.COMPLETENESS -> "要点完整度";
            case DimensionWeights.ACCURACY -> "技术准确性";
            case DimensionWeights.STRUCTURE -> "表达结构";
            case DimensionWeights.RELEVANCE -> "岗位相关性";
            case DimensionWeights.EXPRESSION -> "表达清晰度";
            default -> dimension;
        };
    }
}
