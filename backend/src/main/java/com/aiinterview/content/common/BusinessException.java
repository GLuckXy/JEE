package com.aiinterview.content.common;

import org.springframework.http.HttpStatus;

public class BusinessException extends com.aiinterview.common.BusinessException {
  public BusinessException(HttpStatus status, String message) {
    super(status, switch (status) {
      case NOT_FOUND -> "CONTENT_NOT_FOUND";
      case CONFLICT -> "STATE_CONFLICT";
      default -> "INVALID_REQUEST";
    }, message);
  }

  public static BusinessException missing() {
    return new BusinessException(HttpStatus.NOT_FOUND, "内容不存在或尚未开放");
  }

  public static BusinessException invalid(String m) {
    return new BusinessException(HttpStatus.BAD_REQUEST, m);
  }

  public static BusinessException conflict(String m) {
    return new BusinessException(HttpStatus.CONFLICT, m);
  }
}
