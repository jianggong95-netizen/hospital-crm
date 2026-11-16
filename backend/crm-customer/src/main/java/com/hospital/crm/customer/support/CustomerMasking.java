package com.hospital.crm.customer.support;

import java.util.Locale;

/**
 * 客户敏感字段的规范化与脱敏规则。
 */
public final class CustomerMasking {

    private CustomerMasking() {
    }

    /**
     * 仅保留数字，用于手机号规范化。
     */
    public static String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    /**
     * 证件号去空格并统一大写（含 X）。
     */
    public static String normalizeIdNo(String idNo) {
        if (idNo == null) {
            return null;
        }
        String value = idNo.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        return value.isEmpty() ? null : value;
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String value = normalizePhone(phone);
        if (value == null) {
            return null;
        }
        if (value.length() <= 7) {
            return "*".repeat(value.length());
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    /**
     * 证件号保留前 6 位与后 4 位，其余以 * 代替。
     */
    public static String maskIdNo(String idNo) {
        String value = normalizeIdNo(idNo);
        if (value == null) {
            return null;
        }
        if (value.length() <= 10) {
            return "*".repeat(value.length());
        }
        return value.substring(0, 6) + "********" + value.substring(value.length() - 4);
    }
}
