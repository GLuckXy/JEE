package com.aiinterview.content.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("content_category")
public class Category {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long jobId;
  private String name;
  private String description;
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

  public Long getJobId() {
    return jobId;
  }

  public void setJobId(Long value) {
    this.jobId = value;
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
