package com.aiinterview.logging.persistence;

import com.baomidou.mybatisplus.annotation.*;

@TableName("operation_log")
public class OperationLog {
    @TableId(type = IdType.INPUT)
    public String id;
    public String userId;
    public String action;
    public String targetId;
    public String detail;
    public java.time.LocalDateTime createdAt;
}
