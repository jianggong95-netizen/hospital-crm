package com.hospital.crm.callcenter.adapter;

import com.hospital.crm.callcenter.domain.MessageOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 默认消息通道：仅记录日志模拟发送成功，用于尚未接入短信服务商的环境。
 * 配置 crm.sms.provider 为真实通道值后替换为该通道的 SmsProvider 实现，重试机制不变。
 */
@Component
@ConditionalOnProperty(name = "crm.sms.provider", havingValue = "simulated", matchIfMissing = true)
public class SimulatedSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(SimulatedSmsProvider.class);

    @Override
    public void send(String phone, String content, MessageOutbox message) {
        log.info("模拟发送消息 channel={} msgNo={} recipient={} contentLength={}",
                message.getChannel(), message.getMsgNo(), phone,
                content == null ? 0 : content.length());
    }
}
