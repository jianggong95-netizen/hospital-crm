package com.hospital.crm.common.api;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiResponseTest {

    @Test
    void okResponseCarriesTraceIdAndTimestamp() {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("traceId", "trace-ok-0001")) {
            ApiResponse<String> response = ApiResponse.ok("payload");

            assertEquals("OK", response.code());
            assertEquals("success", response.message());
            assertEquals("payload", response.data());
            assertEquals("trace-ok-0001", response.traceId());
            assertNotNull(response.timestamp());
            assertNull(response.details());
        }
    }

    @Test
    void errorResponseUsesDefaultMessage() {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("traceId", "trace-err-0001")) {
            ApiResponse<Void> response = ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, null, null);

            assertEquals("RESOURCE_NOT_FOUND", response.code());
            assertEquals("资源不存在", response.message());
            assertEquals("trace-err-0001", response.traceId());
        }
    }

    @Test
    void errorResponseKeepsFieldDetails() {
        ApiResponse<Void> response = ApiResponse.error(ErrorCode.VALIDATION_ERROR, null,
                List.of(new FieldViolation("phone", "格式不正确")));

        assertEquals(1, response.details().size());
        assertEquals("phone", response.details().getFirst().field());
    }
}
