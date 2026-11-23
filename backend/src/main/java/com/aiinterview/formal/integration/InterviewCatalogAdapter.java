package com.aiinterview.formal.integration;

import com.aiinterview.auth.service.UserAccessService;
import com.aiinterview.common.BusinessException;
import com.aiinterview.content.entity.Job;
import com.aiinterview.content.service.ContentService;
import com.aiinterview.content.service.QuestionQueryService;
import com.aiinterview.formal.service.InterviewCatalog;
import com.aiinterview.report.service.engine.ReferencePointAnalyzer;
import com.aiinterview.resume.service.ResumeQueryService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class InterviewCatalogAdapter implements InterviewCatalog {
    private final ContentService content;
    private final QuestionQueryService questions;
    private final ReferencePointAnalyzer points;
    private final ResumeQueryService resumes;
    private final UserAccessService access;

    public InterviewCatalogAdapter(ContentService content, QuestionQueryService questions, ReferencePointAnalyzer points,
                                   ResumeQueryService resumes, UserAccessService access) {
        this.content = content;
        this.questions = questions;
        this.points = points;
        this.resumes = resumes;
        this.access = access;
    }

    @Override public List<Role> roles(String studentId) {
        requireOwner(studentId);
        return content.activeJobs().stream().map(this::roleView).toList();
    }

    @Override public Optional<Role> role(String studentId, Long roleId) {
        requireOwner(studentId);
        return content.activeJobs().stream().filter(job -> job.getId().equals(roleId)).findFirst().map(this::roleView);
    }

    @Override public List<ProjectOption> confirmedProjects(String studentId) {
        requireOwner(studentId);
        return resumes.listConfirmedProjects().stream().map(project -> new ProjectOption(project.id(), project.name())).toList();
    }

    @Override public Optional<ConfirmedProject> confirmedProject(String studentId, String projectId) {
        requireOwner(studentId);
        try {
            var project = resumes.getConfirmedProject(projectId);
            String summary = String.join("；", Stream.of(project.goal(), project.difficulty(), project.solution(),
                    project.result(), project.improvement()).filter(value -> value != null && !value.isBlank()).toList());
            return Optional.of(new ConfirmedProject(project.id(), project.name(), project.responsibility(), project.techStack(), summary));
        } catch (BusinessException error) {
            if (error.status.value() == 404 || error.status.value() == 409) return Optional.empty();
            throw error;
        }
    }

    @Override public List<TechnicalQuestion> technicalQuestions(Long roleId) {
        return questions.selectForTraining(roleId, null, null, 1, Set.of()).stream().map(question ->
                new TechnicalQuestion(question.id(), question.categoryId(), content.getCategory(question.categoryId(), false).getName(),
                        question.content(), points.splitPoints(question.referencePoints()))).toList();
    }

    private Role roleView(Job job) {
        String focus = job.getInterviewFocus();
        return new Role(job.getId(), job.getName(), focus == null || focus.isBlank() ? List.of() : List.of(focus));
    }

    private void requireOwner(String studentId) {
        if (!access.requireStudent().id().equals(studentId)) throw BusinessException.missing("STUDENT_NOT_FOUND");
    }
}
