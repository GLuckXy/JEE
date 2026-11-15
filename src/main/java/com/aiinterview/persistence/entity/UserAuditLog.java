package com.aiinterview.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("user_audit_log")
public class UserAuditLog {
    @TableId(type = IdType.INPUT)
    public String id;
    public String actorId;
    public String targetId;
    public String action;
    public java.time.LocalDateTime createdAt;
}

