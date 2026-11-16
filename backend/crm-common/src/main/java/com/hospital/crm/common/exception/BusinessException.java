package com.hospital.crm.common.exception;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.FieldViolation;

import java.util.List;

/**
 * 可预期的业务异常，由全局异常处理器转换为统一响应。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<FieldViolation> details;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), null);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BusinessException(ErrorCode errorCode, String message, List<FieldViolation> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public List<FieldViolation> getDetails() {
        return details;
    }
}
