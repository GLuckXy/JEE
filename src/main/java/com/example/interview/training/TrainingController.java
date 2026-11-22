package com.example.interview.training;

import com.example.interview.api.CurrentUser;
import com.example.interview.voice.VoiceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static com.example.interview.training.TrainingApi.*;

@RestController
@RequestMapping("/api/training")
class TrainingController {
    private final CurrentUser currentUser;
    private final QuestionBank bank;
    private final TrainingService training;
    private final EvaluationService evaluation;
    private final VoiceService voice;

    TrainingController(CurrentUser currentUser, QuestionBank bank, TrainingService training,
                       EvaluationService evaluation, VoiceService voice) {
        this.currentUser = currentUser;
        this.bank = bank;
        this.training = training;
        this.evaluation = evaluation;
        this.voice = voice;
    }

    @GetMapping("/roles")
    List<QuestionBank.Role> roles() {
        return bank.roles();
    }

    @GetMapping("/questions")
    List<QuestionView> questions(@RequestParam Long roleId, @RequestParam(required = false) String topic,
                                 @RequestParam(required = false) String type) {
        return bank.questions(roleId).stream().filter(q -> topic == null || topic.equals(q.topic()))
                .filter(q -> type == null || type.equals(q.type())).map(QuestionView::from).toList();
    }

    @GetMapping("/specialties")
    Map<String, Object> specialties(@RequestParam Long roleId, HttpServletRequest request, HttpServletResponse response) {
        var questions = bank.questions(roleId);
        return Map.of("topics", questions.stream().map(QuestionBank.Question::topic).distinct().toList(),
                "types", questions.stream().map(QuestionBank.Question::type).distinct().toList(),
                "recommendations", training.recommendations(owner(request, response), roleId, questions));
    }

    @GetMapping("/sessions")
    List<HistoryItem> history(HttpServletRequest request, HttpServletResponse response) {
        return training.history(owner(request, response));
    }

    @PostMapping("/sessions")
    Progress create(@RequestBody Create input, HttpServletRequest request, HttpServletResponse response) {
        return training.create(owner(request, response), input);
    }

    @GetMapping("/sessions/{id}")
    Progress progress(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) {
        return training.progress(owner(request, response), id);
    }

    @PutMapping("/sessions/{id}/draft")
    Progress draft(@PathVariable String id, @RequestBody Draft input, HttpServletRequest request, HttpServletResponse response) {
        return training.saveDraft(owner(request, response), id, input, false);
    }

    @PostMapping("/sessions/{id}/pause")
    Progress pause(@PathVariable String id, @RequestBody Draft input, HttpServletRequest request, HttpServletResponse response) {
        return training.saveDraft(owner(request, response), id, input, true);
    }

    @PostMapping("/sessions/{id}/{action:resume|redraft|next|end}")
    Progress transition(@PathVariable String id, @PathVariable String action, @RequestBody Cursor input,
                        HttpServletRequest request, HttpServletResponse response) {
        return training.transition(owner(request, response), id, input, action);
    }

    @PostMapping("/sessions/{id}/answers")
    AnswerView submit(@PathVariable String id, @RequestBody Submit input,
                      HttpServletRequest request, HttpServletResponse response) {
        return training.submit(owner(request, response), id, input);
    }

    @PostMapping("/sessions/{id}/transcribe")
    Map<String, String> transcribe(@PathVariable String id, @RequestBody Transcribe input,
                                   HttpServletRequest request, HttpServletResponse response) {
        String owner = owner(request, response);
        String ticket = training.beginAsr(owner, id, input);
        try {
            return Map.of("text", voice.transcribe(owner, input.audioId()));
        } finally {
            training.finishAsr(owner, id, ticket);
        }
    }

    @GetMapping("/answers/{id}")
    AnswerView answer(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) {
        return training.answer(owner(request, response), id);
    }

    @PostMapping("/answers/{id}/evaluation")
    AnswerView evaluate(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) {
        return evaluation.evaluate(owner(request, response), id);
    }

    private String owner(HttpServletRequest request, HttpServletResponse response) {
        return currentUser.requireId(request, response);
    }
}
