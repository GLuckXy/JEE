package com.example.interview.training;

import com.aiinterview.report.ReportInput;
import com.aiinterview.report.SessionCompleted;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.interview.voice.VoiceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import org.springframework.context.ApplicationEventPublisher;

import static com.example.interview.api.ApiErrors.*;
import static com.example.interview.training.TrainingApi.*;
import static com.example.interview.training.TrainingRows.*;
import static com.example.interview.training.QuestionBank.Question;

@Service
public class TrainingService {
    private final SessionMapper sessions;
    private final AnswerMapper answers;
    private final QuestionBank bank;
    private final QuestionSelection selection;
    private final ObjectMapper json;
    private final VoiceService voice;
    private final long evaluationTimeout;
    private final ApplicationEventPublisher events;

    TrainingService(SessionMapper sessions, AnswerMapper answers, QuestionBank bank, QuestionSelection selection,
                    ObjectMapper json, VoiceService voice,
                    @Value("${training.evaluation-timeout-ms}") long evaluationTimeout, ApplicationEventPublisher events) {
        this.sessions = sessions;
        this.answers = answers;
        this.bank = bank;
        this.selection = selection;
        this.json = json;
        this.voice = voice;
        this.evaluationTimeout = Math.max(1, evaluationTimeout);
        this.events = events;
    }

    @Transactional
    public Progress create(String owner, Create input) {
        var source = input.source();
        Question origin = null;
        if (source != null && source.answerId() != null) {
            var prior = ownedAnswer(owner, source.answerId());
            origin = questions(prior.session()).get(prior.answer().position);
            if (source.questionId() != null && !origin.id().equals(source.questionId())) {
                throw invalid("来源题目与来源回答不匹配");
            }
        } else if (source != null) {
            origin = bank.questions(input.roleId()).stream().filter(q -> q.id().equals(source.questionId()))
                    .findFirst().orElseThrow(() -> missing("找不到来源题目"));
        }
        var chosen = selection.select(input, origin);
        var session = new Session();
        session.id = id();
        session.ownerId = owner;
        session.mode = input.mode();
        session.roleId = input.roleId();
        session.questionSnapshot = encode(chosen);
        session.sourceQuestionId = origin == null ? null : origin.id();
        session.sourceAnswerId = source == null ? null : source.answerId();
        session.sourceStrategy = source == null ? null : source.strategy();
        session.state = "ACTIVE";
        session.phase = "ANSWERING";
        session.roundNumber = 1;
        session.draft = "";
        session.createdAt = now();
        sessions.insert(session);
        return view(session);
    }

    @Transactional
    public Progress progress(String owner, String id) {
        return view(lock(owner, id));
    }

    public List<HistoryItem> history(String owner) {
        return sessions.selectList(Wrappers.<Session>query().eq("owner_id", owner).orderByDesc("created_at")
                        .last("LIMIT 50")).stream()
                .map(s -> new HistoryItem(s.id, s.mode, s.roleId, s.state, questions(s).size(),
                        s.effectiveMs + TrainingClock.interval(s, now()), s.createdAt, s.endedAt)).toList();
    }

    public Optional<ReportInput> completed(String owner, String id) {
        var session = sessions.selectOne(Wrappers.<Session>query().eq("id", id).eq("owner_id", owner).eq("state", "ENDED"));
        if (session == null) return Optional.empty();
        var snapshot = questions(session);
        var rows = answers.selectList(Wrappers.<Answer>query().eq("session_id", id).orderByAsc("position", "round_number"));
        var items = new ArrayList<ReportInput.Answer>();
        for (int position = 0; position < snapshot.size(); position++) {
            Question question = snapshot.get(position);
            int questionPosition = position;
            var attempts = rows.stream().filter(row -> row.position == questionPosition).toList();
            var points = question.points().stream().map(QuestionBank.Point::text).toList();
            if (attempts.isEmpty()) {
                items.add(new ReportInput.Answer(null, question.id(), question.categoryId(), question.topic(), "TECHNICAL",
                        false, position, 0, question.prompt(), points, null, null, 0, false, "NO_ANSWER"));
            }
            for (var attempt : attempts) {
                items.add(new ReportInput.Answer(attempt.id, question.id(), question.categoryId(), question.topic(), "TECHNICAL",
                        false, position, attempt.roundNumber, question.prompt(), points, attempt.answerText, attempt.audioId,
                        attempt.effectiveMs, false, attempt.answerText.isBlank() ? "AUDIO_ONLY" : "ANSWERED"));
            }
        }
        long answered = rows.stream().filter(row -> !row.answerText.isBlank()).map(row -> row.position).distinct().count();
        return Optional.of(new ReportInput(id, session.mode, session.roleId, null, session.createdAt, session.endedAt,
                session.effectiveMs, answered == snapshot.size() ? "COMPLETED" : "EARLY_EXIT", snapshot.size(), items));
    }

