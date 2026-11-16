package com.hospital.crm.system.org;

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
@RequestMapping("/api/v1/system/orgs")
public class OrgController {

    private final OrgService orgService;

    public OrgController(OrgService orgService) {
        this.orgService = orgService;
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:org:read')")
    public ApiResponse<List<OrgDtos.OrgNode>> tree() {
        return ApiResponse.ok(orgService.tree());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:org:write')")
    public ApiResponse<OrgDtos.OrgNode> create(@Valid @RequestBody OrgDtos.CreateRequest request) {
        return ApiResponse.ok(orgService.create(request));
    }

    @PutMapping("/{orgId}")
    @PreAuthorize("hasAuthority('system:org:write')")
    public ApiResponse<OrgDtos.OrgNode> update(@PathVariable UUID orgId,
                                               @Valid @RequestBody OrgDtos.UpdateRequest request) {
        return ApiResponse.ok(orgService.update(orgId, request));
    }
}
