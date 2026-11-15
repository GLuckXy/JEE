package com.aiinterview.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("auth_session")
public class AuthSession {
    @TableId(type = IdType.INPUT)
    public String id;
    public String userId;
    public java.time.LocalDateTime expiresAt;
    public java.time.LocalDateTime revokedAt;
    public java.time.LocalDateTime createdAt;
}

