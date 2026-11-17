package com.hospital.crm.operation.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.operation.application.SourceChannelService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operation/channels")
public class SourceChannelController {

    private final SourceChannelService channelService;

    public SourceChannelController(SourceChannelService channelService) {
        this.channelService = channelService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:channel:read')")
    public ApiResponse<List<OperationDtos.ChannelView>> list(
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return ApiResponse.ok(channelService.list(includeDisabled));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:channel:write')")
    public ApiResponse<OperationDtos.ChannelView> create(
            @Valid @RequestBody OperationDtos.ChannelCreateRequest request) {
        return ApiResponse.ok(channelService.create(request));
    }

    @PutMapping("/{channelId}/status")
    @PreAuthorize("hasAuthority('crm:channel:write')")
    public ApiResponse<OperationDtos.ChannelView> updateStatus(
            @PathVariable UUID channelId,
            @Valid @RequestBody OperationDtos.ChannelStatusRequest request) {
        return ApiResponse.ok(channelService.updateStatus(channelId, request));
    }
}
