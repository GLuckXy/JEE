package com.aiinterview.common;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
    public final HttpStatus status;
    public final String code;
    public BusinessException(HttpStatus status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public static BusinessException bad(String code, String message) { return new BusinessException(HttpStatus.BAD_REQUEST, code, message); }
    public static BusinessException conflict(String code, String message) { return new BusinessException(HttpStatus.CONFLICT, code, message); }
    public static BusinessException missing(String code) { return new BusinessException(HttpStatus.NOT_FOUND, code, "资源不存在或无权访问"); }
}
