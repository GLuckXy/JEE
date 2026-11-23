package com.aiinterview.common;

import java.util.List;

public record PageResult<T>(long total, int page, int size, List<T> records) {
    public static int offset(int page, int size) {
        if (page < 1 || page > 100000 || size < 1 || size > 100)
            throw BusinessException.bad("VALIDATION_FAILED", "page 为 1–100000，size 为 1–100");
        return (page - 1) * size;
    }
}
