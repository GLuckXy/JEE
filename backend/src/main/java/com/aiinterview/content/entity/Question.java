package com.aiinterview.content.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("content_question")
public class Question {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long jobId;
  private Long categoryId;
  private String title;
  private String content;
  private String difficulty;
  private String referencePoints;
  private String sourceNote;
  private Boolean enabled;
  private java.time.LocalDateTime createdAt;
  private java.time.LocalDateTime updatedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public Long getJobId() {
    return jobId;
  }

  public void setJobId(Long value) {
    this.jobId = value;
  }

  public Long getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(Long value) {
    this.categoryId = value;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String value) {
    this.title = value;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String value) {
    this.content = value;
  }

  public String getDifficulty() {
    return difficulty;
  }

  public void setDifficulty(String value) {
    this.difficulty = value;
  }

  public String getReferencePoints() {
    return referencePoints;
  }

  public void setReferencePoints(String value) {
    this.referencePoints = value;
  }

  public String getSourceNote() {
    return sourceNote;
  }

  public void setSourceNote(String value) {
    this.sourceNote = value;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean value) {
    this.enabled = value;
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
