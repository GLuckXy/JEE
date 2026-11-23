package com.aiinterview.content.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(long page, long size, long total, List<T> records) {
  public static <E, T> PageResponse<T> from(IPage<E> p, Function<E, T> mapper) {
    return new PageResponse<>(
        p.getCurrent(), p.getSize(), p.getTotal(), p.getRecords().stream().map(mapper).toList());
  }
}
