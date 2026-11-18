package com.hospital.crm.callcenter.adapter;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 默认呼叫中心实现：尚未接入厂商时拒绝录音访问。
 * 配置 crm.callcenter.enabled=true 后需提供真实的 CallCenterAdapter 实现替换。
 */
@Component
@ConditionalOnProperty(name = "crm.callcenter.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledCallCenterAdapter implements CallCenterAdapter {

    @Override
    public String resolveRecordingUrl(String recordingKey) {
        throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "呼叫中心尚未接入，无法获取录音");
    }
}
