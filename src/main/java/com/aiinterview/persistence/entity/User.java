package com.aiinterview.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("sys_user")
public class User {
    @TableId(type = IdType.INPUT)
    public String id;
    public String account;
    public String passwordHash;
    public String role;
    public String status;
    public String nickname;
    public String email;
    public String phone;
    public String school;
    public String major;
    public String updatedBy;
    public java.time.LocalDateTime createdAt;
    public java.time.LocalDateTime updatedAt;
}

