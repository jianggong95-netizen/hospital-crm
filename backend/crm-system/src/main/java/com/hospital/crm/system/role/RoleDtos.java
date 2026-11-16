package com.hospital.crm.system.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RoleDtos {

    private RoleDtos() {
    }

    public record CreateRequest(
            @NotBlank(message = "角色编码不能为空")
            @Pattern(regexp = "^[A-Za-z0-9_]{2,64}$", message = "角色编码只能包含字母、数字和下划线，长度 2-64")
            String roleCode,
            @NotBlank(message = "角色名称不能为空")
            @Size(max = 120, message = "角色名称不能超过 120 字")
            String roleName,
            @Size(max = 500, message = "角色描述不能超过 500 字")
            String description,
            @NotBlank(message = "数据范围不能为空")
            @Pattern(regexp = "all|org_and_children|org|self|custom", message = "数据范围不合法")
            String dataScope,
            @NotEmpty(message = "至少选择一个权限")
            List<String> permissionCodes,
            List<UUID> orgIds
    ) {
    }

    public record UpdateRequest(
            @NotBlank(message = "角色名称不能为空")
            @Size(max = 120, message = "角色名称不能超过 120 字")
            String roleName,
            @Size(max = 500, message = "角色描述不能超过 500 字")
            String description,
            @NotBlank(message = "数据范围不能为空")
            @Pattern(regexp = "all|org_and_children|org|self|custom", message = "数据范围不合法")
            String dataScope,
            @NotEmpty(message = "至少选择一个权限")
            List<String> permissionCodes,
            List<UUID> orgIds,
            @Pattern(regexp = "active|disabled", message = "角色状态不合法")
            String status
    ) {
    }

    public record RoleResponse(
            UUID id,
            String roleCode,
            String roleName,
            String description,
            String dataScope,
            String status,
            List<String> permissionCodes,
            List<UUID> orgIds,
            Instant createdAt
    ) {
    }

    public record PermissionItem(
            UUID id,
            String permissionCode,
            String permissionName,
            String module,
            String permissionType,
            String description,
            int sortOrder
    ) {
    }

    public record PermissionGroup(String module, List<PermissionItem> items) {
    }
}