    @Transactional
    public Progress retrainFromReport(String owner, Long roleId, Long categoryId, Long sourceQuestionId) {
        var similar = bank.questions(roleId).stream().filter(question -> question.categoryId().equals(categoryId)
                && !question.id().equals(sourceQuestionId)).limit(5).toList();
        if (similar.isEmpty()) throw invalid("该类别暂无可供复练的其他启用题目");
        return create(owner, new Create("SPECIAL", roleId, similar.stream().map(Question::id).toList(), similar.getFirst().topic(), null, null));
    }

    List<QuestionBank.Recommendation> recommendations(String owner, Long roleId, List<Question> available) {
        if (available.isEmpty()) return List.of();
        var previous = sessions.selectList(Wrappers.<Session>query().eq("owner_id", owner).eq("role_id", roleId)
                .orderByDesc("created_at").last("LIMIT 20"));
        if (previous.isEmpty()) return List.of();
        var recent = answers.selectList(Wrappers.<Answer>query().in("session_id", previous.stream().map(session -> session.id).toList())
                .isNotNull("feedback_json").orderByDesc("created_at").last("LIMIT 60"));
        var questionsBySession = new HashMap<String, List<Question>>();
        var recommended = new LinkedHashMap<Long, QuestionBank.Recommendation>();
        for (var answer : recent) {
            var feedback = decode(answer.feedbackJson, new TypeReference<AnswerEvaluator.Feedback>() {});
            if (feedback.missing().isEmpty()) continue;
            var snapshot = questionsBySession.computeIfAbsent(answer.sessionId, id -> previous.stream()
                    .filter(session -> id.equals(session.id)).findFirst().map(this::questions).orElseThrow());
            var source = snapshot.get(answer.position);
            available.stream().filter(question -> question.categoryId().equals(source.categoryId()) && !question.id().equals(source.id()))
                    .findFirst().ifPresent(question -> recommended.putIfAbsent(question.categoryId(),
                            new QuestionBank.Recommendation(question.topic(), "近期作答有未覆盖的要点", question.id())));
            if (recommended.size() >= 5) break;
        }
        return List.copyOf(recommended.values());
    }

    @Transactional
    public Progress saveDraft(String owner, String id, Draft input, boolean pause) {
        var session = lock(owner, id);
        checkCursor(session, input.position(), input.roundNumber());
        if ("ENDED".equals(session.state)) throw conflict("训练已经结束");
        long now = now();
        if ("ANSWERING".equals(session.phase)) {
            session.draft = text(input.text());
            voice.requireOwned(owner, input.audioId());
            session.draftAudioId = input.audioId();
            if (!pause && "ACTIVE".equals(session.state) && session.asrTicket == null) {
                TrainingClock.heartbeat(session, now);
            }
        }
        if (pause && "ACTIVE".equals(session.state)) {
            TrainingClock.stop(session, now);
            session.state = "PAUSED";
            session.pausedAt = now;
        }
        sessions.updateById(session);
        return view(session);
    }

    @Transactional
    public Progress transition(String owner, String id, Cursor input, String action) {
        var s = lock(owner, id);
        checkCursor(s, input.position(), input.roundNumber());
        long now = now();
        if ("ENDED".equals(s.state)) {
            if ("end".equals(action)) return view(s);
            throw conflict("训练已经结束");
        }
        switch (action) {
            case "resume" -> {
                closePause(s, now);
                s.state = "ACTIVE";
                if ("ANSWERING".equals(s.phase) && s.asrTicket == null) TrainingClock.heartbeat(s, now);
            }
            case "redraft", "next" -> {
                if (!"ACTIVE".equals(s.state) || !"REVIEW".equals(s.phase)) {
                    throw conflict("请先继续训练并提交本题");
                }
                if ("next".equals(action)) {
                    if (s.position + 1 >= questions(s).size()) throw conflict("已经是最后一题，请结束训练");
                    s.position++;
                    s.roundNumber = 1;
                } else {
                    s.roundNumber++;
                }
                s.phase = "ANSWERING";
                s.roundMs = 0;
                s.draft = "";
                s.draftAudioId = null;
                TrainingClock.heartbeat(s, now);
            }
            case "end" -> {
                TrainingClock.stop(s, now);
                TrainingClock.closeAsr(s, now);
                closePause(s, now);
                s.state = "ENDED";
                s.endedAt = now;
            }
            default -> throw invalid("未知的会话操作");
        }
        sessions.updateById(s);
        if ("end".equals(action)) events.publishEvent(new SessionCompleted(owner, id));
        return view(s);
    }

