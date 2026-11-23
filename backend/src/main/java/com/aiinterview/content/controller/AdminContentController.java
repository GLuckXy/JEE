package com.aiinterview.content.controller;

import com.aiinterview.common.ApiResponse;
import com.aiinterview.content.dto.DemoStatus;
import com.aiinterview.content.dto.Difficulty;
import com.aiinterview.content.dto.Requests.*;
import com.aiinterview.content.service.ContentService;
import com.aiinterview.content.service.TeachingContentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/content")
@PreAuthorize("hasRole('ADMIN')")
public class AdminContentController {
  private final ContentService service;
  private final TeachingContentService teaching;

  public AdminContentController(ContentService service, TeachingContentService teaching) {
    this.service = service;
    this.teaching = teaching;
  }

  @PostMapping("/teaching-content")
  public ApiResponse<TeachingContentService.ImportResult> installTeachingContent() {
    return ApiResponse.ok(teaching.install());
  }

  @GetMapping("/jobs")
  public Object jobs(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String keyword) {
    return ApiResponse.ok(service.listJobs(false, page, size, keyword));
  }

  @GetMapping("/jobs/{id}")
  public Object job(@PathVariable long id) {
    return ApiResponse.ok(service.getJob(id, false));
  }

  @PostMapping("/jobs")
  @ResponseStatus(HttpStatus.CREATED)
  public Object createJob(@Valid @RequestBody JobInput r) {
    return ApiResponse.ok(service.createJob(r));
  }

  @PutMapping("/jobs/{id}")
  public Object updateJob(@PathVariable long id, @Valid @RequestBody JobInput r) {
    return ApiResponse.ok(service.updateJob(id, r));
  }

  @PatchMapping("/jobs/{id}/enabled")
  public Object enableJob(@PathVariable long id, @Valid @RequestBody EnabledInput r) {
    return ApiResponse.ok(service.enableJob(id, r.enabled()));
  }

  @GetMapping("/categories")
  public Object categories(
      @RequestParam(required = false) Long jobId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String keyword) {
    return ApiResponse.ok(service.listCategories(false, jobId, page, size, keyword));
  }

  @GetMapping("/categories/{id}")
  public Object category(@PathVariable long id) {
    return ApiResponse.ok(service.getCategory(id, false));
  }

  @PostMapping("/categories")
  @ResponseStatus(HttpStatus.CREATED)
  public Object createCategory(@Valid @RequestBody CategoryInput r) {
    return ApiResponse.ok(service.createCategory(r));
  }

  @PutMapping("/categories/{id}")
  public Object updateCategory(@PathVariable long id, @Valid @RequestBody CategoryInput r) {
    return ApiResponse.ok(service.updateCategory(id, r));
  }

  @PatchMapping("/categories/{id}/enabled")
  public Object enableCategory(@PathVariable long id, @Valid @RequestBody EnabledInput r) {
    return ApiResponse.ok(service.enableCategory(id, r.enabled()));
  }

  @GetMapping("/questions")
  public Object questions(
      @RequestParam(required = false) Long jobId,
      @RequestParam(required = false) Long categoryId,
      @RequestParam(required = false) Difficulty difficulty,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String keyword) {
    return ApiResponse.ok(
        service.adminQuestions(jobId, categoryId, difficulty, page, size, keyword));
  }

  @GetMapping("/questions/{id}")
  public Object question(@PathVariable long id) {
    return ApiResponse.ok(service.getQuestion(id, false));
  }

  @PostMapping("/questions")
  @ResponseStatus(HttpStatus.CREATED)
  public Object createQuestion(@Valid @RequestBody QuestionInput r) {
    return ApiResponse.ok(service.createQuestion(r));
  }

  @PutMapping("/questions/{id}")
  public Object updateQuestion(@PathVariable long id, @Valid @RequestBody QuestionInput r) {
    return ApiResponse.ok(service.updateQuestion(id, r));
  }

  @PatchMapping("/questions/{id}/enabled")
  public Object enableQuestion(@PathVariable long id, @Valid @RequestBody EnabledInput r) {
    return ApiResponse.ok(service.enableQuestion(id, r.enabled()));
  }

  @GetMapping("/demos")
  public Object demos(
      @RequestParam(required = false) Long jobId,
      @RequestParam(required = false) DemoStatus state,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String keyword) {
    return ApiResponse.ok(service.listDemos(false, jobId, state, page, size, keyword));
  }

  @GetMapping("/demos/{id}")
  public Object demo(@PathVariable long id) {
    return ApiResponse.ok(service.demoDetail(id, false));
  }

  @PostMapping("/demos")
  @ResponseStatus(HttpStatus.CREATED)
  public Object createDemo(@Valid @RequestBody DemoInput r) {
    return ApiResponse.ok(service.createDemo(r));
  }

  @PutMapping("/demos/{id}")
  public Object updateDemo(@PathVariable long id, @Valid @RequestBody DemoInput r) {
    return ApiResponse.ok(service.updateDemo(id, r));
  }

  @PatchMapping("/demos/{id}/status")
  public Object demoStatus(@PathVariable long id, @Valid @RequestBody StatusInput r) {
    return ApiResponse.ok(service.setDemoStatus(id, r.status()));
  }
}
