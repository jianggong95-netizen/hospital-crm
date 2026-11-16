package com.hospital.crm.common.api;

import java.util.List;

/**
 * 统一分页响应。
 */
public record PageResponse<T>(
        List<T> items,
        long total,
        int page,
        int pageSize,
        int totalPages
) {

    public static <T> PageResponse<T> of(List<T> items, long total, int page, int pageSize) {
        int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) total / pageSize);
        return new PageResponse<>(items, total, page, pageSize, totalPages);
    }
}
