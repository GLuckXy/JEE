package com.aicoach.report.service.engine;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class ReferencePointAnalyzer {

    private static final int MIN_POINT_LENGTH = 2;

    private static final Pattern SENTENCE_SPLITTER = Pattern.compile("[。；;\\n\\r]");

    private static final Pattern PUNCTUATION = Pattern.compile("[，,。！？、：:；;（）()\"'“”‘’\\-—]");

    public PointAnalysis analyze(String answerText, String referencePoints) {
        List<String> points = splitPoints(referencePoints);
        List<String> covered = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        if (points.isEmpty()) {
            return PointAnalysis.builder()
                    .covered(covered)
                    .missing(missing)
                    .coverageRate(0)
                    .sufficientEvidence(false)
                    .build();
        }

        String normalizedAnswer = normalize(answerText);
        boolean hasAnswer = StringUtils.hasText(normalizedAnswer);
        for (String point : points) {
            if (hasAnswer && matches(normalizedAnswer, point)) {
                covered.add(point);
            } else {
                missing.add(point);
            }
        }
        int rate = points.isEmpty() ? 0 : (int) Math.round(covered.size() * 100.0 / points.size());
        return PointAnalysis.builder()
                .covered(covered)
                .missing(missing)
                .coverageRate(rate)
                .sufficientEvidence(true)
                .build();
    }

    public List<String> splitPoints(String referencePoints) {
        List<String> points = new ArrayList<>();
        if (!StringUtils.hasText(referencePoints)) {
            return points;
        }
        for (String raw : SENTENCE_SPLITTER.split(referencePoints)) {
            String point = raw.trim();
            if (point.length() <= MIN_POINT_LENGTH) {
                continue;
            }
            point = point.replaceAll("^(要点[一二三四五六七八九十0-9]*|\\d+[.、)]|-[\\s]*)", "").trim();
            if (point.length() > MIN_POINT_LENGTH) {
                points.add(point);
            }
        }
        return points;
    }

    public List<String> splitTags(String tags) {
        List<String> result = new ArrayList<>();
        if (!StringUtils.hasText(tags)) {
            return result;
        }
        for (String raw : tags.split("[,，、;；/\\s]+")) {
            String tag = raw.trim();
            if (!tag.isEmpty()) {
                result.add(tag);
            }
        }
        return result;
    }

    private boolean matches(String normalizedAnswer, String point) {
        String normalizedPoint = normalize(point);
        if (normalizedPoint.isEmpty()) {
            return false;
        }
        if (normalizedAnswer.contains(normalizedPoint)) {
            return true;
        }
        List<String> keywords = keywords(normalizedPoint);
        if (keywords.isEmpty()) {
            return false;
        }
        int hit = 0;
        for (String keyword : keywords) {
            if (normalizedAnswer.contains(keyword)) {
                hit++;
            }
        }
        return hit >= keywordThreshold(keywords.size());
    }

    private List<String> keywords(String normalizedPoint) {
        List<String> keywords = new ArrayList<>();
        for (String chunk : normalizedPoint.split("[,，、/|]+")) {
            if (chunk.length() >= MIN_POINT_LENGTH && chunk.length() <= 12) {
                keywords.add(chunk);
            } else if (chunk.length() > 12) {
                for (int i = 0; i + 4 <= chunk.length(); i += 2) {
                    keywords.add(chunk.substring(i, i + 4));
                }
            }
        }
        return keywords;
    }

    private int keywordThreshold(int keywordCount) {
        if (keywordCount <= 2) {
            return 1;
        }
        return (int) Math.ceil(keywordCount * 0.4);
    }

    public String normalize(String text) {
        if (text == null) {
            return "";
        }
        return PUNCTUATION.matcher(text.toLowerCase()).replaceAll("");
    }
}