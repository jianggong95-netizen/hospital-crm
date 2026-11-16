package com.hospital.crm.common.trace;

import org.slf4j.MDC;

/**
 * 链路追踪上下文，traceId 同时写入日志 MDC 与响应头。
 */
public final class TraceContext {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER_NAME = "X-Trace-Id";

    private TraceContext() {
    }

    public static String currentTraceId() {
        String traceId = MDC.get(MDC_KEY);
        return traceId == null ? "" : traceId;
    }
}
