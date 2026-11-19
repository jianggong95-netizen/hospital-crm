package com.hospital.crm.system;

import com.hospital.crm.web.GlobalExceptionHandler;
import com.hospital.crm.web.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SystemController.class)
@WithMockUser
@Import({TraceIdFilter.class, GlobalExceptionHandler.class})
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsUnifiedEnvelopeWithTraceId() throws Exception {
        mockMvc.perform(get("/api/v1/system/info"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.application").value("hospital-crm"))
                // 版本号由构建注入（-Dcrm.release.version），测试只校验格式而非具体值
                .andExpect(jsonPath("$.data.version").value(org.hamcrest.Matchers.matchesPattern("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?")))
                .andExpect(jsonPath("$.data.profiles[0]").value("dev"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void propagatesIncomingTraceId() throws Exception {
        mockMvc.perform(get("/api/v1/system/info").header("X-Trace-Id", "trace-from-gateway-01"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "trace-from-gateway-01"))
                .andExpect(jsonPath("$.traceId").value("trace-from-gateway-01"));
    }

    @Test
    void returnsUnifiedEnvelopeForUnknownResource() throws Exception {
        mockMvc.perform(get("/api/v1/system/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
