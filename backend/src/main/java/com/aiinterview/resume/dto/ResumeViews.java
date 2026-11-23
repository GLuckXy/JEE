package com.aiinterview.resume.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;

public final class ResumeViews {
    private ResumeViews() {}
    public record Summary(String id,String fileId,String originalName,String parseStatus,String confirmStatus,
            int version,int revision,String failureReason,LocalDateTime createdAt,LocalDateTime confirmedAt) {}
    public record Detail(Summary summary,JsonNode rawParseResult,ResumePayload draft,ResumePayload confirmedPayload,List<String> allowedActions) {}
    public record Version(String id,int version,ResumePayload payload,LocalDateTime confirmedAt) {}
    public record Project(String id,String resumeId,int version,String name,String goal,String responsibility,
            List<String> techStack,String difficulty,String solution,String result,String improvement,int sortNo) {}
    public record SelectedProject(String resumeId,String projectId,int version,Project snapshot) {}
}
