package com.aicoach.report.service.engine;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class DimensionWeights {

    public static final String COMPLETENESS = "completeness";
    public static final String ACCURACY = "accuracy";
    public static final String STRUCTURE = "structure";
    public static final String RELEVANCE = "relevance";
    public static final String EXPRESSION = "expression";

    private final Map<String, Integer> weights = new LinkedHashMap<>();

    public DimensionWeights(int completeness, int accuracy, int structure, int relevance, int expression) {
        weights.put(COMPLETENESS, completeness);
        weights.put(ACCURACY, accuracy);
        weights.put(STRUCTURE, structure);
        weights.put(RELEVANCE, relevance);
        weights.put(EXPRESSION, expression);
    }

    public int get(String dimension) {
        return weights.getOrDefault(dimension, 0);
    }

    public int total() {
        return weights.values().stream().mapToInt(Integer::intValue).sum();
    }
}