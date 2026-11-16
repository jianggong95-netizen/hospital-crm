package com.hospital.crm.patient360.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 患者360聚合配置：HIS 查询结果短时缓存与分区默认分页。
 * 缓存仅减少重复的 HIS 调用，不改变 CRM-005 的快照降级语义。
 */
@ConfigurationProperties(prefix = "crm.patient360")
public class Patient360Properties {

    private int cacheTtlSeconds = 60;
    private int cacheMaxEntries = 500;
    private int defaultPageSize = 10;
    private int maxPageSize = 50;

    public int getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(int cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public int getCacheMaxEntries() {
        return cacheMaxEntries;
    }

    public void setCacheMaxEntries(int cacheMaxEntries) {
        this.cacheMaxEntries = cacheMaxEntries;
    }

    public int getDefaultPageSize() {
        return defaultPageSize;
    }

    public void setDefaultPageSize(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }
}