    @Transactional
    public AnswerView submit(String owner, String id, Submit input) {
        var s = lock(owner, id);
        String answer = text(input.text()).strip();
        if (answer.isEmpty()) throw invalid("请填写回答，或确认语音转写后的文字");
        if (!isUuid(input.requestKey())) throw invalid("请提供有效的提交标识");
        var prior = answers.selectOne(Wrappers.<Answer>query().eq("session_id", id).eq("request_key", input.requestKey()));
        if (prior != null) {
            if (!Objects.equals(input.position(), prior.position) || !Objects.equals(input.roundNumber(), prior.roundNumber)
                    || !prior.answerText.equals(answer) || !Objects.equals(prior.audioId, input.audioId())) {
                throw conflict("该提交标识已用于另一份回答");
            }
            return answerView(s, prior);
        }
        checkCursor(s, input.position(), input.roundNumber());
        requireAnswering(s);
        if (s.asrTicket != null) throw conflict("语音识别正在进行，请稍后提交");
        voice.requireOwned(owner, input.audioId());
        long now = now();
        TrainingClock.stop(s, now);
        var row = new Answer();
        row.id = id();
        row.sessionId = id;
        row.questionId = questions(s).get(s.position).id();
        row.position = s.position;
        row.roundNumber = s.roundNumber;
        row.requestKey = input.requestKey();
        row.answerText = answer;
        row.audioId = input.audioId();
        row.effectiveMs = s.roundMs;
        row.evaluationState = "PENDING";
        row.createdAt = now;
        answers.insert(row);
        s.phase = "REVIEW";
        s.draft = "";
        s.draftAudioId = null;
        sessions.updateById(s);
        return answerView(s, row);
    }

    @Transactional
    public String beginAsr(String owner, String id, Transcribe input) {
        var s = lock(owner, id);
        checkCursor(s, input.position(), input.roundNumber());
        requireAnswering(s);
        if (s.asrTicket != null) throw conflict("已有语音识别正在进行");
        if (input.audioId() == null) throw invalid("请先上传录音");
        voice.requireOwned(owner, input.audioId());
        long now = now();
        TrainingClock.stop(s, now);
        s.draftAudioId = input.audioId();
        s.asrTicket = id();
        s.asrAt = now;
        sessions.updateById(s);
        return s.asrTicket;
    }

    @Transactional
    public void finishAsr(String owner, String id, String ticket) {
        var s = lock(owner, id);
        if (!ticket.equals(s.asrTicket)) return;
        TrainingClock.closeAsr(s, now());
        sessions.updateById(s);
    }

    record EvaluationAttempt(AnswerView answer, String ticket, AnswerEvaluator.Input input) {}

    @Transactional
    public EvaluationAttempt beginEvaluation(String owner, String answerId) {
        var owned = ownedAnswer(owner, answerId);
        var s = lock(owner, owned.session().id);
        var a = answers.lock(answerId);
        long now = now();
        if ("RUNNING".equals(a.evaluationState) && now - a.evaluationStartedAt >= evaluationTimeout + 2000) {
            s.evaluationMs += evaluationTimeout;
            sessions.updateById(s);
        } else if ("RUNNING".equals(a.evaluationState) || "READY".equals(a.evaluationState)) {
            return new EvaluationAttempt(answerView(s, a), null, null);
        }
        a.evaluationState = "RUNNING";
        a.evaluationTicket = id();
        a.evaluationStartedAt = now;
        a.failureReason = null;
        answers.updateById(a);
        return new EvaluationAttempt(answerView(s, a), a.evaluationTicket,
                new AnswerEvaluator.Input(a.id, s.mode, questions(s).get(a.position), a.answerText, a.effectiveMs));
    }

    @Transactional
    public AnswerView finishEvaluation(String owner, String answerId, String ticket,
                                       AnswerEvaluator.Feedback feedback, String failure) {
        var owned = ownedAnswer(owner, answerId);
        var s = lock(owner, owned.session().id);
        var a = answers.lock(answerId);
        if (!ticket.equals(a.evaluationTicket)) return answerView(s, a);
        s.evaluationMs += Math.min(evaluationTimeout, Math.max(0, now() - a.evaluationStartedAt));
        sessions.updateById(s);
        a.evaluationTicket = null;
        a.evaluationState = feedback == null ? "FAILED" : "READY";
        a.feedbackJson = feedback == null ? null : encode(feedback);
        a.failureReason = failure;
        answers.updateById(a);
        return answerView(s, a);
    }

