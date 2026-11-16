package com.hospital.crm.system.auth;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.CurrentUser;
import com.hospital.crm.system.security.DataScope;
import com.hospital.crm.system.security.DataScopeService;
import com.hospital.crm.system.security.JwtService;
import com.hospital.crm.system.security.LoginPolicyProperties;
import com.hospital.crm.system.security.RefreshTokenService;
import com.hospital.crm.system.security.SysRefreshToken;
import com.hospital.crm.system.security.SysRefreshTokenRepository;
import com.hospital.crm.system.security.UserPrincipal;
import com.hospital.crm.system.security.UserPrincipalFactory;
import com.hospital.crm.system.support.MaskingUtils;
import com.hospital.crm.system.user.SysUser;
import com.hospital.crm.system.user.SysUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private final SysUserRepository userRepository;
    private final SysRefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserPrincipalFactory principalFactory;
    private final DataScopeService dataScopeService;
    private final LoginPolicyProperties loginPolicy;
    private final AuditService auditService;

    public AuthService(SysUserRepository userRepository,
                       SysRefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       UserPrincipalFactory principalFactory,
                       DataScopeService dataScopeService,
                       LoginPolicyProperties loginPolicy,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.principalFactory = principalFactory;
        this.dataScopeService = dataScopeService;
        this.loginPolicy = loginPolicy;
        this.auditService = auditService;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request, HttpServletRequest httpRequest) {
        String username = MaskingUtils.normalizeUsername(request.username());
        SysUser user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            auditService.recordNow("AUTH_LOGIN_FAILED", "AUTH", null,
                    Map.of("username", safeUsername(username), "reason", "user_not_found"));
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "用户名或密码错误");
        }
        if (!"active".equals(user.getStatus())) {
            auditService.recordNow("AUTH_LOGIN_FAILED", "AUTH", user.getId().toString(),
                    Map.of("username", safeUsername(username), "reason", "disabled"));
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "账号已停用，请联系系统管理员");
        }
        if (user.isLocked()) {
            auditService.recordNow("AUTH_LOGIN_FAILED", "AUTH", user.getId().toString(),
                    Map.of("username", safeUsername(username), "reason", "locked"));
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "账号已锁定，请稍后重试");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int failedCount = user.getFailedLoginCount() + 1;
            user.setFailedLoginCount(failedCount);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("username", safeUsername(username));
            detail.put("reason", "bad_credentials");
            detail.put("failedCount", failedCount);
            if (failedCount >= loginPolicy.maxFailedAttempts()) {
                user.setLockedUntil(Instant.now().plus(loginPolicy.lockMinutes(), ChronoUnit.MINUTES));
                user.setFailedLoginCount(0);
                detail.put("locked", true);
            }
            userRepository.save(user);
            auditService.recordNow("AUTH_LOGIN_FAILED", "AUTH", user.getId().toString(), detail);
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "用户名或密码错误");
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        UserPrincipal principal = principalFactory.create(user);
        String accessToken = jwtService.issueAccessToken(principal);
        String refreshToken = refreshTokenService.issue(user.getId(), httpRequest);
        auditService.recordNow("AUTH_LOGIN_SUCCESS", "AUTH", user.getId().toString(),
                Map.of("username", safeUsername(username)));

        return new AuthDtos.TokenResponse(accessToken, refreshToken, "Bearer",
                jwtService.accessTokenSeconds(), toSession(principal));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public AuthDtos.TokenResponse refresh(AuthDtos.RefreshRequest request, HttpServletRequest httpRequest) {
        SysRefreshToken token = refreshTokenService.find(request.refreshToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED, "刷新令牌无效，请重新登录"));

        if (token.getRevokedAt() != null) {
            if (token.getReplacedByHash() != null) {
                refreshTokenService.revokeAll(token.getUserId());
                auditService.recordNow("AUTH_REFRESH_REUSE", "AUTH", token.getUserId().toString(),
                        Map.of("reason", "revoked_token_reuse"));
            }
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "刷新令牌已失效，请重新登录");
        }
        if (!token.getExpiresAt().isAfter(Instant.now())) {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
            auditService.recordNow("AUTH_REFRESH_FAILED", "AUTH", token.getUserId().toString(),
                    Map.of("reason", "expired"));
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "刷新令牌已过期，请重新登录");
        }

        SysUser user = userRepository.findWithRolesById(token.getUserId()).orElse(null);
        if (user == null || !user.isEnabled()) {
            refreshTokenService.revokeAll(token.getUserId());
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "账号不可用，请重新登录");
        }

        String newRefreshToken = refreshTokenService.issue(user.getId(), httpRequest);
        token.setRevokedAt(Instant.now());
        token.setReplacedByHash(refreshTokenService.hash(newRefreshToken));
        refreshTokenRepository.save(token);

        UserPrincipal principal = principalFactory.create(user);
        String accessToken = jwtService.issueAccessToken(principal);
        auditService.recordNow("AUTH_REFRESH", "AUTH", user.getId().toString(), Map.of());

        return new AuthDtos.TokenResponse(accessToken, newRefreshToken, "Bearer",
                jwtService.accessTokenSeconds(), toSession(principal));
    }

    @Transactional
    public void logout(AuthDtos.LogoutRequest request) {
        refreshTokenService.revoke(request.refreshToken());
        auditService.record("AUTH_LOGOUT", "AUTH", CurrentUser.id().toString(), Map.of());
    }

    public AuthDtos.UserSession me() {
        return toSession(CurrentUser.principal());
    }

    private AuthDtos.UserSession toSession(UserPrincipal principal) {
        DataScope dataScope = dataScopeService.resolve(principal);
        return new AuthDtos.UserSession(
                principal.userId(),
                principal.username(),
                principal.displayName(),
                principal.orgId(),
                principal.orgName(),
                principal.roleCodes().stream().sorted().toList(),
                principal.permissions().stream().sorted().toList(),
                new AuthDtos.DataScopeView(dataScope.all(),
                        dataScope.orgIds().stream().sorted().toList(),
                        dataScope.includeSelf())
        );
    }

    private String safeUsername(String username) {
        return username == null ? "" : username;
    }
}
