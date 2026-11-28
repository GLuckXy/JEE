package com.aiinterview.logging.dto;

public record OperationLogView(String id, String userId, String account, String action,
        String targetId, String detail, java.time.LocalDateTime createdAt) {}
