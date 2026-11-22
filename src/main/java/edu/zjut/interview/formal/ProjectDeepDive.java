package edu.zjut.interview.formal;

import java.util.List;
import java.util.Optional;

public interface ProjectDeepDive {
    boolean available();
    String firstQuestion(InterviewCatalog.Role role, InterviewCatalog.ConfirmedProject project);

    Optional<String> followUp(InterviewCatalog.Role role, InterviewCatalog.ConfirmedProject project,
                              List<Exchange> history, int followUpsUsed, long remainingMillis);

    record Exchange(String question, String transcript) {}
}
