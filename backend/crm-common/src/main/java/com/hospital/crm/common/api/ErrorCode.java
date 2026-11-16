package com.hospital.crm.common.api;

/**
 * 统一错误码，与 api/04-标准API接口规范.md 保持一致。
 */
public enum ErrorCode {

    VALIDATION_ERROR("VALIDATION_ERROR", 400, "请求参数不正确"),
    UNAUTHENTICATED("UNAUTHENTICATED", 401, "未认证"),
    FORBIDDEN("FORBIDDEN", 403, "无权限"),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", 404, "资源不存在"),
    BUSINESS_CONFLICT("BUSINESS_CONFLICT", 409, "业务状态冲突"),
    IDEMPOTENCY_CONFLICT("IDEMPOTENCY_CONFLICT", 409, "幂等键冲突"),
    BUSINESS_RULE_FAILED("BUSINESS_RULE_FAILED", 422, "不满足业务规则"),
    RATE_LIMITED("RATE_LIMITED", 429, "请求过于频繁"),
    INTERNAL_ERROR("INTERNAL_ERROR", 500, "未知内部错误"),
    UPSTREAM_ERROR("UPSTREAM_ERROR", 502, "HIS或第三方服务错误"),
    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", 503, "服务暂不可用");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(String code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
