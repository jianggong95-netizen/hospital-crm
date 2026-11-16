package com.hospital.crm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 应用上下文冒烟测试：使用内存 H2 验证 Spring 装配、JPA 映射与安全配置可正常启动。
 *
 * <p>真实 PostgreSQL 的 Flyway 迁移由 {@link DatabaseMigrationTest} 在 CI 中验证。</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:crm;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "crm.security.bootstrap-admin.enabled=false"
})
class CrmApplicationTests {

    @Test
    void contextLoads() {
    }
}
