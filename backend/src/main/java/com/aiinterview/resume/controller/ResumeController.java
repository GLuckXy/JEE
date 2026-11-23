package com.aiinterview.resume.controller;

import com.aiinterview.common.ApiResponse;
import com.aiinterview.common.PageResult;
import com.aiinterview.resume.dto.ResumeRequests;
import com.aiinterview.resume.dto.ResumeViews;
import com.aiinterview.resume.service.ResumeService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ResumeController {
    private final ResumeService resumes;
    public ResumeController(ResumeService resumes) { this.resumes=resumes; }
    @PostMapping(value="/api/resumes",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ResumeViews.Detail> upload(@RequestPart("file") MultipartFile file) { return ApiResponse.ok(resumes.upload(file)); }
    @GetMapping("/api/resumes") public ApiResponse<PageResult<ResumeViews.Summary>> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) { return ApiResponse.ok(resumes.list(page,size)); }
    @GetMapping("/api/resumes/{id}") public ApiResponse<ResumeViews.Detail> detail(@PathVariable String id) { return ApiResponse.ok(resumes.detail(id)); }
    @DeleteMapping("/api/resumes/{id}") public ApiResponse<Void> delete(@PathVariable String id,@Valid @RequestBody ResumeRequests.Delete r) { resumes.delete(id,r); return ApiResponse.ok(null); }
    @PutMapping("/api/resumes/{id}/parsed-data") public ApiResponse<ResumeViews.Detail> draft(@PathVariable String id,@Valid @RequestBody ResumeRequests.Draft r) { return ApiResponse.ok(resumes.saveDraft(id,r)); }
    @PostMapping("/api/resumes/{id}/confirm") public ApiResponse<ResumeViews.Detail> confirm(@PathVariable String id,@Valid @RequestBody ResumeRequests.Confirm r) { return ApiResponse.ok(resumes.confirm(id,r)); }
    @PostMapping("/api/resumes/{id}/retry-parse") public ApiResponse<ResumeViews.Detail> retry(@PathVariable String id) { return ApiResponse.ok(resumes.retry(id)); }
    @GetMapping("/api/resumes/{id}/versions") public ApiResponse<List<ResumeViews.Version>> history(@PathVariable String id) { return ApiResponse.ok(resumes.history(id)); }
    @GetMapping("/api/resumes/{id}/projects") public ApiResponse<List<ResumeViews.Project>> projects(@PathVariable String id) { return ApiResponse.ok(resumes.projectList(id)); }
    @GetMapping("/api/resume-projects/{id}") public ApiResponse<ResumeViews.Project> project(@PathVariable String id) { return ApiResponse.ok(resumes.projectDetail(id)); }
    @PostMapping("/api/resumes/{id}/select-project") public ApiResponse<ResumeViews.SelectedProject> select(@PathVariable String id,@Valid @RequestBody ResumeRequests.Selection r) { return ApiResponse.ok(resumes.select(id,r)); }
}
