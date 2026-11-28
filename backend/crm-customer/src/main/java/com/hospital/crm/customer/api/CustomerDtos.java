package com.hospital.crm.customer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

    /**
     * 供跨模块聚合视图（如家庭关系图谱）使用的客户基础信息，含性别与出生日期，不含敏感明文。
     */
    public record CustomerProfile(
            UUID id,
            String customerNo,
            String name,
            String gender,
            LocalDate birthDate,
            String phoneMasked
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

    /**
     * 查重分组内的客户条目，仅含脱敏信息。
     */
    public record DuplicateItem(
            UUID id,
            String customerNo,
            String name,
            String phoneMasked,
            LocalDate birthDate,
            Instant createdAt
    ) {
    }

    /**
     * 一组重复客户：reason 为 phone（手机号相同）或 name_birth（姓名+生日相同）。
     */
    public record DuplicateGroup(
            String reason,
            List<DuplicateItem> items
    ) {
    }

    public record MergeRequest(
            @NotNull(message = "被合并客户ID不能为空")
            UUID sourceId
    ) {
    }

    /**
     * 合并结果：mergedTables 为改挂到目标客户的表与行数，
     * removedRows 为因唯一约束冲突而删除的源数据行（含余额并入后的源会员账户），
     * filledFields 为从被合并档案补齐到目标档案的缺失字段。
     */
    public record MergeResponse(
            UUID targetId,
            UUID sourceId,
            Map<String, Integer> mergedTables,
            Map<String, Integer> removedRows,
            List<String> filledFields
    ) {
    }

    /**
     * HIS 患者全量同步结果：total 本次处理的 HIS 患者数；created 新建客户数；
     * linked 匹配到现有客户并绑定主索引数；updated 补齐性别/生日数；skipped 已绑定或无效数据跳过数。
     */
    public record HisSyncResult(
            int total,
            int created,
            int linked,
            int updated,
            int skipped
    ) {
    }
}
