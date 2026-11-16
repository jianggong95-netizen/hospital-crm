package com.hospital.crm.his.support;

/**
 * HIS 返回数据最小化与脱敏工具。
 * 搜索结果仅返回脱敏姓名与手机号，不返回证件号明文。
 */
public final class HisMasking {

    private HisMasking() {
    }

    public static String normalizePhone(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 13 && digits.startsWith("86")) {
            digits = digits.substring(2);
        }
        if (digits.length() == 11 && digits.startsWith("1")) {
            return digits;
        }
        return digits.isEmpty() ? raw.trim() : digits;
    }

    public static boolean looksLikeMobile(String raw) {
        String digits = normalizePhone(raw);
        return digits != null && digits.matches("^1\\d{10}$");
    }

    public static String normalizeIdNo(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.replaceAll("\\s", "").toUpperCase(java.util.Locale.ROOT);
    }

    public static boolean looksLikeIdNo(String raw) {
        String normalized = normalizeIdNo(raw);
        return normalized != null && normalized.matches("^(\\d{15}|\\d{17}[\\dX])$");
    }

    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.length() == 1) {
            return "*";
        }
        int stars = Math.min(trimmed.length() - 1, 3);
        return trimmed.charAt(0) + "*".repeat(stars);
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 11) {
            return digits.substring(0, 3) + "****" + digits.substring(7);
        }
        if (digits.length() > 4) {
            return digits.substring(0, 2) + "***" + digits.substring(digits.length() - 2);
        }
        return "***";
    }
}
