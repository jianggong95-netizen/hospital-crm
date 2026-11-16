package com.hospital.crm.system.auth;

import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.org.SysOrg;
import com.hospital.crm.system.org.SysOrgRepository;
import com.hospital.crm.system.role.SysRole;
import com.hospital.crm.system.role.SysRoleRepository;
import com.hospital.crm.system.security.BootstrapAdminProperties;
import com.hospital.crm.system.security.PasswordPolicy;
import com.hospital.crm.system.support.MaskingUtils;
import com.hospital.crm.system.user.SysUser;
import com.hospital.crm.system.user.SysUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "crm.security.bootstrap-admin", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final BootstrapAdminProperties properties;
    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysOrgRepository orgRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final Environment environment;

    public BootstrapAdminInitializer(BootstrapAdminProperties properties,
                                     SysUserRepository userRepository,
                                     SysRoleRepository roleRepository,
                                     SysOrgRepository orgRepository,
                                     PasswordEncoder passwordEncoder,
                                     AuditService auditService,
                                     Environment environment) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.orgRepository = orgRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = MaskingUtils.normalizeUsername(properties.username());
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("CRM_BOOTSTRAP_ADMIN_USERNAME 未配置");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            return;
        }
        String password = properties.password();
        if (password == null || password.isBlank()) {
            String message = "系统尚无用户，且未配置 CRM_BOOTSTRAP_ADMIN_PASSWORD，无法初始化管理员";
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException(message);
            }
            log.error(message);
            return;
        }
        PasswordPolicy.validate(password);

        SysRole superAdmin = roleRepository.findByRoleCode("SUPER_ADMIN")
                .orElseThrow(() -> new IllegalStateException("缺少内置角色 SUPER_ADMIN，请检查数据库迁移"));
        SysOrg rootOrg = orgRepository.findByOrgCode("ROOT")
                .orElseThrow(() -> new IllegalStateException("缺少根组织 ROOT，请检查数据库迁移"));

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(properties.displayName() == null || properties.displayName().isBlank()
                ? "系统管理员" : properties.displayName());
        user.setOrgId(rootOrg.getId());
        user.setStatus("active");
        user.setRoles(new HashSet<>(Set.of(superAdmin)));
        userRepository.save(user);

        auditService.record("BOOTSTRAP_ADMIN_CREATED", "USER", user.getId().toString(),
                Map.of("username", username));
        log.info("已初始化内置管理员账号: {}", username);
    }
}
