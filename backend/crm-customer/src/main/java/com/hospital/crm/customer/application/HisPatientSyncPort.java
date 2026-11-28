package com.hospital.crm.customer.application;

import com.hospital.crm.customer.api.CustomerDtos;

/**
 * HIS 患者全量同步端口：由 crm-his 模块实现（依赖方向为 crm-his -> crm-customer），
 * 客户中心端点通过该端口触发同步，避免模块间循环依赖。
 */
public interface HisPatientSyncPort {

    /**
     * 分页拉取 HIS 患者并同步到 CRM 客户档案（幂等，可重复执行）。
     */
    CustomerDtos.HisSyncResult syncFromHis();
}
