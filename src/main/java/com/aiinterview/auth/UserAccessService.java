package com.aiinterview.auth;

import com.aiinterview.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class UserAccessService {
    public CurrentUser getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CurrentUser user))
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "请先登录");
        return user;
    }
    public CurrentUser requireStudent() {
        CurrentUser user = getCurrentUser();
        if (!"STUDENT".equals(user.role())) throw new BusinessException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "此功能仅对学生开放");
        return user;
    }
}
