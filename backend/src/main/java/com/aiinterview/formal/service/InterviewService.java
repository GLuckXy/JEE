package com.aiinterview.formal.service;

import com.aiinterview.formal.domain.InterviewProblem;
import com.aiinterview.formal.domain.InterviewRecords;
import com.aiinterview.formal.domain.InterviewSession;
import com.aiinterview.formal.persistence.SqlInterviewStore;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.aiinterview.formal.domain.InterviewRecords.AnswerStatus.NO_ANSWER;
import static com.aiinterview.formal.domain.InterviewRecords.AnswerStatus.TRANSCRIBED;
import static com.aiinterview.formal.domain.InterviewSession.State.*;

public final class InterviewService implements InterviewRecords {
    private final SqlInterviewStore store;
    private final InterviewCatalog catalog;
    private final SpeechTranscriber speech;
    private final ProjectDeepDive projects;
    private final Clock clock;

    public InterviewService(SqlInterviewStore store, InterviewCatalog catalog, SpeechTranscriber speech,
                     ProjectDeepDive projects, Clock clock) {
        this.store = store;
        this.catalog = catalog;
        this.speech = speech;
        this.projects = projects;
        this.clock = clock;
    }

    public Options options(String studentId) {
        return new Options(catalog.roles(studentId), catalog.confirmedProjects(studentId), speech.available(), projects.available());
    }

    public View create(String studentId, Long roleId, String projectId) {
        if (roleId == null || projectId == null || projectId.isBlank()) {
            throw InterviewProblem.badRequest("请选择岗位和已确认项目");
        }
        if (!speech.available()) {
            throw InterviewProblem.speechUnavailable();
        }
        if (!projects.available()) throw InterviewProblem.modelUnavailable();
        var role = catalog.role(studentId, roleId).orElseThrow(() -> InterviewProblem.notFound("岗位不存在"));
        var project = catalog.confirmedProject(studentId, projectId)
                .orElseThrow(() -> InterviewProblem.notFound("当前学生没有该已确认项目"));
        var technical = catalog.technicalQuestions(roleId).stream().findFirst()
                .orElseThrow(() -> InterviewProblem.conflict("该岗位暂无可用技术题"));
        String projectQuestion = projects.firstQuestion(role, project);
        InterviewSession session = new InterviewSession();
        session.id = UUID.randomUUID();
        session.studentId = studentId;
        session.roleId = roleId;
        session.projectId = projectId;
        session.roleSnapshot = role;
        session.projectSnapshot = project;
        session.createdAt = clock.millis();
        session.questions = List.of(
                new InterviewSession.Question(Stage.INTRODUCTION, null, null, null, "请围绕目标岗位做一段简短的自我介绍。",
                        List.of("岗位相关经历", "个人能力", "条理清晰")),
                new InterviewSession.Question(Stage.PROJECT, null, null, null, projectQuestion,
                        List.of("项目目标", "本人职责", "技术选择与结果")),
                new InterviewSession.Question(Stage.TECHNICAL, technical.id(), technical.categoryId(), technical.topic(), technical.text(),
                        technical.referencePoints()));
        session.show(session.questions.get(0), false);
        store.insert(session);
        return view(session);
    }

    public View get(String studentId, UUID id) {
        return store.change(studentId, id, tx -> {
            tx.session().applyDeadlines(clock.millis());
            return view(tx.session());
        });
    }

    public View start(String studentId, UUID id, UUID turnId) {
        return store.change(studentId, id, tx -> {
            var session = tx.session();
            session.applyDeadlines(clock.millis());
            if (!session.current().id.equals(turnId) || session.state == ENDED) {
                throw InterviewProblem.conflict("当前题目已变更，请刷新会话");
            }
            if (session.state == READY) {
                session.start(clock.millis());
            }
            return view(session);
        });
    }

    public View stop(String studentId, UUID id, UUID turnId, boolean finish) {
        return store.change(studentId, id, tx -> {
            var session = tx.session();
            long now = clock.millis();
            session.applyDeadlines(now);
            if (session.current().id.equals(turnId) && session.state != ENDED) {
                if (session.state == RECORDING) {
                    session.stop(now);
                }
                if (finish && session.state == AWAITING_AUDIO) {
                    session.endAfterUpload = EndReason.EARLY_EXIT;
                }
            }
            return view(session);
        });
    }

    public View submit(String studentId, UUID id, UUID turnId, Audio audio) {
        store.change(studentId, id, tx -> {
            var session = tx.session();
            long now = clock.millis();
            session.applyDeadlines(now);
            if (session.turns.stream().anyMatch(turn -> turn.id.equals(turnId) && turn.audioAvailable)) {
                return null;
            }
            if (!session.current().id.equals(turnId) || session.state != AWAITING_AUDIO) {
                throw InterviewProblem.conflict("录音未停止或已超过上传期限");
            }
            tx.saveAudio(turnId, audio);
            session.current().audioAvailable = true;
            session.state = AUDIO_SAVED;
            return null;
        });
        return retry(studentId, id, turnId);
    }

