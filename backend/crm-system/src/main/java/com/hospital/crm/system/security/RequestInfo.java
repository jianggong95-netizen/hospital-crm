package com.hospital.crm.system.security;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestInfo {

    private RequestInfo() {
    }

    public static String ip(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        String candidate = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        if (candidate == null || candidate.length() > 64
                || !candidate.matches("[0-9a-fA-F:.]+")) {
            return null;
        }
        return candidate;
    }

    public static String userAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent;
    }
}
