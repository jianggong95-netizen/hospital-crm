package com.hospital.crm.system.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    public record CreateRequest(
            @NotBlank(message = "用户名不能为空")
            @Pattern(regexp = "^[A-Za-z0-9._-]{4,64}$", message = "用户名只能包含字母、数字、点、下划线、短横线，长度 4-64")
            String username,
            @NotBlank(message = "初始密码不能为空")
            @Size(min = 8, max = 64, message = "密码长度必须为 8-64 位")
            String password,
            @NotBlank(message = "姓名不能为空")
            @Size(max = 100, message = "姓名不能超过 100 字")
            String displayName,
            @Email(message = "邮箱格式不正确")
            @Size(max = 120, message = "邮箱不能超过 120 字")
            String email,
            @Pattern(regexp = "^[0-9+\\-\\s]{6,20}$", message = "手机号格式不正确")
            String mobile,
            @NotNull(message = "所属组织不能为空")
            UUID orgId,
            @NotEmpty(message = "至少选择一个角色")
            List<UUID> roleIds
    ) {
    }

    public record UpdateRequest(
            @NotBlank(message = "姓名不能为空")
            @Size(max = 100, message = "姓名不能超过 100 字")
            String displayName,
            @Email(message = "邮箱格式不正确")
            @Size(max = 120, message = "邮箱不能超过 120 字")
            String email,
            @Pattern(regexp = "^[0-9+\\-\\s]{6,20}$", message = "手机号格式不正确")
            String mobile,
            @NotNull(message = "所属组织不能为空")
            UUID orgId,
            @NotEmpty(message = "至少选择一个角色")
            List<UUID> roleIds,
            @Pattern(regexp = "active|disabled", message = "用户状态不合法")
            String status
    ) {
    }

    public record StatusRequest(
            @NotBlank(message = "状态不能为空")
            @Pattern(regexp = "active|disabled", message = "用户状态不合法")
            String status
    ) {
    }

    public record PasswordResetRequest(
            @NotBlank(message = "新密码不能为空")
            @Size(min = 8, max = 64, message = "密码长度必须为 8-64 位")
            String password
    ) {
    }

    public record UserResponse(
            UUID id,
            String username,
            String displayName,
            String email,
            String mobileMasked,
            UUID orgId,
            String orgName,
            String status,
            List<RoleSummary> roles,
            Instant lastLoginAt,
            Instant createdAt
    ) {
    }

    public record RoleSummary(UUID id, String roleCode, String roleName) {
    }
}
