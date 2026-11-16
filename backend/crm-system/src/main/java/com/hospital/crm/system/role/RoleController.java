package com.hospital.crm.system.role;

import com.hospital.crm.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/system")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('system:role:read')")
    public ApiResponse<List<RoleDtos.RoleResponse>> list() {
        return ApiResponse.ok(roleService.list());
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('system:role:write')")
    public ApiResponse<RoleDtos.RoleResponse> create(@Valid @RequestBody RoleDtos.CreateRequest request) {
        return ApiResponse.ok(roleService.create(request));
    }

    @PutMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('system:role:write')")
    public ApiResponse<RoleDtos.RoleResponse> update(@PathVariable UUID roleId,
                                                     @Valid @RequestBody RoleDtos.UpdateRequest request) {
        return ApiResponse.ok(roleService.update(roleId, request));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('system:permission:read')")
    public ApiResponse<List<RoleDtos.PermissionGroup>> permissions() {
        return ApiResponse.ok(roleService.permissions());
    }
}
