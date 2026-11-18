package com.hospital.crm.callcenter.adapter;

/**
 * 呼叫中心厂商适配接口。接入真实厂商（如天润、容联）后提供实现，
 * 负责将录音键换取为可访问的地址（签名 URL 等）。
 */
public interface CallCenterAdapter {

    /**
     * 解析录音访问地址；厂商不可用或未接入时抛出 BusinessException。
     */
    String resolveRecordingUrl(String recordingKey);
}
