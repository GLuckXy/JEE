package com.aiinterview.resume;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class ResumeRequests {
    private ResumeRequests() {}
    public record Draft(@NotNull @Min(0) Integer expectedRevision, @NotNull @Valid ResumePayload payload) {}
    public record Confirm(@NotNull @Min(0) Integer expectedRevision) {}
    public record Delete(@NotNull @Min(0) Integer expectedRevision) {}
    public record Selection(@NotBlank String projectId, @NotNull @Min(1) Integer version) {}
}
