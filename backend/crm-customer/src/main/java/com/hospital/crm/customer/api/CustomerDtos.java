package com.hospital.crm.customer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record CreateRequest(
            @NotBlank(message = "姓名不能为空")
            @Size(max = 100, message = "姓名长度不能超过 100")
            String name,

            @Pattern(regexp = "unknown|male|female", message = "性别取值不合法")
            String gender,

            @Past(message = "出生日期必须早于今天")
            LocalDate birthDate,

            @Size(max = 32, message = "手机号长度不合法")
            String phone,

            @Size(max = 32, message = "证件号长度不合法")
            String idNo,

            @Size(max = 32, message = "来源编码长度不能超过 32")
            String sourceCode,

            @Size(max = 1000, message = "备注长度不能超过 1000")
            String notes,

            List<UUID> tagIds,

            Boolean forceCreate
    ) {
    }

    public record UpdateRequest(
            @Size(max = 100, message = "姓名长度不能超过 100")
            String name,

            @Pattern(regexp = "unknown|male|female", message = "性别取值不合法")
            String gender,

            @Past(message = "出生日期必须早于今天")
            LocalDate birthDate,

            @Size(max = 32, message = "手机号长度不合法")
            String phone,

            @Size(max = 32, message = "证件号长度不合法")
            String idNo,

            @Size(max = 32, message = "来源编码长度不能超过 32")
            String sourceCode,

            @Size(max = 1000, message = "备注长度不能超过 1000")
            String notes,

            List<UUID> tagIds
    ) {
    }

    public record StatusRequest(
            @NotBlank(message = "状态不能为空")
            @Pattern(regexp = "active|disabled", message = "状态取值不合法")
            String status
    ) {
    }

    public record TagSummary(UUID id, String tagCode, String tagName, String tagType) {
    }

    /**
     * 供其他模块引用的客户简要信息，仅包含脱敏手机号，不含任何敏感明文。
     */
    public record CustomerBrief(
            UUID id,
            String customerNo,
            String name,
            String phoneMasked,
            String status
    ) {
    }

    public record CustomerSummary(
            UUID id,
            String customerNo,
            String name,
            String gender,
            LocalDate birthDate,
            String phoneMasked,
            String idNoMasked,
            String sourceCode,
            String status,
            List<TagSummary> tags,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CustomerDetail(
            UUID id,
            String customerNo,
            String name,
            String gender,
            LocalDate birthDate,
            String phoneMasked,
            String idNoMasked,
            String phone,
            String idNo,
            boolean sensitiveVisible,
            String sourceCode,
            String status,
            String notes,
            List<TagSummary> tags,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
