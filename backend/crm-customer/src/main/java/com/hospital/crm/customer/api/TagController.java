package com.hospital.crm.customer.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.customer.application.TagService;
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
@RequestMapping("/api/v1/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:tag:read')")
    public ApiResponse<List<TagDtos.TagResponse>> list(
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return ApiResponse.ok(tagService.list(includeDisabled));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:tag:write')")
    public ApiResponse<TagDtos.TagResponse> create(@Valid @RequestBody TagDtos.CreateRequest request) {
        return ApiResponse.ok(tagService.create(request));
    }

    @PutMapping("/{tagId}")
    @PreAuthorize("hasAuthority('crm:tag:write')")
    public ApiResponse<TagDtos.TagResponse> update(@PathVariable UUID tagId,
                                                   @Valid @RequestBody TagDtos.UpdateRequest request) {
        return ApiResponse.ok(tagService.update(tagId, request));
    }
}
