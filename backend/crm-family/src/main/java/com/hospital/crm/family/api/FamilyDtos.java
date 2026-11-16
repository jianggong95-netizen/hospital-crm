package com.hospital.crm.family.api;

import com.hospital.crm.customer.api.CustomerDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class FamilyDtos {

    private FamilyDtos() {
    }

    public record CreateRequest(
            @NotBlank(message = "家庭名称不能为空")
            @Size(max = 100, message = "家庭名称长度不能超过 100")
            String familyName,

            @NotNull(message = "家庭管理员客户不能为空")
            UUID ownerCustomerId
    ) {
    }

    public record AddMemberRequest(
            @NotNull(message = "客户不能为空")
            UUID customerId,

            @NotBlank(message = "家庭关系不能为空")
            @Size(max = 32, message = "家庭关系编码长度不能超过 32")
            String relationshipCode
    ) {
    }

    public record UpdateMemberRequest(
            @NotBlank(message = "家庭关系不能为空")
            @Size(max = 32, message = "家庭关系编码长度不能超过 32")
            String relationshipCode,

            Boolean admin
    ) {
    }

    public record CreateAuthorizationRequest(
            @NotNull(message = "被授权客户不能为空")
            UUID granteeCustomerId,

            @NotBlank(message = "代办范围不能为空")
            @Size(max = 32, message = "代办范围编码长度不能超过 32")
            String scopeCode,

            Instant validFrom,

            Instant validTo
    ) {
    }

    public record DelegationCheckRequest(
            @NotNull(message = "家庭成员不能为空")
            UUID memberId,

            @NotNull(message = "被授权客户不能为空")
            UUID granteeCustomerId,

            @NotBlank(message = "代办范围不能为空")
            @Size(max = 32, message = "代办范围编码长度不能超过 32")
            String scopeCode
    ) {
    }

    public record FamilySummary(
            UUID id,
            String familyNo,
            String familyName,
            String status,
            int memberCount,
            String adminName,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record FamilyDetail(
            UUID id,
            String familyNo,
            String familyName,
            String status,
            List<MemberItem> members,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record FamilyMembership(
            UUID familyId,
            String familyNo,
            String familyName,
            String familyStatus,
            String relationshipCode,
            String relationshipLabel,
            boolean admin,
            int memberCount,
            Instant validFrom
    ) {
    }

    public record MemberItem(
            UUID id,
            UUID familyId,
            CustomerDtos.CustomerBrief customer,
            String relationshipCode,
            String relationshipLabel,
            boolean admin,
            String status,
            Instant validFrom,
            Instant validTo
    ) {
    }

    public record AuthorizationItem(
            UUID id,
            UUID memberId,
            CustomerDtos.CustomerBrief grantee,
            String scopeCode,
            String scopeLabel,
            String status,
            Instant validFrom,
            Instant validTo,
            Instant revokedAt,
            Instant createdAt
    ) {
    }

    public record DelegationCheckResponse(
            boolean allowed,
            UUID memberId,
            UUID granteeCustomerId,
            String scopeCode,
            UUID authorizationId,
            Instant validTo
    ) {
    }
}
