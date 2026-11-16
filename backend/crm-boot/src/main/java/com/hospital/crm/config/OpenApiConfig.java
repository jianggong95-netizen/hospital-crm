package com.hospital.crm.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档基线。认证在 CRM-002 接入，安全方案先按规范声明。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI hospitalCrmOpenApi(CrmProperties properties) {
        String version = properties.version() == null ? "1.0.0" : properties.version();
        return new OpenAPI()
                .info(new Info()
                        .title("Hospital CRM API")
                        .version(version)
                        .description("医院智慧客户服务与运营管理平台标准 API 基线。"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
