package com.example.interview.training;

import java.util.List;

public interface AnswerEvaluator {
    record Input(String answerId, String mode, QuestionBank.Question question, String answer, long effectiveMs) {}
    record Feedback(String label, List<String> referencePoints, List<String> covered,
                    List<String> missing, String suggestion) {}

    Feedback evaluate(Input input) throws Exception;
}
