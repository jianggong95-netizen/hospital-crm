package com.hospital.crm.system.org;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public final class OrgDtos {

    private OrgDtos() {
    }

    public record CreateRequest(
            @NotBlank(message = "组织编码不能为空")
            @Pattern(regexp = "^[A-Za-z0-9_]{2,64}$", message = "组织编码只能包含字母、数字和下划线，长度 2-64")
            String orgCode,
            @NotBlank(message = "组织名称不能为空")
            @Size(max = 120, message = "组织名称不能超过 120 字")
            String orgName,
            @NotNull(message = "上级组织不能为空")
            UUID parentId,
            @Size(max = 32, message = "组织类型不能超过 32 字")
            String orgType,
            @Min(value = 0, message = "排序值不能小于 0")
            Integer sortOrder
    ) {
    }

    public record UpdateRequest(
            @NotBlank(message = "组织名称不能为空")
            @Size(max = 120, message = "组织名称不能超过 120 字")
            String orgName,
            @Size(max = 32, message = "组织类型不能超过 32 字")
            String orgType,
            @Min(value = 0, message = "排序值不能小于 0")
            Integer sortOrder,
            @Pattern(regexp = "active|disabled", message = "组织状态不合法")
            String status
    ) {
    }

    public record OrgNode(
            UUID id,
            String orgCode,
            String orgName,
            UUID parentId,
            String orgType,
            int sortOrder,
            String status,
            List<OrgNode> children
    ) {
    }
}
