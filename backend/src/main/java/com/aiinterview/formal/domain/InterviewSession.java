package com.aiinterview.formal.domain;

import com.aiinterview.formal.service.InterviewCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class InterviewSession {
    static final long SESSION_BUDGET = 180_000;
    static final long MAIN_BUDGET = 60_000;
    static final long FOLLOW_UP_BUDGET = 30_000;
    static final long UPLOAD_GRACE = 30_000;

    public enum State { READY, RECORDING, AWAITING_AUDIO, AUDIO_SAVED, NEEDS_FOLLOW_UP, ENDED }

    public record Question(InterviewRecords.Stage stage, Long id, Long categoryId, String topic, String text, List<String> referencePoints) {}

    public static final class Turn {
        public UUID id;
        public Question question;
        public boolean followUp;
        public long startedAt;
        public long deadlineAt;
        public long uploadBy;
        public long usedMillis;
        public boolean timedOut;
        public boolean audioAvailable;
        public String transcript;
        public InterviewRecords.AnswerStatus status;
    }

    public UUID id;
    public String studentId;
    public Long roleId;
    public String projectId;
    public InterviewCatalog.Role roleSnapshot;
    public InterviewCatalog.ConfirmedProject projectSnapshot;
    public long createdAt;
    public long endedAt;
    public State state;
    public InterviewRecords.EndReason endReason;
    public InterviewRecords.EndReason endAfterUpload;
    public List<Question> questions;
    public List<Turn> turns = new ArrayList<>();
    public int questionIndex;
    public long remainingMillis = SESSION_BUDGET;

    public Turn current() {
        return turns.get(turns.size() - 1);
    }

    public void show(Question question, boolean followUp) {
        Turn turn = new Turn();
        turn.id = UUID.randomUUID();
        turn.question = question;
        turn.followUp = followUp;
        turns.add(turn);
        state = State.READY;
    }

    public void start(long now) {
        Turn turn = current();
        turn.startedAt = now;
        long budget = turn.followUp ? FOLLOW_UP_BUDGET : MAIN_BUDGET;
        turn.deadlineAt = now + Math.min(remainingMillis, budget);
        state = State.RECORDING;
    }

    public void stop(long now) {
        Turn turn = current();
        long capturedUntil = Math.min(now, turn.deadlineAt);
        turn.usedMillis = Math.max(0, capturedUntil - turn.startedAt);
        remainingMillis -= turn.usedMillis;
        turn.timedOut = now >= turn.deadlineAt;
        turn.uploadBy = capturedUntil + UPLOAD_GRACE;
        state = State.AWAITING_AUDIO;
    }

    public void applyDeadlines(long now) {
        if (state == State.RECORDING && now >= current().deadlineAt) {
            stop(current().deadlineAt);
        }
        if (state == State.AWAITING_AUDIO && now > current().uploadBy) {
            current().status = InterviewRecords.AnswerStatus.NO_ANSWER;
            advance(now);
        }
    }

    public void advance(long now) {
        if (endAfterUpload != null) {
            end(endAfterUpload, now);
        } else if (remainingMillis == 0) {
            end(InterviewRecords.EndReason.TIME_LIMIT, now);
        } else if (++questionIndex == questions.size()) {
            end(InterviewRecords.EndReason.COMPLETED, now);
        } else {
            show(questions.get(questionIndex), false);
        }
    }

    public void end(InterviewRecords.EndReason reason, long now) {
        Turn turn = current();
        if (state == State.RECORDING) {
            stop(now);
        }
        if (turn.status == null) {
            turn.status = turn.audioAvailable ? InterviewRecords.AnswerStatus.AUDIO_ONLY
                    : InterviewRecords.AnswerStatus.NO_ANSWER;
        }
        state = State.ENDED;
        endReason = reason;
        endedAt = now;
    }
}