    long evaluationTimeout() {
        return evaluationTimeout;
    }

    AnswerView answer(String owner, String answerId) {
        var owned = ownedAnswer(owner, answerId);
        return answerView(owned.session(), owned.answer());
    }

    private Session lock(String owner, String id) {
        var session = sessions.lock(id, owner);
        if (session == null) throw missing("找不到该训练会话");
        long now = now();
        if (session.asrTicket != null && now - session.asrAt >= TrainingClock.ASR_LIMIT_MS) {
            TrainingClock.closeAsr(session, now);
            sessions.updateById(session);
        }
        return session;
    }

    private record OwnedAnswer(Session session, Answer answer) {}

    private OwnedAnswer ownedAnswer(String owner, String id) {
        var a = answers.selectById(id);
        var s = a == null ? null : sessions.selectById(a.sessionId);
        if (s == null || !owner.equals(s.ownerId)) throw missing("找不到该回答");
        return new OwnedAnswer(s, a);
    }

    private Progress view(Session s) {
        var questions = questions(s);
        long now = now();
        var rows = answers.selectList(Wrappers.<Answer>query().eq("session_id", s.id).orderByAsc("position", "round_number"));
        var source = s.sourceStrategy == null ? null : new Source(s.sourceStrategy, s.sourceQuestionId, s.sourceAnswerId);
        return new Progress(s.id, s.mode, s.roleId, s.state, s.phase, s.position, s.roundNumber, questions.size(),
                QuestionView.from(questions.get(s.position)), s.effectiveMs + TrainingClock.interval(s, now),
                s.pausedMs + (s.pausedAt == 0 ? 0 : Math.max(0, now - s.pausedAt)),
                s.asrMs + (s.asrAt == 0 ? 0 : Math.max(0, now - s.asrAt)), s.evaluationMs,
                TrainingClock.running(s, now), s.draft, s.draftAudioId, source,
                s.sourceAnswerId == null ? null : answer(s.ownerId, s.sourceAnswerId),
                rows.stream().map(a -> answerView(questions.get(a.position), a)).toList(), s.createdAt, s.endedAt);
    }

    private AnswerView answerView(Session s, Answer a) {
        return answerView(questions(s).get(a.position), a);
    }

    private AnswerView answerView(Question q, Answer a) {
        var feedback = a.feedbackJson == null ? null : decode(a.feedbackJson, new TypeReference<AnswerEvaluator.Feedback>() {});
        boolean expired = "RUNNING".equals(a.evaluationState) && now() - a.evaluationStartedAt >= evaluationTimeout + 2000;
        return new AnswerView(a.id, a.sessionId, a.questionId, q.title(), q.prompt(), a.position, a.roundNumber,
                a.answerText, a.audioId, a.effectiveMs, expired ? "FAILED" : a.evaluationState, feedback,
                expired ? "上次评价已超时，可按原回答重试" : a.failureReason, a.createdAt);
    }

    private List<Question> questions(Session s) {
        return decode(s.questionSnapshot, new TypeReference<List<Question>>() {});
    }

    private <T> T decode(String value, TypeReference<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Invalid persisted training data", error);
        }
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not serialize training data", error);
        }
    }

    private void requireAnswering(Session s) {
        if (!"ACTIVE".equals(s.state) || !"ANSWERING".equals(s.phase)) {
            throw conflict("当前轮次无法作答，请刷新进度");
        }
    }

    private void checkCursor(Session s, Integer position, Integer round) {
        if (!Objects.equals(position, s.position) || !Objects.equals(round, s.roundNumber)) {
            throw conflict("当前题目或作答轮次已变更，请刷新进度");
        }
    }

    private void closePause(Session s, long now) {
        if (s.pausedAt == 0) return;
        s.pausedMs += Math.max(0, now - s.pausedAt);
        s.pausedAt = 0;
    }

    private String text(String text) {
        String value = Objects.toString(text, "");
        if (value.length() > 8000) throw invalid("回答最多 8000 字符");
        return value;
    }

    private boolean isUuid(String value) {
        return value != null && value.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    }

    private String id() { return UUID.randomUUID().toString(); }
    private long now() { return System.currentTimeMillis(); }
}
