package com.hospital.crm.system.security;

import com.hospital.crm.system.user.SysUser;
import com.hospital.crm.system.user.SysUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final SysUserRepository userRepository;
    private final UserPrincipalFactory principalFactory;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   SysUserRepository userRepository,
                                   UserPrincipalFactory principalFactory) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.principalFactory = principalFactory;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = authorization.substring(7).trim();
            try {
                UUID userId = jwtService.parseUserId(token);
                SysUser user = userRepository.findWithRolesById(userId).orElse(null);
                if (user != null && user.isEnabled()) {
                    UserPrincipal principal = principalFactory.create(user);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception exception) {
                SecurityContextHolder.clearContext();
                request.setAttribute("crm.auth.error", exception.getClass().getSimpleName());
            }
        }
        filterChain.doFilter(request, response);
    }
}
