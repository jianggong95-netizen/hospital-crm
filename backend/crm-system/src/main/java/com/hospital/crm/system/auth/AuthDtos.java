package com.hospital.crm.system.auth;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password
    ) {
    }

    public record RefreshRequest(
            @NotBlank(message = "刷新令牌不能为空") String refreshToken
    ) {
    }

    public record LogoutRequest(
            @NotBlank(message = "刷新令牌不能为空") String refreshToken
    ) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            UserSession user
    ) {
    }

    public record UserSession(
            UUID id,
            String username,
            String displayName,
            UUID orgId,
            String orgName,
            List<String> roles,
            List<String> permissions,
            DataScopeView dataScope
    ) {
    }

    public record DataScopeView(
            boolean all,
            List<UUID> orgIds,
            boolean includeSelf
    ) {
    }
}
