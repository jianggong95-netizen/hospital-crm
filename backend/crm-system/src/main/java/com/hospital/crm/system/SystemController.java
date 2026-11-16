package com.hospital.crm.system;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.system.api.SystemInfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * 系统运行信息接口，用于前端联调与部署验收。
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final Environment environment;
    private final String version;

    public SystemController(Environment environment, @Value("${crm.version:1.0.0}") String version) {
        this.environment = environment;
        this.version = version;
    }

    @GetMapping("/info")
    public ApiResponse<SystemInfoResponse> info() {
        String[] activeProfiles = environment.getActiveProfiles();
        String[] profiles = activeProfiles.length > 0 ? activeProfiles : environment.getDefaultProfiles();
        SystemInfoResponse response = new SystemInfoResponse(
                environment.getProperty("spring.application.name", "hospital-crm"),
                version,
                List.of(profiles),
                System.getProperty("java.version"),
                System.getProperty("os.name"),
                ZoneId.systemDefault().getId(),
                Instant.now()
        );
        return ApiResponse.ok(response);
    }
}
