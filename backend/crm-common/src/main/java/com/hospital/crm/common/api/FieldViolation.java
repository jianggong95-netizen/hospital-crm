package com.hospital.crm.common.api;

/**
 * 字段级校验失败明细。
 */
public record FieldViolation(String field, String reason) {
}
