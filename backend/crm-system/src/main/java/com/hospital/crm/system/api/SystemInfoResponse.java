package com.hospital.crm.system.api;

import java.time.Instant;
import java.util.List;

/**
 * 运行信息，仅暴露非敏感的环境元数据。
 */
public record SystemInfoResponse(
        String application,
        String version,
        List<String> profiles,
        String javaVersion,
        String os,
        String timezone,
        Instant serverTime
) {
}
