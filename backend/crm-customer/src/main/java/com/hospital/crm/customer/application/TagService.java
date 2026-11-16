package com.hospital.crm.customer.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.TagDtos;
import com.hospital.crm.customer.domain.Tag;
import com.hospital.crm.customer.domain.TagRepository;
import com.hospital.crm.system.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TagService {

    private final TagRepository tagRepository;
    private final AuditService auditService;

    public TagService(TagRepository tagRepository, AuditService auditService) {
        this.tagRepository = tagRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<TagDtos.TagResponse> list(boolean includeDisabled) {
        List<Tag> tags = includeDisabled
                ? tagRepository.findAllByOrderBySortOrderAscTagNameAsc()
                : tagRepository.findAllByStatusOrderBySortOrderAscTagNameAsc("active");
        return tags.stream().map(this::toResponse).toList();
    }

    @Transactional
    public TagDtos.TagResponse create(TagDtos.CreateRequest request) {
        String tagCode = request.tagCode().trim().toUpperCase();
        if (tagRepository.existsByTagCode(tagCode)) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "标签编码已存在");
        }
        if (tagRepository.existsByTagName(request.tagName().trim())) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "标签名称已存在");
        }
        Tag tag = new Tag();
        tag.setTagCode(tagCode);
        tag.setTagName(request.tagName().trim());
        tag.setTagType(request.tagType() == null ? "customer" : request.tagType());
        tag.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        tag.setStatus("active");
        tag = tagRepository.save(tag);
        auditService.record("TAG_CREATE", "TAG", tag.getId().toString(),
                Map.of("tagCode", tag.getTagCode(), "tagName", tag.getTagName()));
        return toResponse(tag);
    }

    @Transactional
    public TagDtos.TagResponse update(UUID tagId, TagDtos.UpdateRequest request) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "标签不存在"));
        String tagName = request.tagName().trim();
        if (!tagName.equals(tag.getTagName()) && tagRepository.existsByTagName(tagName)) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "标签名称已存在");
        }
        tag.setTagName(tagName);
        if (request.tagType() != null) {
            tag.setTagType(request.tagType());
        }
        if (request.sortOrder() != null) {
            tag.setSortOrder(request.sortOrder());
        }
        if (request.status() != null) {
            tag.setStatus(request.status());
        }
        tag = tagRepository.save(tag);
        auditService.record("TAG_UPDATE", "TAG", tagId.toString(), Map.of(
                "tagCode", tag.getTagCode(),
                "tagName", tag.getTagName(),
                "status", tag.getStatus()));
        return toResponse(tag);
    }

    private TagDtos.TagResponse toResponse(Tag tag) {
        return new TagDtos.TagResponse(tag.getId(), tag.getTagCode(), tag.getTagName(),
                tag.getTagType(), tag.getStatus(), tag.getSortOrder(), tag.getCreatedAt());
    }
}
