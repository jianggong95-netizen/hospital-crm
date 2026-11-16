package com.hospital.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Hospital CRM 启动入口。
 *
 * <p>第一版采用模块化单体：领域模块按包隔离，通过应用服务与领域事件交互，
 * 后续确有负载或团队边界时再拆分服务。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CrmApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrmApplication.class, args);
    }
}
