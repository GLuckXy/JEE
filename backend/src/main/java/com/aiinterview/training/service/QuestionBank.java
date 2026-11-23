package com.aiinterview.training.service;

import java.util.List;

public interface QuestionBank {
    record Role(Long id, String name) {}
    record Point(String text, List<String> keywords) {}
    record Question(Long id, Long roleId, Long categoryId, String topic, String type, String title, String prompt,
                    String explanation, List<Point> points) {}
    record Recommendation(String topic, String reason, Long questionId) {}

    List<Role> roles();
    List<Question> questions(Long roleId);
}
