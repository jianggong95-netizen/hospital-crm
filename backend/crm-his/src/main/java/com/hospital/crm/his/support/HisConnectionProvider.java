package com.hospital.crm.his.support;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HIS 独立只读数据源。CRM 主库连接池不受影响；
 * HIS 未启用或未配置时保持 fail-closed，不建立任何连接。
 */
@Component
public class HisConnectionProvider implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(HisConnectionProvider.class);

    private final HisProperties properties;
    private final AtomicBoolean accountVerified = new AtomicBoolean(false);

    private volatile HikariDataSource dataSource;
    private volatile NamedParameterJdbcTemplate jdbcTemplate;

    public HisConnectionProvider(HisProperties properties) {
        this.properties = properties;
    }

    public boolean configured() {
        return properties.configured();
    }

    public String sourceSystem() {
        return properties.getSourceSystem();
    }

    public NamedParameterJdbcTemplate template() {
        if (!configured()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "HIS 集成未启用或连接未配置，请联系管理员完成 CRM_HIS_URL 等配置");
        }
        NamedParameterJdbcTemplate template = this.jdbcTemplate;
        if (template == null) {
            synchronized (this) {
                template = this.jdbcTemplate;
                if (template == null) {
                    template = createTemplate();
                    this.jdbcTemplate = template;
                }
            }
        }
        verifyReadOnlyAccount(template);
        return template;
    }

    private NamedParameterJdbcTemplate createTemplate() {
        String url = properties.getUrl().trim();
        String driver = properties.getDriverClassName() == null ? "" : properties.getDriverClassName().trim();
        if (driver.toLowerCase(Locale.ROOT).contains("sqlserver")
                && !url.toLowerCase(Locale.ROOT).startsWith("jdbc:sqlserver:")) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "HIS 连接地址必须为 SQL Server 只读视图地址（jdbc:sqlserver:）");
        }

        try {
            HikariConfig config = new HikariConfig();
            config.setPoolName("crm-his-pool");
            config.setJdbcUrl(url);
            config.setUsername(properties.getUsername());
            config.setPassword(properties.getPassword());
            config.setDriverClassName(driver);
            config.setMaximumPoolSize(Math.max(properties.getMaximumPoolSize(), 1));
            config.setMinimumIdle(Math.max(Math.min(properties.getMinimumIdle(), properties.getMaximumPoolSize()), 0));
            config.setConnectionTimeout(Math.max(properties.getConnectionTimeoutMs(), 250));
            config.setReadOnly(true);
            config.setAutoCommit(true);
            config.setInitializationFailTimeout(-1);
            config.setMaxLifetime(30 * 60 * 1000L);
            HikariDataSource created = new HikariDataSource(config);
            this.dataSource = created;

            JdbcTemplate jdbc = new JdbcTemplate(created);
            jdbc.setQueryTimeout(Math.max(properties.getQueryTimeoutSeconds(), 1));
            jdbc.setFetchSize(50);
            log.info("HIS 只读数据源已初始化，pool=crm-his-pool, sourceSystem={}", properties.getSourceSystem());
            return new NamedParameterJdbcTemplate(jdbc);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("HIS 只读数据源初始化失败", ex);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "HIS 数据源初始化失败，请检查连接配置");
        }
    }

    /**
     * 只读账号校验：若账号具备 INSERT/UPDATE/DELETE/ALTER 权限则拒绝查询，避免误用写账号。
     * 校验本身失败（如权限函数不可用）仅记录告警，不阻断后续只读查询。
     */
    private void verifyReadOnlyAccount(NamedParameterJdbcTemplate template) {
        if (!properties.isReadOnlyCheck()
                || !properties.getDriverClassName().toLowerCase(Locale.ROOT).contains("sqlserver")
                || accountVerified.get()) {
            return;
        }
        synchronized (this) {
            if (accountVerified.get()) {
                return;
            }
            String sql = "select has_perms_by_name(db_name(), 'DATABASE', 'INSERT') as can_insert, "
                    + "has_perms_by_name(db_name(), 'DATABASE', 'UPDATE') as can_update, "
                    + "has_perms_by_name(db_name(), 'DATABASE', 'DELETE') as can_delete, "
                    + "has_perms_by_name(db_name(), 'DATABASE', 'ALTER') as can_alter";
            try {
                template.query(sql, resultSet -> {
                    if (resultSet.next()
                            && (resultSet.getInt("can_insert") > 0
                            || resultSet.getInt("can_update") > 0
                            || resultSet.getInt("can_delete") > 0
                            || resultSet.getInt("can_alter") > 0)) {
                        throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                                "HIS 账号具备写权限，已拒绝查询；请配置医院提供的只读账号");
                    }
                    return null;
                });
                accountVerified.set(true);
                log.info("HIS 只读账号校验通过");
            } catch (BusinessException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                accountVerified.set(true);
                log.warn("HIS 只读账号权限校验未完成，将继续执行受 SQL 守卫保护的只读查询：{}",
                        ex.getMessage());
            }
        }
    }

    @Override
    public void destroy() {
        HikariDataSource current = dataSource;
        if (current != null) {
            current.close();
        }
        jdbcTemplate = null;
    }
}
