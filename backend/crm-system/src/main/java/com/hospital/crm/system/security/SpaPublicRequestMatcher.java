package com.hospital.crm.system.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;

/**
 * 单进程部署时，放行前端静态资源与 SPA history 路由的匿名 GET 请求。
 *
 * <p>仅匹配 GET，且路径不以受保护前缀（{@code /api}、{@code /actuator}、{@code /v3}、
 * {@code /swagger-ui}、{@code /error}）开头，因此不会放大后端 API 的安全边界：
 * 业务接口、运维端点、OpenAPI 文档仍交由各自的授权规则处理，POST/PUT/PATCH/DELETE 一律不匹配。
 *
 * <p>等价于"匿名 GET 最多只能拿到前端 HTML 与静态资源"，这正是单进程 SPA 的公开面。
 */
public final class SpaPublicRequestMatcher implements RequestMatcher {

    private static final List<String> PROTECTED_PREFIXES =
            List.of("/api", "/actuator", "/v3", "/swagger-ui", "/error");

    @Override
    public boolean matches(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (path == null || path.isEmpty()) {
            return false;
        }
        for (String prefix : PROTECTED_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/") || path.startsWith(prefix + ".")) {
                return false;
            }
        }
        return true;
    }
}
