package edu.zjut.interview.formal;

import com.aiinterview.common.BusinessException;
import org.springframework.http.HttpStatus;

final class InterviewProblem extends BusinessException {
    InterviewProblem(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    static InterviewProblem notFound(String message) {
        return new InterviewProblem(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    static InterviewProblem conflict(String message) {
        return new InterviewProblem(HttpStatus.CONFLICT, "STATE_CONFLICT", message);
    }

    static InterviewProblem badRequest(String message) {
        return new InterviewProblem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    static InterviewProblem speechUnavailable() {
        return new InterviewProblem(HttpStatus.SERVICE_UNAVAILABLE, "ASR_UNAVAILABLE",
                "语音转写暂不可用，请稍后重试");
    }

    static InterviewProblem modelUnavailable() {
        return new InterviewProblem(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE",
                "项目提问服务尚未配置或暂不可用，请稍后重试");
    }
}
