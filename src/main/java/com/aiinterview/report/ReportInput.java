package com.aiinterview.report;

import java.util.List;

public record ReportInput(String sessionId, String mode, Long roleId, String projectId, long createdAt, long endedAt,
                          long effectiveMs, String endReason, int questionCount, List<Answer> answers) {
    public record Answer(String id, Long questionId, Long categoryId, String topic, String stage, boolean followUp,
                         int position, int round, String question, List<String> referencePoints, String text,
                         String audioId, long effectiveMs, boolean timedOut, String status) {
        public boolean answered() {
            return "ANSWERED".equals(status) && text != null && !text.isBlank();
        }
    }
}
