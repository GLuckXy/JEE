package com.aiinterview.report;

import com.aiinterview.auth.UserAccessService;
import com.aiinterview.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    public record RetrainRequest(@NotBlank String answerId) {}

    private final ReportService reports;
    private final ReportInputFacade inputs;
    private final UserAccessService access;

    public ReportController(ReportService reports, ReportInputFacade inputs, UserAccessService access) {
        this.reports = reports;
        this.inputs = inputs;
        this.access = access;
    }

    @GetMapping({"/history", "/my"})
    public ApiResponse<?> history(@RequestParam(required = false) String mode, @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(reports.history(owner(), mode, page, size));
    }

    @GetMapping("/sessions/{id}")
    public ApiResponse<?> detail(@PathVariable String id) { return ApiResponse.ok(reports.detail(owner(), id)); }

    @PostMapping("/sessions/{id}/generate")
    public ApiResponse<?> generate(@PathVariable String id) { return ApiResponse.ok(reports.generate(owner(), id)); }

    @GetMapping("/evaluations/{id}")
    public ApiResponse<?> evaluation(@PathVariable String id) { return ApiResponse.ok(reports.evaluation(owner(), id)); }

    @PostMapping("/sessions/{id}/retrain")
    public ApiResponse<?> retrain(@PathVariable String id, @Valid @RequestBody RetrainRequest request) {
        return ApiResponse.ok(reports.retrain(owner(), id, request.answerId()));
    }

    @GetMapping("/sessions/{id}/audio/{turnId}")
    public ResponseEntity<byte[]> audio(@PathVariable String id, @PathVariable String turnId) {
        var audio = inputs.formalAudio(owner(), id, turnId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(audio.mediaType())).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff").body(audio.bytes());
    }

    private String owner() { return access.requireStudent().id(); }
}
