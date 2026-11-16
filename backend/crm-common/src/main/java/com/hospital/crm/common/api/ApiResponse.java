package com.hospital.crm.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.hospital.crm.common.trace.TraceContext;

import java.time.Instant;
import java.util.List;

/**
 * 统一响应结构，字段顺序与接口规范保持一致。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"code", "message", "data", "details", "traceId", "timestamp"})
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        List<FieldViolation> details,
        String traceId,
        Instant timestamp
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "success", data, null, TraceContext.currentTraceId(), Instant.now());
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, String message, List<FieldViolation> details) {
        String resolvedMessage = (message == null || message.isBlank()) ? errorCode.defaultMessage() : message;
        return new ApiResponse<>(errorCode.code(), resolvedMessage, null, details,
                TraceContext.currentTraceId(), Instant.now());
    }
}
