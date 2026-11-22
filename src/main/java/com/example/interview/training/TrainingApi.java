package com.example.interview.training;

import com.example.interview.training.AnswerEvaluator.Feedback;
import com.example.interview.training.QuestionBank.Question;

import java.util.List;

public final class TrainingApi {
    private TrainingApi() {}

    public record Source(String strategy, Long questionId, String answerId) {}
    public record Create(String mode, Long roleId, List<Long> questionIds, String topic,
                         String type, Source source) {}
    public record Cursor(Integer position, Integer roundNumber) {}
    public record Draft(Integer position, Integer roundNumber, String text, String audioId) {}
    public record Submit(Integer position, Integer roundNumber, String text, String audioId, String requestKey) {}
    public record Transcribe(Integer position, Integer roundNumber, String audioId) {}
    public record QuestionView(Long id, Long categoryId, String title, String topic, String type, String prompt) {
        static QuestionView from(Question question) {
            return new QuestionView(question.id(), question.categoryId(), question.title(), question.topic(), question.type(), question.prompt());
        }
    }
    public record AnswerView(String id, String sessionId, Long questionId, String questionTitle, String prompt,
                             int position, int roundNumber, String text, String audioId, long effectiveMs,
                             String evaluationState, Feedback feedback, String failureReason, long createdAt) {}
    public record Progress(String id, String mode, Long roleId, String state, String phase, int position,
                           int roundNumber, int total, QuestionView question, long effectiveMs, long pausedMs,
                           long asrMs, long evaluationMs, boolean clockRunning, String draft, String draftAudioId,
                           Source source, AnswerView sourceAnswer, List<AnswerView> answers, long createdAt, long endedAt) {}
    public record HistoryItem(String id, String mode, Long roleId, String state, int total, long effectiveMs,
                              long createdAt, long endedAt) {}
}
