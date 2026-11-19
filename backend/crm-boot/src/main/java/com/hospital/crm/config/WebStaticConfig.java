package com.hospital.crm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.List;

/**
 * 单进程部署静态资源托管（安装包形态）。
 *
 * <p>当 admin-web 构建产物经 {@code with-web} profile 打入 {@code classpath:/static/} 后，
 * 由本配置直接对外提供前端页面；后端 API、actuator、springdoc 仍由各自的映射优先处理。
 * 对前端 history 路由（如 {@code /login}、{@code /customers}、{@code /system/users}）做 SPA 回退，
 * 统一返回 {@code index.html}，避免直达或刷新时 404。
 *
 * <p>未打入前端产物（纯后端开发 / CI 单测）时 {@code static/index.html} 不存在，
 * 回退分支返回 null，行为与不配置本类一致，不影响接口 404 语义。
 */
@Configuration
public class WebStaticConfig implements WebMvcConfigurer {

    private static final String STATIC_ROOT = "static/";
    private static final String INDEX_HTML = STATIC_ROOT + "index.html";

    /** 这些前缀属于后端接口 / 运维端点 / 文档，绝不回退 index.html，保持原生 404/401 语义。 */
    private static final List<String> RESERVED_PREFIXES =
            List.of("api/", "actuator/", "v3/", "swagger-ui", "error");

    /** 命中静态资源扩展名却找不到文件时返回真 404，不把缺失的 js/css/图片伪装成页面。 */
    private static final List<String> ASSET_EXTENSIONS = List.of(
            ".js", ".mjs", ".css", ".map", ".json", ".ico", ".png", ".jpg", ".jpeg",
            ".gif", ".svg", ".webp", ".woff", ".woff2", ".ttf", ".eot", ".txt", ".xml", ".manifest", ".webmanifest");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/" + STATIC_ROOT)
                .resourceChain(true)
                .addResolver(new SpaResourceResolver());
    }

    /**
     * 先按真实路径解析静态文件；未命中且属于前端路由时回退 index.html。
     */
    static final class SpaResourceResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }

            String normalized = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;

            // 根路径直接给 index.html
            if (normalized.isEmpty()) {
                return indexResource();
            }

            // 后端 / 运维 / 文档前缀：保持 404，不回退
            for (String prefix : RESERVED_PREFIXES) {
                if (normalized.startsWith(prefix)) {
                    return null;
                }
            }

            // 带静态资源扩展名但未命中：真 404
            int dot = normalized.lastIndexOf('.');
            int slash = normalized.lastIndexOf('/');
            if (dot > slash && ASSET_EXTENSIONS.contains(normalized.substring(dot).toLowerCase())) {
                return null;
            }

            // 其余视为前端 history 路由，回退 index.html
            return indexResource();
        }

        private Resource indexResource() {
            Resource index = new ClassPathResource(INDEX_HTML);
            return index.exists() ? index : null;
        }
    }
}
