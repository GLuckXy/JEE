package com.aiinterview.resume.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record ResumePayload(
        @NotNull @Size(max=30) List<@NotNull @Valid Experience> education,
        @NotNull @Size(max=100) List<@NotBlank @Size(max=128) String> skills,
        @NotNull @Size(max=30) List<@NotNull @Valid Project> projects,
        @NotNull @Size(max=30) List<@NotNull @Valid Experience> internships) {
    public record Experience(@Size(max=256) String organization, @Size(max=256) String title,
            @Size(max=128) String period, @Size(max=5000) String description) {}
    public record Project(@Size(max=256) String name, @Size(max=5000) String goal,
            @Size(max=5000) String responsibility, @NotNull @Size(max=100) List<@NotBlank @Size(max=128) String> techStack,
            @Size(max=5000) String difficulty, @Size(max=5000) String solution,
            @Size(max=5000) String result, @Size(max=5000) String improvement) {}
    public static ResumePayload empty() { return new ResumePayload(List.of(),List.of(),List.of(),List.of()); }
}
