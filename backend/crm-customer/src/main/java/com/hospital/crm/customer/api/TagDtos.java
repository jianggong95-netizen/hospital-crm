package com.hospital.crm.customer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class TagDtos {

    private TagDtos() {
    }

    public record CreateRequest(
            @NotBlank(message = "标签编码不能为空")
            @Pattern(regexp = "^[A-Za-z0-9_]{2,64}$", message = "标签编码需为 2-64 位字母、数字或下划线")
            String tagCode,

            @NotBlank(message = "标签名称不能为空")
            @Size(max = 80, message = "标签名称长度不能超过 80")
            String tagName,

            @Pattern(regexp = "customer|service|risk", message = "标签类型取值不合法")
            String tagType,

            Integer sortOrder
    ) {
    }

    public record UpdateRequest(
            @NotBlank(message = "标签名称不能为空")
            @Size(max = 80, message = "标签名称长度不能超过 80")
            String tagName,

            @Pattern(regexp = "customer|service|risk", message = "标签类型取值不合法")
            String tagType,

            Integer sortOrder,

            @Pattern(regexp = "active|disabled", message = "状态取值不合法")
            String status
    ) {
    }

    public record TagResponse(
            UUID id,
            String tagCode,
            String tagName,
            String tagType,
            String status,
            int sortOrder,
            Instant createdAt
    ) {
    }
}
