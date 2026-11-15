package com.aiinterview.user;

import com.aiinterview.persistence.entity.User;
import java.time.LocalDateTime;

public record UserView(String id, String account, String role, String status, String nickname,
        String email, String phone, String school, String major, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static UserView of(User u) { return new UserView(u.id,u.account,u.role,u.status,u.nickname,u.email,u.phone,u.school,u.major,u.createdAt,u.updatedAt); }
}
