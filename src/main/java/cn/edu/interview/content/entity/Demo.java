package cn.edu.interview.content.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("content_demo")
public class Demo {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long jobId;
  private String title;
  private String introduction;
  private String sourceNote;
  private String status;
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

  public String getTitle() {
    return title;
  }

  public void setTitle(String value) {
    this.title = value;
  }

  public String getIntroduction() {
    return introduction;
  }

  public void setIntroduction(String value) {
    this.introduction = value;
  }

  public String getSourceNote() {
    return sourceNote;
  }

  public void setSourceNote(String value) {
    this.sourceNote = value;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    this.status = value;
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
