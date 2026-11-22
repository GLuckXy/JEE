package cn.edu.interview.content.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** 创建与编辑使用明确的字段白名单，避免把数据库实体直接用作请求。 */
public final class Requests {
  private Requests() {}

  public record JobInput(
      @NotBlank @Size(max = 80) String name,
      @NotBlank @Size(max = 2000) String description,
      @NotBlank @Size(max = 4000) String requirements,
      @NotBlank @Size(max = 4000) String interviewFocus,
      @NotNull Boolean enabled,
      @Min(0) @Max(9999) int sortOrder) {}

  public record CategoryInput(
      @NotNull @Positive Long jobId,
      @NotBlank @Size(max = 80) String name,
      @NotBlank @Size(max = 1000) String description,
      @NotNull Boolean enabled,
      @Min(0) @Max(9999) int sortOrder) {}

  public record QuestionInput(
      @NotNull @Positive Long jobId,
      @NotNull @Positive Long categoryId,
      @NotBlank @Size(max = 200) String title,
      @NotBlank @Size(max = 6000) String content,
      @NotNull Difficulty difficulty,
      @NotBlank @Size(max = 10000) String referencePoints,
      @Size(max = 500) String sourceNote,
      @NotNull Boolean enabled) {}

  public record StageInput(
      @Min(1) @Max(50) int sortOrder,
      @NotBlank @Size(max = 80) String title,
      @NotBlank @Size(max = 3000) String interviewerQuestion,
      @NotBlank @Size(max = 6000) String sampleAnswer,
      @Size(max = 3000) String followUp,
      @NotBlank @Size(max = 4000) String explanation) {}

  public record DemoInput(
      @NotNull @Positive Long jobId,
      @NotBlank @Size(max = 150) String title,
      @NotBlank @Size(max = 2000) String introduction,
      @NotBlank @Size(max = 500) String sourceNote,
      @NotNull @Size(max = 50) List<@NotNull @Valid StageInput> stages) {}

  public record EnabledInput(@NotNull Boolean enabled) {}

  public record StatusInput(@NotNull DemoStatus status) {}
}
