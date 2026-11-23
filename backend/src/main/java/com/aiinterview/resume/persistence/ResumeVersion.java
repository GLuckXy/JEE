package com.aiinterview.resume.persistence;

import com.baomidou.mybatisplus.annotation.*;

@TableName("resume_version")
public class ResumeVersion {
    @TableId(type = IdType.INPUT)
    public String id;
    public String resumeId;
    public Integer version;
    public String confirmedPayload;
    public java.time.LocalDateTime confirmedAt;
}

