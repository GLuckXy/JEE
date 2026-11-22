package cn.edu.interview.content.controller;

import com.aiinterview.common.ApiResponse;
import cn.edu.interview.content.dto.Difficulty;
import cn.edu.interview.content.service.ContentService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/content")
public class ContentController {
  private final ContentService service;

  public ContentController(ContentService service) {
    this.service = service;
  }

  @GetMapping("/jobs")
  public Object jobs(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String keyword) {
    return ApiResponse.ok(service.listJobs(true, page, size, keyword));
  }

  @GetMapping("/jobs/{id}")
  public Object job(@PathVariable long id) {
    return ApiResponse.ok(service.getJob(id, true));
  }

  @GetMapping("/categories")
  public Object categories(
      @RequestParam(required = false) Long jobId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String keyword) {
    return ApiResponse.ok(service.listCategories(true, jobId, page, size, keyword));
  }

  @GetMapping("/categories/{id}")
  public Object category(@PathVariable long id) {
    return ApiResponse.ok(service.getCategory(id, true));
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
        service.publicQuestions(jobId, categoryId, difficulty, page, size, keyword));
  }

  @GetMapping("/questions/{id}")
  public Object question(@PathVariable long id) {
    return ApiResponse.ok(ContentService.questionView(service.getQuestion(id, true)));
  }

  @GetMapping("/demos")
  public Object demos(
      @RequestParam(required = false) Long jobId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String keyword) {
    return ApiResponse.ok(service.listDemos(true, jobId, null, page, size, keyword));
  }

  @GetMapping("/demos/{id}")
  public Object demo(@PathVariable long id) {
    return ApiResponse.ok(service.demoDetail(id, true));
  }

  @GetMapping("/interview-guide")
  public Object guide() {
    return ApiResponse.ok(service.guide());
  }

  @GetMapping("/training-options")
  public Object training() {
    return ApiResponse.ok(service.trainingOptions());
  }
}
