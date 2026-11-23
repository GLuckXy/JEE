package com.aiinterview.content.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("content_demo_stage")
public class DemoStage {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long demoId;
  private Integer sortOrder;
  private String title;
  private String interviewerQuestion;
  private String sampleAnswer;
  private String followUp;
  private String explanation;
  private java.time.LocalDateTime createdAt;
  private java.time.LocalDateTime updatedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public Long getDemoId() {
    return demoId;
  }

  public void setDemoId(Long value) {
    this.demoId = value;
  }

  public Integer getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(Integer value) {
    this.sortOrder = value;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String value) {
    this.title = value;
  }

  public String getInterviewerQuestion() {
    return interviewerQuestion;
  }

  public void setInterviewerQuestion(String value) {
    this.interviewerQuestion = value;
  }

  public String getSampleAnswer() {
    return sampleAnswer;
  }

  public void setSampleAnswer(String value) {
    this.sampleAnswer = value;
  }

  public String getFollowUp() {
    return followUp;
  }

  public void setFollowUp(String value) {
    this.followUp = value;
  }

  public String getExplanation() {
    return explanation;
  }

  public void setExplanation(String value) {
    this.explanation = value;
  }

  public java.time.LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(java.time.LocalDateTime value) {
    this.createdAt = value;
  }

  public java.time.LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(java.time.LocalDateTime value) {
    this.updatedAt = value;
  }
}
