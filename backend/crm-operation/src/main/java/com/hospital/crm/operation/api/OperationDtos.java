package com.hospital.crm.operation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class OperationDtos {

    private OperationDtos() {
    }

    // ------------------------------------------------------------------
    // 来源渠道
    // ------------------------------------------------------------------

    public record ChannelCreateRequest(
            @NotBlank(message = "渠道编码不能为空")
            @Pattern(regexp = "[A-Z0-9_]{2,64}", message = "渠道编码仅允许大写字母、数字与下划线")
            String channelCode,
            @NotBlank(message = "渠道名称不能为空")
            @Size(max = 100, message = "渠道名称长度不能超过 100")
            String channelName,
            @Pattern(regexp = "online|offline|referral|activity|his|other", message = "渠道类型不合法")
            String channelType,
            @Size(max = 500, message = "备注长度不能超过 500")
            String remark
    ) {
    }

    public record ChannelStatusRequest(
            @NotBlank(message = "状态不能为空")
            @Pattern(regexp = "active|disabled", message = "状态取值不合法")
            String status
    ) {
    }

    public record ChannelView(
            UUID id,
            String channelCode,
            String channelName,
            String channelType,
            String status,
            String remark
    ) {
    }

    // ------------------------------------------------------------------
    // 线索
    // ------------------------------------------------------------------

    public record LeadCreateRequest(
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
            @NotBlank(message = "来源渠道不能为空")
            @Size(max = 64, message = "来源渠道编码长度不合法")
            String sourceChannelCode,
            UUID ownerUserId,
            @Size(max = 1000, message = "备注长度不能超过 1000")
            String remark,
            Boolean forceCreate
    ) {
    }

    public record LeadUpdateRequest(
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
            @Size(max = 64, message = "来源渠道编码长度不合法")
            String sourceChannelCode,
            UUID ownerUserId,
            @Size(max = 1000, message = "备注长度不能超过 1000")
            String remark
    ) {
    }

    public record LeadStatusRequest(
            @NotBlank(message = "状态不能为空")
            String status,
            @Size(max = 500, message = "说明长度不能超过 500")
            String remark
    ) {
    }

    public record LeadConvertRequest(
            UUID customerId,
            Boolean createCustomer,
            @Size(max = 500, message = "备注长度不能超过 500")
            String remark
    ) {
    }

    public record LeadMergeRequest(
            @NotNull(message = "目标线索不能为空")
            UUID targetLeadId,
            @Size(max = 500, message = "合并说明长度不能超过 500")
            String reason
    ) {
    }

    public record LeadAssignRequest(
            @NotNull(message = "目标员工不能为空")
            UUID toUserId,
            @Size(max = 500, message = "分派原因长度不能超过 500")
            String reason
    ) {
    }

    public record ContactRecordCreateRequest(
            @NotBlank(message = "联系类型不能为空")
            @Pattern(regexp = "phone|wechat|sms|visit|other", message = "联系类型不合法")
            String contactType,
            Instant contactedAt,
            @NotBlank(message = "联系内容不能为空")
            @Size(max = 2000, message = "联系内容长度不能超过 2000")
            String content,
            @NotBlank(message = "联系结果不能为空")
            @Pattern(regexp = "CONNECTED|NO_ANSWER|REFUSED|INTERESTED|CONVERTED|OTHER", message = "联系结果不合法")
            String resultCode,
            Instant nextFollowUpAt
    ) {
    }

    public record LeadSummary(
            UUID id,
            String leadNo,
            String name,
            String gender,
            LocalDate birthDate,
            String phoneMasked,
            String idNoMasked,
            String sourceChannelCode,
            String sourceChannelName,
            String status,
            UUID ownerUserId,
            String ownerName,
            UUID orgId,
            UUID convertedCustomerId,
            UUID mergedIntoLeadId,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record LeadDetail(
            LeadSummary summary,
            String remark,
            Instant convertedAt,
            List<ContactRecordView> recentContacts,
            List<AssignmentView> assignments,
            ConversionView conversion
    ) {
    }

    public record DuplicateLead(
            UUID id,
            String leadNo,
            String name,
            String phoneMasked,
            String status,
            Instant createdAt
    ) {
    }

    public record AssigneeView(
            UUID id,
            String username,
            String displayName,
            UUID orgId
    ) {
    }

    public record ContactRecordView(
            UUID id,
            String contactType,
            Instant contactedAt,
            UUID contactedBy,
            String contactedByName,
            String content,
            String resultCode,
            Instant nextFollowUpAt
    ) {
    }

    public record AssignmentView(
            UUID id,
            UUID fromUserId,
            String fromUserName,
            UUID toUserId,
            String toUserName,
            String reason,
            Instant assignedAt
    ) {
    }

    public record ConversionView(
            UUID id,
            UUID leadId,
            UUID customerId,
            String customerNo,
            String customerName,
            String conversionMode,
            String remark,
            UUID convertedBy,
            Instant convertedAt
    ) {
    }

    // ------------------------------------------------------------------
    // 邀约
    // ------------------------------------------------------------------

    public record InvitationCreateRequest(
            UUID leadId,
            UUID customerId,
            @NotBlank(message = "邀约渠道不能为空")
            @Size(max = 64, message = "渠道编码长度不合法")
            String channelCode,
            @NotBlank(message = "邀约主题不能为空")
            @Size(max = 200, message = "邀约主题长度不能超过 200")
            String title,
            @NotNull(message = "计划邀约时间不能为空")
            Instant plannedAt,
            UUID ownerUserId,
            @Size(max = 1000, message = "备注长度不能超过 1000")
            String remark
    ) {
    }

    public record InvitationStatusRequest(
            @NotBlank(message = "状态不能为空")
            @Pattern(regexp = "CONTACTED|ACCEPTED|DECLINED|CANCELLED|EXPIRED", message = "邀约状态不合法")
            String status,
            @Size(max = 500, message = "结果长度不能超过 500")
            String result,
            @Size(max = 1000, message = "备注长度不能超过 1000")
            String remark
    ) {
    }

    public record InvitationView(
            UUID id,
            String invitationNo,
            UUID leadId,
            String leadNo,
            UUID customerId,
            String customerNo,
            String customerName,
            String channelCode,
            String channelName,
            String title,
            Instant plannedAt,
            UUID ownerUserId,
            String ownerName,
            String status,
            String result,
            String remark,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    // ------------------------------------------------------------------
    // 活动
    // ------------------------------------------------------------------

    public record ActivityCreateRequest(
            @NotBlank(message = "活动名称不能为空")
            @Size(max = 200, message = "活动名称长度不能超过 200")
            String activityName,
            @Pattern(regexp = "health_lecture|screening|charity|marketing|other", message = "活动类型不合法")
            String activityType,
            @NotNull(message = "开始时间不能为空")
            Instant startAt,
            @NotNull(message = "结束时间不能为空")
            Instant endAt,
            @Size(max = 200, message = "活动地点长度不能超过 200")
            String location,
            Integer capacity,
            @Size(max = 2000, message = "活动说明长度不能超过 2000")
            String description
    ) {
    }

    public record ActivityStatusRequest(
            @NotBlank(message = "状态不能为空")
            @Pattern(regexp = "DRAFT|PUBLISHED|CLOSED|CANCELLED", message = "活动状态不合法")
            String status
    ) {
    }

    public record ParticipantCreateRequest(
            UUID leadId,
            UUID customerId,
            @NotBlank(message = "参与人姓名不能为空")
            @Size(max = 100, message = "参与人姓名长度不能超过 100")
            String participantName,
            @Size(max = 32, message = "手机号长度不合法")
            String phone,
            @Size(max = 500, message = "备注长度不能超过 500")
            String remark
    ) {
    }

    public record ParticipantStatusRequest(
            @NotBlank(message = "状态不能为空")
            @Pattern(regexp = "ATTENDED|CANCELLED|NO_SHOW", message = "参与状态不合法")
            String status,
            @Size(max = 500, message = "备注长度不能超过 500")
            String remark
    ) {
    }

    public record ActivityView(
            UUID id,
            String activityNo,
            String activityName,
            String activityType,
            Instant startAt,
            Instant endAt,
            String location,
            Integer capacity,
            String status,
            String description,
            long participantCount,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ParticipantView(
            UUID id,
            UUID activityId,
            UUID leadId,
            UUID customerId,
            String participantName,
            String phoneMasked,
            String status,
            Instant registeredAt,
            Instant checkedInAt,
            String remark
    ) {
    }
}
