package cn.edu.interview.content.dto;

import java.util.List;

public final class Views {
  private Views() {}

  /** 学生选题信息不包含 referencePoints。 */
  public record QuestionView(
      Long id, Long jobId, Long categoryId, String title, String content, String difficulty) {}

  /** 仅限后端服务调用，禁止作为学生端选题接口返回值。 */
  public record TrainingQuestion(
      Long id,
      Long jobId,
      Long categoryId,
      String title,
      String content,
      String difficulty,
      String referencePoints) {}

  public record DemoSummary(
      Long id, Long jobId, String title, String introduction, String status) {}

  public record StageView(
      Long id,
      int sortOrder,
      String title,
      String interviewerQuestion,
      String sampleAnswer,
      String followUp,
      String explanation) {}

  public record DemoDetail(
      Long id,
      Long jobId,
      String title,
      String introduction,
      String sourceNote,
      String status,
      List<StageView> stages) {}

  public record TrainingOption(
      String type,
      String name,
      String description,
      String route,
      boolean requiresConfirmedResume,
      boolean voiceRequired) {}

  public record GuideStep(int order, String title, String purpose) {}
}
