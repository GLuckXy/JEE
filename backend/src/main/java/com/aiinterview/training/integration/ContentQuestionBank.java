package com.aiinterview.training.integration;

import com.aiinterview.content.service.ContentService;
import com.aiinterview.content.service.QuestionQueryService;
import com.aiinterview.report.service.engine.ReferencePointAnalyzer;
import com.aiinterview.training.service.QuestionBank;
import java.util.HashMap;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ContentQuestionBank implements QuestionBank {
    private final ContentService content;
    private final QuestionQueryService questions;
    private final ReferencePointAnalyzer points;

    public ContentQuestionBank(ContentService content, QuestionQueryService questions, ReferencePointAnalyzer points) {
        this.content = content;
        this.questions = questions;
        this.points = points;
    }

    @Override public List<Role> roles() {
        return content.activeJobs().stream().map(job -> new Role(job.getId(), job.getName())).toList();
    }

    @Override public List<Question> questions(Long roleId) {
        var categories = new HashMap<Long, String>();
        return questions.listForTraining(roleId).stream().map(question -> new Question(question.id(), question.jobId(),
                question.categoryId(), categories.computeIfAbsent(question.categoryId(), id -> content.getCategory(id, false).getName()),
                question.difficulty(), question.title(), question.content(), null,
                points.splitPoints(question.referencePoints()).stream().map(point -> new Point(point, List.of())).toList())).toList();
    }
}
