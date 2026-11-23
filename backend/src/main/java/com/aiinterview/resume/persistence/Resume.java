package com.aiinterview.resume.persistence;

import com.baomidou.mybatisplus.annotation.*;

@TableName("resume")
public class Resume {
    @TableId(type = IdType.INPUT)
    public String id;
    public String userId;
    public String fileId;
    public String parseStatus;
    public String rawParseResult;
    public String draftPayload;
    public String confirmStatus;
    public Integer version;
    public Integer revision;
    public String failureReason;
    public java.time.LocalDateTime confirmedAt;
    public java.time.LocalDateTime createdAt;
    public java.time.LocalDateTime updatedAt;
    public java.time.LocalDateTime deletedAt;
}

