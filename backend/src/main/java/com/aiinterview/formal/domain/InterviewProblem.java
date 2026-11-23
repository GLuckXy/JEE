package com.aiinterview.formal.domain;

import com.aiinterview.common.BusinessException;
import org.springframework.http.HttpStatus;

public final class InterviewProblem extends BusinessException {
    private InterviewProblem(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static InterviewProblem notFound(String message) {
        return new InterviewProblem(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static InterviewProblem conflict(String message) {
        return new InterviewProblem(HttpStatus.CONFLICT, "STATE_CONFLICT", message);
    }

    public static InterviewProblem badRequest(String message) {
        return new InterviewProblem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    public static InterviewProblem speechUnavailable() {
        return new InterviewProblem(HttpStatus.SERVICE_UNAVAILABLE, "ASR_UNAVAILABLE",
                "语音转写暂不可用，请稍后重试");
    }

    public static InterviewProblem modelUnavailable() {
        return new InterviewProblem(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE",
                "项目提问服务尚未配置或暂不可用，请稍后重试");
    }
}
