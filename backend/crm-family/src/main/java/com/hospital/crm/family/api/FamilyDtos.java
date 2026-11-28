package com.hospital.crm.family.api;

import com.hospital.crm.customer.api.CustomerDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
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

    /**
     * 家庭关系图谱节点：成员客户基础信息 + 相对户主的关系。
     */
    public record GraphNode(
            UUID customerId,
            String name,
            String customerNo,
            String phoneMasked,
            String gender,
            LocalDate birthDate,
            String relationshipCode,
            String relationshipLabel,
            boolean admin
    ) {
    }

    /**
     * 家庭关系图谱边：label 表示 fromCustomerId 是 toCustomerId 的什么（演示级称谓推导）。
     * 每对成员输出双向两条边，便于按任一成员视角查询称谓。
     */
    public record GraphEdge(
            UUID fromCustomerId,
            UUID toCustomerId,
            String label
    ) {
    }

    public record FamilyGraph(
            List<GraphNode> nodes,
            List<GraphEdge> edges
    ) {
    }

    /**
     * 成员关联视图用的成员简卡：含性别与出生日期（称谓推导依赖），不含敏感明文。
     * relationshipCode/Label 仍是相对户主存储的关系编码。
     */
    public record MemberBrief(
            UUID memberId,
            UUID customerId,
            String customerNo,
            String name,
            String gender,
            LocalDate birthDate,
            String phoneMasked,
            String relationshipCode,
            String relationshipLabel,
            boolean admin
    ) {
    }

    /**
     * 以"我"（锚点成员）视角的一条关联：relationToSelf 表示"该成员是我的什么人"，
     * relationFromSelf 表示反向称谓"我是该成员的什么人"，两者由户主中转推导。
     */
    public record MemberRelationItem(
            MemberBrief member,
            String relationToSelf,
            String relationFromSelf
    ) {
    }

    /**
     * 其它成员之间的关系概览条目：label 表示 fromMemberId 是 toMemberId 的什么人。
     * 每对成员输出双向两条，前端可按任一成员视角取用。
     */
    public record PeerRelationItem(
            UUID fromMemberId,
            String fromName,
            UUID toMemberId,
            String toName,
            String label
    ) {
    }

    /**
     * 家庭成员关联视图：self 为锚点成员，relations 为其余有效成员相对"我"的关联卡片，
     * peerRelations 为除 self 外成员之间的两两称谓概览。
     */
    public record MemberRelations(
            MemberBrief self,
            List<MemberRelationItem> relations,
            List<PeerRelationItem> peerRelations
    ) {
    }
}
