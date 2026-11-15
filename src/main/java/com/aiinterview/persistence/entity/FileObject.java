package com.aiinterview.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("file_object")
public class FileObject {
    @TableId(type = IdType.INPUT)
    public String id;
    public String ownerId;
    public String storageKey;
    public String originalName;
    public String mimeType;
    public Long fileSize;
    public String bizType;
    public String bizId;
    public String storageStatus;
    public java.time.LocalDateTime createdAt;
}

