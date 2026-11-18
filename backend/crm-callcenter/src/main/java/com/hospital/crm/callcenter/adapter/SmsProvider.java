package com.hospital.crm.callcenter.adapter;

import com.hospital.crm.callcenter.domain.MessageOutbox;

/**
 * 短信/消息服务商接口。接入真实通道（阿里云、腾讯云等）后提供实现；
 * 发送失败必须抛出异常，由队列统一记录并按指数退避重试。
 */
public interface SmsProvider {

    /**
     * 发送一条消息。出于合规要求库内不存手机号明文，phone 为脱敏值；
     * 真实通道需从安全渠道（如厂商号码池映射或加密存储）获取明文后发送。
     */
    void send(String phone, String content, MessageOutbox message);
}
