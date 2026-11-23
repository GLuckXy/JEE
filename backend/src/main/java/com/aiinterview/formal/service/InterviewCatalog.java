package com.aiinterview.formal.service;

import java.util.List;
import java.util.Optional;

public interface InterviewCatalog {
    List<Role> roles(String studentId);

    List<ProjectOption> confirmedProjects(String studentId);

    Optional<Role> role(String studentId, Long roleId);

    Optional<ConfirmedProject> confirmedProject(String studentId, String projectId);

    List<TechnicalQuestion> technicalQuestions(Long roleId);

    record Role(Long id, String name, List<String> skills) {}

    record ProjectOption(String id, String name) {}

    // Map only confirmed training fields. Exclude identity, contact and file data.
    record ConfirmedProject(String id, String name, String responsibilities,
                            List<String> technologies, String summary) {}

    record TechnicalQuestion(Long id, Long categoryId, String topic, String text, List<String> referencePoints) {}
}
