package com.hospital.crm.operation.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.operation.api.OperationDtos;
import com.hospital.crm.operation.domain.SourceChannel;
import com.hospital.crm.operation.domain.SourceChannelRepository;
import com.hospital.crm.system.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class SourceChannelService {

    private final SourceChannelRepository channelRepository;
    private final AuditService auditService;

    public SourceChannelService(SourceChannelRepository channelRepository, AuditService auditService) {
        this.channelRepository = channelRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<OperationDtos.ChannelView> list(boolean includeDisabled) {
        List<SourceChannel> channels = includeDisabled
                ? channelRepository.findAllByOrderByChannelCodeAsc()
                : channelRepository.findAllByStatusOrderByChannelCodeAsc("active");
        return channels.stream().map(this::toView).toList();
    }

    @Transactional
    public OperationDtos.ChannelView create(OperationDtos.ChannelCreateRequest request) {
        String code = request.channelCode().trim().toUpperCase();
        if (channelRepository.existsByChannelCode(code)) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "渠道编码已存在");
        }
        SourceChannel channel = new SourceChannel();
        channel.setChannelCode(code);
        channel.setChannelName(request.channelName().trim());
        channel.setChannelType(request.channelType() == null ? "other" : request.channelType());
        channel.setStatus("active");
        channel.setRemark(blankToNull(request.remark()));
        channel = channelRepository.save(channel);
        auditService.record("CHANNEL_CREATE", "SOURCE_CHANNEL", channel.getId().toString(),
                Map.of("channelCode", channel.getChannelCode(), "channelType", channel.getChannelType()));
        return toView(channel);
    }

    @Transactional
    public OperationDtos.ChannelView updateStatus(java.util.UUID channelId, OperationDtos.ChannelStatusRequest request) {
        SourceChannel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "来源渠道不存在"));
        channel.setStatus(request.status());
        channel = channelRepository.save(channel);
        auditService.record("CHANNEL_STATUS_CHANGE", "SOURCE_CHANNEL", channelId.toString(),
                Map.of("channelCode", channel.getChannelCode(), "status", channel.getStatus()));
        return toView(channel);
    }

    private OperationDtos.ChannelView toView(SourceChannel channel) {
        return new OperationDtos.ChannelView(
                channel.getId(),
                channel.getChannelCode(),
                channel.getChannelName(),
                channel.getChannelType(),
                channel.getStatus(),
                channel.getRemark());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
