package com.aicoach.report.service.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AiEvaluationClient {

    private final ObjectMapper objectMapper;

    private final boolean enabled;

    private final String baseUrl;

    private final String apiKey;

    private final String model;

    private final Duration timeout;

    public AiEvaluationClient(ObjectMapper objectMapper,
                              @Value("${app.evaluation.ai.enabled:false}") boolean enabled,
                              @Value("${app.evaluation.ai.base-url:}") String baseUrl,
                              @Value("${app.evaluation.ai.api-key:}") String apiKey,
                              @Value("${app.evaluation.ai.model:}") String model,
                              @Value("${app.evaluation.ai.timeout-seconds:20}") long timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    public boolean isAvailable() {
        return enabled && StringUtils.hasText(apiKey);
    }

    public EvaluationResult evaluate(String questionText, String referencePoints, String answerText,
                                     DimensionWeights weights) {
        if (!isAvailable()) {
            return null;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("temperature", 0.2);
            payload.put("response_format", Map.of("type", "json_object"));

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt()));
            messages.add(Map.of("role", "user", "content", userPrompt(questionText, referencePoints, answerText, weights)));
            payload.put("messages", messages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.replaceAll("/$", "") + "/chat/completions"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(timeout)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("AI 评价服务返回状态码 {}，降级为规则评价", response.statusCode());
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            return parse(content, weights);
        } catch (Exception e) {
            log.warn("AI 评价调用失败，降级为规则评价: {}", e.getMessage());
            return null;
        }
    }

    public EvaluationResult parse(String content, DimensionWeights weights) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(stripCodeFence(content));
            int completeness = clamp(node.path("completenessScore").asInt(-1));
            int accuracy = clamp(node.path("accuracyScore").asInt(-1));
            int structure = clamp(node.path("structureScore").asInt(-1));
            int relevance = clamp(node.path("relevanceScore").asInt(-1));
            int expression = clamp(node.path("expressionScore").asInt(-1));
            if (completeness < 0 || accuracy < 0 || structure < 0 || relevance < 0 || expression < 0) {
                log.warn("AI 评价缺少必要维度字段，降级为规则评价");
                return null;
            }
            int total = weights.total() <= 0 ? 0 : (int) Math.round((completeness * weights.get(DimensionWeights.COMPLETENESS)
                    + accuracy * weights.get(DimensionWeights.ACCURACY)
                    + structure * weights.get(DimensionWeights.STRUCTURE)
                    + relevance * weights.get(DimensionWeights.RELEVANCE)
                    + expression * weights.get(DimensionWeights.EXPRESSION)) / weights.total());
            return EvaluationResult.builder()
                    .completenessScore(completeness)
                    .accuracyScore(accuracy)
                    .structureScore(structure)
                    .relevanceScore(relevance)
                    .expressionScore(expression)
                    .totalScore(total)
                    .coveredPoints(node.path("coveredPoints").asText(""))
                    .missingPoints(node.path("missingPoints").asText(""))
                    .evidence(node.path("evidence").asText(""))
                    .suggestions(node.path("suggestions").asText(""))
                    .source("AI")
                    .build();
        } catch (Exception e) {
            log.warn("AI 评价返回内容不符合约定格式，降级为规则评价: {}", e.getMessage());
            return null;
        }
    }

    private String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstBreak = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstBreak > 0 && lastFence > firstBreak) {
                return trimmed.substring(firstBreak + 1, lastFence);
            }
        }
        return trimmed;
    }

    private int clamp(int value) {
        if (value < 0) {
            return -1;
        }
        return Math.min(100, value);
    }

    private String systemPrompt() {
        return "你是模拟面试平台的回答评价助手。只依据给定题目、参考要点和学生回答进行分析，"
                + "不得编造学生未提供的项目经历，不得推断语调或心理状态。必须输出 JSON 对象，字段为："
                + "completenessScore, accuracyScore, structureScore, relevanceScore, expressionScore（0-100 整数），"
                + "coveredPoints, missingPoints（字符串），evidence（评价依据），suggestions（改进建议）。";
    }

    private String userPrompt(String questionText, String referencePoints, String answerText, DimensionWeights weights) {
        return "题目：" + questionText
                + "\n参考要点：" + (StringUtils.hasText(referencePoints) ? referencePoints : "（无）")
                + "\n学生回答：" + (StringUtils.hasText(answerText) ? answerText : "（未作答）")
                + "\n维度权重：" + objectMapperValue(weights);
    }

    private String objectMapperValue(DimensionWeights weights) {
        return "完整度" + weights.get(DimensionWeights.COMPLETENESS) + "/准确性" + weights.get(DimensionWeights.ACCURACY)
                + "/结构" + weights.get(DimensionWeights.STRUCTURE) + "/相关性" + weights.get(DimensionWeights.RELEVANCE)
                + "/表达" + weights.get(DimensionWeights.EXPRESSION);
    }
}