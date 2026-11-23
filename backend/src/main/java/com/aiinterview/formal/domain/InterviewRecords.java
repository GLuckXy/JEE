package com.aiinterview.formal.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterviewRecords {
    Optional<CompletedInterview> findCompleted(String studentId, UUID sessionId);

    Optional<Audio> findAudio(String studentId, UUID sessionId, UUID turnId);

    enum EndReason { COMPLETED, TIME_LIMIT, EARLY_EXIT, INTERRUPTED }

    enum AnswerStatus { TRANSCRIBED, NO_ANSWER, AUDIO_ONLY }

    enum Stage { INTRODUCTION, PROJECT, TECHNICAL }

    record CompletedInterview(UUID id, String studentId, String mode, Long roleId, String projectId,
                              EndReason reason, long createdAt, long endedAt, int totalQuestions, List<Answer> answers) {}

    record Answer(UUID turnId, Stage stage, boolean followUp, Long questionId, Long categoryId, String topic, String question,
                  List<String> referencePoints, String transcript, long usedMillis, boolean timedOut,
                  boolean audioAvailable, AnswerStatus status) {}

    record Audio(String mediaType, byte[] bytes) {
        public static final int MAX_BYTES = 8 * 1024 * 1024;
    }
}