    public View retry(String studentId, UUID id, UUID turnId) {
        var session = store.find(studentId, id)
                .orElseThrow(() -> InterviewProblem.notFound("面试会话不存在"));
        if (isCurrent(session, turnId, AUDIO_SAVED)) {
            Audio audio = store.audio(studentId, id, turnId)
                    .orElseThrow(() -> InterviewProblem.notFound("当前设备上找不到原录音，请在上传录音的设备上重试"));
            String transcript;
            try {
                transcript = speech.transcribe(audio.bytes(), audio.mediaType()).strip();
                if (transcript.length() > 12_000) {
                    throw InterviewProblem.speechUnavailable();
                }
            } catch (RuntimeException e) {
                throw InterviewProblem.speechUnavailable();
            }
            session = store.change(studentId, id, tx -> {
                var current = tx.session();
                if (isCurrent(current, turnId, AUDIO_SAVED)) {
                    var turn = current.current();
                    turn.transcript = transcript;
                    turn.status = transcript.isEmpty() ? NO_ANSWER : TRANSCRIBED;
                    if (turn.question.stage() == Stage.PROJECT && !turn.followUp && !turn.timedOut
                            && turn.status == TRANSCRIBED && current.endAfterUpload == null
                            && current.remainingMillis >= ProjectQuestions.MIN_FOLLOW_UP_MILLIS) {
                        current.state = NEEDS_FOLLOW_UP;
                    } else {
                        current.advance(clock.millis());
                    }
                }
                return current;
            });
        }
        if (isCurrent(session, turnId, NEEDS_FOLLOW_UP)) {
            var turn = session.current();
            Optional<String> question = projects.followUp(session.roleSnapshot, session.projectSnapshot,
                    List.of(new ProjectDeepDive.Exchange(turn.question.text(), turn.transcript)), 0,
                    session.remainingMillis);
            return completeFollowUp(studentId, id, turnId, question);
        }
        return view(session);
    }

    public View completeFollowUp(String studentId, UUID id, UUID turnId, Optional<String> question) {
        return store.change(studentId, id, tx -> {
            var session = tx.session();
            if (isCurrent(session, turnId, NEEDS_FOLLOW_UP)) {
                if (question.isPresent()) {
                    session.show(new InterviewSession.Question(Stage.PROJECT, null, null, null, question.get(),
                            List.of("针对问题说明本人采取的做法与依据")), true);
                } else {
                    session.advance(clock.millis());
                }
            }
            return view(session);
        });
    }

    public View end(String studentId, UUID id, EndReason reason) {
        if (reason != EndReason.EARLY_EXIT && reason != EndReason.INTERRUPTED) {
            throw InterviewProblem.badRequest("结束原因须为 EARLY_EXIT 或 INTERRUPTED");
        }
        return store.change(studentId, id, tx -> {
            var session = tx.session();
            if (session.state != ENDED) {
                session.end(reason, clock.millis());
            }
            return view(session);
        });
    }

    @Override
    public Optional<CompletedInterview> findCompleted(String studentId, UUID sessionId) {
        return store.find(studentId, sessionId).filter(session -> session.state == ENDED)
                .map(session -> new CompletedInterview(session.id, session.studentId, "FORMAL", session.roleId,
                        session.projectId, session.endReason, session.createdAt, session.endedAt,
                        session.questions.size() + (int) session.turns.stream().filter(turn -> turn.followUp).count(),
                        session.turns.stream().map(turn -> new Answer(turn.id, turn.question.stage(), turn.followUp,
                                turn.question.id(), turn.question.categoryId(), turn.question.topic(), turn.question.text(), turn.question.referencePoints(),
                                turn.transcript, turn.usedMillis, turn.timedOut, turn.audioAvailable, turn.status))
                                .toList()));
    }

    @Override
    public Optional<Audio> findAudio(String studentId, UUID sessionId, UUID turnId) {
        return store.audio(studentId, sessionId, turnId);
    }

    private boolean isCurrent(InterviewSession session, UUID turnId, InterviewSession.State state) {
        return session.state == state && session.current().id.equals(turnId);
    }

    private View view(InterviewSession session) {
        var turn = session.current();
        long now = clock.millis();
        long left = session.remainingMillis;
        if (session.state == RECORDING) {
            left -= Math.max(0, Math.min(now, turn.deadlineAt) - turn.startedAt);
        }
        return new View(session.id, session.state, session.roleId, session.projectId, session.questionIndex + 1,
                session.questions.size(), left, now, session.endReason,
                session.state == ENDED ? null : new Prompt(turn.id, turn.question.stage(),
                        session.state == READY ? null : turn.question.text(), turn.followUp, turn.deadlineAt, turn.uploadBy));
    }

    public record Options(List<InterviewCatalog.Role> roles, List<InterviewCatalog.ProjectOption> projects, boolean asrReady, boolean modelReady) {}

    public record Prompt(UUID id, Stage stage, String text, boolean followUp, long deadlineAt, long uploadBy) {}

    public record View(UUID id, InterviewSession.State state, Long roleId, String projectId, int index, int total,
                long remainingMillis, long serverTime, EndReason endReason, Prompt turn) {}
}
