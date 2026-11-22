package com.aiinterview.report;

import com.aiinterview.common.BusinessException;
import com.example.interview.training.TrainingService;
import edu.zjut.interview.formal.InterviewRecords;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReportInputFacade {
    private final TrainingService training;
    private final InterviewRecords interviews;

    public ReportInputFacade(TrainingService training, InterviewRecords interviews) {
        this.training = training;
        this.interviews = interviews;
    }

    public ReportInput requireCompleted(String userId, String sessionId) {
        UUID session = sessionUuid(sessionId);
        return training.completed(userId, session.toString()).orElseGet(() -> interviews.findCompleted(userId, session)
                .map(this::toFormalInput).orElseThrow(() -> BusinessException.missing("COMPLETED_SESSION_NOT_FOUND")));
    }

    public InterviewRecords.Audio formalAudio(String userId, String sessionId, String turnId) {
        UUID session = sessionUuid(sessionId);
        if (interviews.findCompleted(userId, session).isEmpty()) throw BusinessException.missing("COMPLETED_SESSION_NOT_FOUND");
        return interviews.findAudio(userId, session, sessionUuid(turnId))
                .orElseThrow(() -> BusinessException.missing("AUDIO_NOT_FOUND"));
    }

    private ReportInput toFormalInput(InterviewRecords.CompletedInterview session) {
        var answers = new ArrayList<ReportInput.Answer>();
        long elapsed = 0;
        for (var turn : session.answers()) {
            elapsed += turn.usedMillis();
            String status = switch (turn.status()) {
                case TRANSCRIBED -> "ANSWERED";
                case AUDIO_ONLY -> "AUDIO_ONLY";
                case NO_ANSWER -> "NO_ANSWER";
            };
            answers.add(new ReportInput.Answer(turn.turnId().toString(), turn.questionId(), turn.categoryId(), turn.topic(),
                    turn.stage().name(), turn.followUp(), answers.size(), 1, turn.question(), turn.referencePoints(),
                    turn.transcript(), turn.audioAvailable() ? turn.turnId().toString() : null, turn.usedMillis(), turn.timedOut(), status));
        }
        return new ReportInput(session.id().toString(), session.mode(), session.roleId(), session.projectId(), session.createdAt(),
                session.endedAt(), elapsed, session.reason().name(), session.totalQuestions(), answers);
    }

    private UUID sessionUuid(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (id.toString().equals(value)) return id;
        } catch (IllegalArgumentException ignored) {
        }
        throw BusinessException.missing("COMPLETED_SESSION_NOT_FOUND");
    }
}
