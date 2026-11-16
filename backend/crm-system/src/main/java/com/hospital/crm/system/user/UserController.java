package com.hospital.crm.system.user;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/system/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:user:read')")
    public ApiResponse<PageResponse<UserDtos.UserResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID orgId,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(userService.list(page, pageSize, keyword, orgId, status));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:user:write')")
    public ApiResponse<UserDtos.UserResponse> create(@Valid @RequestBody UserDtos.CreateRequest request) {
        return ApiResponse.ok(userService.create(request));
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasAuthority('system:user:write')")
    public ApiResponse<UserDtos.UserResponse> update(@PathVariable UUID userId,
                                                     @Valid @RequestBody UserDtos.UpdateRequest request) {
        return ApiResponse.ok(userService.update(userId, request));
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAuthority('system:user:write')")
    public ApiResponse<Void> changeStatus(@PathVariable UUID userId,
                                          @Valid @RequestBody UserDtos.StatusRequest request) {
        userService.changeStatus(userId, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{userId}/reset-password")
    @PreAuthorize("hasAuthority('system:user:write')")
    public ApiResponse<Void> resetPassword(@PathVariable UUID userId,
                                           @Valid @RequestBody UserDtos.PasswordResetRequest request) {
        userService.resetPassword(userId, request);
        return ApiResponse.ok(null);
    }
}
