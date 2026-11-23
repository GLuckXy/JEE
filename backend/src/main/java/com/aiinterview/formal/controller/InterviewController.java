package com.aiinterview.formal.controller;

import com.aiinterview.auth.service.UserAccessService;
import com.aiinterview.formal.domain.InterviewProblem;
import com.aiinterview.formal.domain.InterviewRecords;
import com.aiinterview.formal.service.InterviewService;
import com.aiinterview.formal.service.ModelClient;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/formal-interviews")
final class InterviewController {
    private final InterviewService service;
    private final UserAccessService access;

    InterviewController(InterviewService service, UserAccessService access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping("/options")
    InterviewService.Options options(HttpServletRequest request) {
        return service.options(student(request));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    InterviewService.View create(HttpServletRequest request, @RequestBody StartInterview body) {
        return service.create(student(request), body.roleId(), body.projectId());
    }

    @GetMapping("/{id}")
    InterviewService.View get(HttpServletRequest request, @PathVariable UUID id) {
        return service.get(student(request), id);
    }

    @PostMapping("/{id}/turns/{turnId}/start")
    InterviewService.View start(HttpServletRequest request, @PathVariable UUID id, @PathVariable UUID turnId) {
        return service.start(student(request), id, turnId);
    }

    @PostMapping("/{id}/turns/{turnId}/stop")
    InterviewService.View stop(HttpServletRequest request, @PathVariable UUID id, @PathVariable UUID turnId,
                               @RequestParam(defaultValue = "false") boolean finish) {
        return service.stop(student(request), id, turnId, finish);
    }

    @PostMapping(path = "/{id}/turns/{turnId}/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    InterviewService.View submit(HttpServletRequest request, @PathVariable UUID id, @PathVariable UUID turnId,
                                 @RequestPart("audio") MultipartFile file) throws IOException {
        String owner = student(request);
        if (file.isEmpty() || file.getSize() > InterviewRecords.Audio.MAX_BYTES) {
            throw InterviewProblem.badRequest("请上传 8 MiB 以内的非空录音");
        }
        String type = Optional.ofNullable(file.getContentType()).orElse("").split(";", 2)[0]
                .toLowerCase(Locale.ROOT);
        byte[] bytes = file.getBytes();
        if (!audioSignature(type, bytes)) {
            throw InterviewProblem.badRequest("录音格式须为 WebM、Ogg、MP4 或 WAV");
        }
        return service.submit(owner, id, turnId, new InterviewRecords.Audio(type, bytes));
    }

    @PostMapping("/{id}/turns/{turnId}/retry")
    InterviewService.View retry(HttpServletRequest request, @PathVariable UUID id, @PathVariable UUID turnId) {
        return service.retry(student(request), id, turnId);
    }

    @PostMapping("/{id}/turns/{turnId}/skip-follow-up")
    InterviewService.View skip(HttpServletRequest request, @PathVariable UUID id, @PathVariable UUID turnId) {
        return service.completeFollowUp(student(request), id, turnId, Optional.empty());
    }

    @PostMapping(path = "/{id}/end", consumes = MediaType.APPLICATION_JSON_VALUE)
    InterviewService.View end(HttpServletRequest request, @PathVariable UUID id, @RequestBody FinishInterview body) {
        return service.end(student(request), id, body.reason());
    }

    private String student(HttpServletRequest request) {
        return access.requireStudent().id();
    }

    @ExceptionHandler(ModelClient.Unavailable.class)
    org.springframework.http.ResponseEntity<com.aiinterview.common.ApiResponse<Void>> modelUnavailable() {
        var error = InterviewProblem.modelUnavailable();
        return org.springframework.http.ResponseEntity.status(error.status)
                .body(com.aiinterview.common.ApiResponse.error(error.code, error.getMessage()));
    }

    private boolean audioSignature(String type, byte[] audio) {
        if (audio.length < 12) {
            return false;
        }
        return switch (type) {
            case "audio/webm" -> Arrays.equals(audio, 0, 4, new byte[]{0x1a, 0x45, (byte) 0xdf, (byte) 0xa3}, 0, 4);
            case "audio/ogg" -> "OggS".equals(new String(audio, 0, 4, StandardCharsets.US_ASCII));
            case "audio/mp4" -> "ftyp".equals(new String(audio, 4, 4, StandardCharsets.US_ASCII));
            case "audio/wav", "audio/x-wav" -> "RIFF".equals(new String(audio, 0, 4, StandardCharsets.US_ASCII))
                    && "WAVE".equals(new String(audio, 8, 4, StandardCharsets.US_ASCII));
            default -> false;
        };
    }

    record StartInterview(Long roleId, String projectId) {}

    record FinishInterview(InterviewRecords.EndReason reason) {}
}
