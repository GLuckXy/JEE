package com.aiinterview.report.service.engine;

import com.aiinterview.common.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class DimensionWeightParser {

    private static final DimensionWeights DEFAULT = new DimensionWeights(30, 30, 15, 15, 10);

    private static final List<String> SUPPORTED = List.of(
            DimensionWeights.COMPLETENESS,
            DimensionWeights.ACCURACY,
            DimensionWeights.STRUCTURE,
            DimensionWeights.RELEVANCE,
            DimensionWeights.EXPRESSION);

    private final ObjectMapper objectMapper;

    public DimensionWeights defaultWeights() {
        return DEFAULT;
    }

    public DimensionWeights parse(String json) {
        if (!StringUtils.hasText(json)) {
            return DEFAULT;
        }
        try {
            Map<String, Integer> raw = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Integer>>() {
            });
            return normalize(raw);
        } catch (Exception e) {
            log.warn("评价维度权重解析失败，使用默认权重: {}", e.getMessage());
            return DEFAULT;
        }
    }

    public DimensionWeights normalize(Map<String, Integer> raw) {
        Map<String, Integer> cleaned = new LinkedHashMap<>();
        for (String dimension : SUPPORTED) {
            Integer value = raw.get(dimension);
            if (value != null && value < 0) {
                throw BusinessException.bad("INVALID_RULE", "维度权重不能为负数: " + dimension);
            }
            cleaned.put(dimension, value == null ? 0 : value);
        }
        DimensionWeights weights = fromMap(cleaned);
        if (weights.total() == 0) {
            throw BusinessException.bad("INVALID_RULE", "维度权重之和必须大于 0");
        }
        return weights;
    }

    public String validateAndSerialize(Map<String, Integer> raw) {
        for (String key : raw.keySet()) {
            if (!SUPPORTED.contains(key)) {
                throw BusinessException.bad("INVALID_RULE", "不支持的评分维度: " + key);
            }
        }
        DimensionWeights weights = normalize(raw);
        return format(weights);
    }

    public String format(DimensionWeights weights) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String dimension : SUPPORTED) {
            map.put(dimension, weights.get(dimension));
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("维度权重序列化失败", e);
        }
    }

    private DimensionWeights fromMap(Map<String, Integer> map) {
        return new DimensionWeights(
                map.get(DimensionWeights.COMPLETENESS),
                map.get(DimensionWeights.ACCURACY),
                map.get(DimensionWeights.STRUCTURE),
                map.get(DimensionWeights.RELEVANCE),
                map.get(DimensionWeights.EXPRESSION));
    }
}
