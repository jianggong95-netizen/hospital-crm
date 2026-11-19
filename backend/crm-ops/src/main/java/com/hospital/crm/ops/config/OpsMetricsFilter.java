package com.hospital.crm.ops.config;

import com.hospital.crm.ops.application.ApiMetricsRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 业务接口指标采集：统计 /api/** 请求的状态码与耗时（含安全检查产生的 401/403），
 * 写入进程内滑动窗口供告警评估，同时导出 Prometheus 指标：
 * crm_api_requests_total{module,status} 与 crm_api_request_duration_seconds{module}。
 *
 * <p>过滤器排在安全链之前，才能统计到未通过认证的 401 请求。
 * 依赖以 ObjectProvider 获取，在 @WebMvcTest 等切片测试中无对应 Bean 时自动跳过采集。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class OpsMetricsFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/";

    private final ObjectProvider<ApiMetricsRegistry> metricsRegistryProvider;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public OpsMetricsFilter(ObjectProvider<ApiMetricsRegistry> metricsRegistryProvider,
                            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.metricsRegistryProvider = metricsRegistryProvider;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ApiMetricsRegistry metricsRegistry = metricsRegistryProvider.getIfAvailable();
        String path = request.getRequestURI();
        if (metricsRegistry == null || !path.startsWith(API_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            int status = response.getStatus();
            String module = module(path);
            metricsRegistry.record(module, status, durationMs);
            publishToMicrometer(module, status, durationMs);
        }
    }

    private void publishToMicrometer(String module, int status, long durationMs) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            return;
        }
        String statusClass = (status / 100) + "xx";
        registry.counter("crm_api_requests_total", "module", module, "status", statusClass).increment();
        registry.timer("crm_api_request_duration_seconds", "module", module)
                .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /** 取路径第三段作为模块名：/api/v1/appointments/... → appointments。 */
    private String module(String path) {
        String[] segments = path.split("/");
        if (segments.length > 3 && !segments[3].isBlank()) {
            return segments[3];
        }
        return "root";
    }
}
