package com.aiinterview.content.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("content_job")
public class Job {
  @TableId(type = IdType.AUTO)
  private Long id;

  private String name;
  private String description;
  private String requirements;
  private String interviewFocus;
  private Boolean enabled;
  private Integer sortOrder;
  private java.time.LocalDateTime createdAt;
  private java.time.LocalDateTime updatedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public String getName() {
    return name;
  }

  public void setName(String value) {
    this.name = value;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String value) {
    this.description = value;
  }

  public String getRequirements() {
    return requirements;
  }

  public void setRequirements(String value) {
    this.requirements = value;
  }

  public String getInterviewFocus() {
    return interviewFocus;
  }

  public void setInterviewFocus(String value) {
    this.interviewFocus = value;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean value) {
    this.enabled = value;
  }

  public Integer getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(Integer value) {
    this.sortOrder = value;
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
