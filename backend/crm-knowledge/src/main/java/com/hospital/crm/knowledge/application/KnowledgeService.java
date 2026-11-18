package com.hospital.crm.knowledge.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.knowledge.api.KnowledgeDtos;
import com.hospital.crm.knowledge.domain.KbCategory;
import com.hospital.crm.knowledge.domain.KbCategoryRepository;
import com.hospital.crm.knowledge.domain.KbChunk;
import com.hospital.crm.knowledge.domain.KbChunkRepository;
import com.hospital.crm.knowledge.domain.KbDocument;
import com.hospital.crm.knowledge.domain.KbDocumentRepository;
import com.hospital.crm.knowledge.domain.KbReview;
import com.hospital.crm.knowledge.domain.KbReviewRepository;
import com.hospital.crm.knowledge.domain.KbVersion;
import com.hospital.crm.knowledge.domain.KbVersionRepository;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.CurrentUser;
import com.hospital.crm.system.user.SysUser;
import com.hospital.crm.system.user.SysUserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * AI 知识库服务：分类、文档、上传解析切片、审核、版本与下架。
 *
 * 审核规则：仅草稿可提交送审；送审前扫描切片，禁止出现身份证号/手机号等患者敏感标识；
 * 审核通过后草稿切片发布为新版本，下架知识不再参与 RAG 检索。
 */
@Service
public class KnowledgeService {

    private static final String RESOURCE = "CRM_KB";

    private static final Pattern ID_NUMBER = Pattern.compile("\\d{17}[\\dXx]");
    private static final Pattern MOBILE = Pattern.compile("1[3-9]\\d{9}");

    private static final Set<String> UPLOAD_SUFFIXES = Set.of("txt", "md");

    private final KbCategoryRepository categoryRepository;
    private final KbDocumentRepository documentRepository;
    private final KbChunkRepository chunkRepository;
    private final KbVersionRepository versionRepository;
    private final KbReviewRepository reviewRepository;
    private final SysUserRepository userRepository;
    private final AuditService auditService;

    public KnowledgeService(KbCategoryRepository categoryRepository,
                            KbDocumentRepository documentRepository,
                            KbChunkRepository chunkRepository,
                            KbVersionRepository versionRepository,
                            KbReviewRepository reviewRepository,
                            SysUserRepository userRepository,
                            AuditService auditService) {
        this.categoryRepository = categoryRepository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.versionRepository = versionRepository;
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // 分类
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<KnowledgeDtos.CategoryView> listCategories() {
        return categoryRepository.findAllByOrderBySortOrderAscCreatedAtAsc().stream()
                .map(this::toCategoryView)
                .toList();
    }

    @Transactional
    public KnowledgeDtos.CategoryView createCategory(KnowledgeDtos.CategoryRequest request) {
        if (request.code() == null || request.code().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分类编码不能为空");
        }
        if (categoryRepository.existsByCode(request.code())) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "分类编码已存在");
        }
        KbCategory category = new KbCategory();
        category.setCode(request.code());
        category.setName(request.name());
        category.setSortOrder(request.sortOrder() == null ? 100 : request.sortOrder());
        category.setStatus(request.status() == null || request.status().isBlank() ? "active" : request.status());
        categoryRepository.save(category);
        auditService.record("KB_CATEGORY_CREATE", RESOURCE, category.getCode(),
                Map.of("name", category.getName()));
        return toCategoryView(category);
    }

    @Transactional
    public KnowledgeDtos.CategoryView updateCategory(UUID categoryId, KnowledgeDtos.CategoryRequest request) {
        KbCategory category = requireCategory(categoryId);
        category.setName(request.name());
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        if (request.status() != null && !request.status().isBlank()) {
            category.setStatus(request.status());
        }
        categoryRepository.save(category);
        auditService.record("KB_CATEGORY_UPDATE", RESOURCE, category.getCode(), Map.of());
        return toCategoryView(category);
    }

    // ------------------------------------------------------------------
    // 文档列表与详情
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public PageResponse<KnowledgeDtos.DocumentView> listDocuments(String keyword, UUID categoryId,
                                                                  String status, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<KbDocument> result = documentRepository.findAll(
                buildSpecification(keyword, categoryId, status), pageable);
        return PageResponse.of(toViews(result.getContent()), result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public KnowledgeDtos.DocumentDetail detail(UUID documentId) {
        KbDocument document = requireDocument(documentId);
        int shownVersion = "DRAFT".equals(document.getStatus()) || "REVIEWING".equals(document.getStatus())
                ? document.getCurrentVersion() + 1
                : Math.max(document.getCurrentVersion(), 1);
        List<KbChunk> chunks = chunkRepository
                .findByDocumentIdAndDocVersionOrderByChunkIndexAsc(documentId, shownVersion);
        List<KbVersion> versions = versionRepository.findByDocumentIdOrderByVersionNoDesc(documentId);
        List<KbReview> reviews = reviewRepository.findByDocumentIdOrderByCreatedAtDesc(documentId);

        Set<UUID> userIds = new HashSet<>();
        if (document.getOwnerId() != null) {
            userIds.add(document.getOwnerId());
        }
        versions.forEach(version -> {
            if (version.getCreatedBy() != null) {
                userIds.add(version.getCreatedBy());
            }
        });
        reviews.forEach(review -> {
            if (review.getReviewerId() != null) {
                userIds.add(review.getReviewerId());
            }
        });
        Map<UUID, String> names = userNames(userIds);

        KnowledgeDtos.DocumentView view = toViews(List.of(document)).get(0);
        List<KnowledgeDtos.ChunkView> chunkViews = chunks.stream()
                .map(chunk -> new KnowledgeDtos.ChunkView(chunk.getId(), chunk.getDocVersion(),
                        chunk.getChunkIndex(), chunk.getContent(), chunk.getCharCount(), chunk.getCreatedAt()))
                .toList();
        List<KnowledgeDtos.VersionView> versionViews = versions.stream()
                .map(version -> new KnowledgeDtos.VersionView(version.getId(), version.getVersionNo(),
                        version.getTitle(), version.getSummary(), version.getChunkCount(),
                        version.getChangeNote(), version.getCreatedBy(),
                        names.get(version.getCreatedBy()), version.getCreatedAt()))
                .toList();
        List<KnowledgeDtos.ReviewView> reviewViews = reviews.stream()
                .map(review -> new KnowledgeDtos.ReviewView(review.getId(), review.getVersionNo(),
                        review.getAction(), review.getReviewerId(), names.get(review.getReviewerId()),
                        review.getComment(), review.getCreatedAt()))
                .toList();
        return new KnowledgeDtos.DocumentDetail(view, chunkViews, versionViews, reviewViews);
    }

    // ------------------------------------------------------------------
    // 创建：手工录入与文件上传
    // ------------------------------------------------------------------
    @Transactional
    public KnowledgeDtos.DocumentView createDocument(KnowledgeDtos.CreateDocumentRequest request) {
        validateCategory(request.categoryId());
        List<String> chunks = TextChunker.chunk(request.content());
        if (chunks.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "知识内容为空或无法解析");
        }
        KbDocument document = newDocument(request.title(), request.categoryId(), request.summary(),
                "manual", null, null, null);
        saveDraftChunks(document, chunks);
        auditService.record("KB_DOCUMENT_CREATE", RESOURCE, document.getDocNo(),
                Map.of("chunks", chunks.size(), "source", "manual"));
        return toViews(List.of(document)).get(0);
    }

    @Transactional
    public KnowledgeDtos.DocumentView upload(MultipartFile file, String title, UUID categoryId, String summary) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传文件不能为空");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String suffix = suffixOf(originalName);
        if (!UPLOAD_SUFFIXES.contains(suffix)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "仅支持上传 txt/markdown 知识文件");
        }
        String text;
        try {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件读取失败");
        }
        List<String> chunks = TextChunker.chunk(text);
        if (chunks.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件解析结果为空");
        }
        validateCategory(categoryId);
        String docTitle = title == null || title.isBlank()
                ? originalName.substring(0, Math.min(originalName.length(), originalName.lastIndexOf('.')))
                : title;
        KbDocument document = newDocument(docTitle, categoryId, summary, "file",
                originalName, file.getSize(), suffix);
        saveDraftChunks(document, chunks);
        auditService.record("KB_DOCUMENT_UPLOAD", RESOURCE, document.getDocNo(),
                Map.of("fileName", originalName, "chunks", chunks.size()));
        return toViews(List.of(document)).get(0);
    }

    @Transactional
    public KnowledgeDtos.DocumentView updateContent(UUID documentId, KnowledgeDtos.UpdateContentRequest request) {
        KbDocument document = requireDocument(documentId);
        if (!Set.of("DRAFT", "OFFLINE").contains(document.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅草稿或已下架知识可修改内容");
        }
        List<String> chunks = TextChunker.chunk(request.content());
        if (chunks.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "知识内容为空或无法解析");
        }
        saveDraftChunks(document, chunks);
        documentRepository.save(document);
        auditService.record("KB_DOCUMENT_CONTENT", RESOURCE, document.getDocNo(),
                Map.of("chunks", chunks.size(),
                        "changeNote", request.changeNote() == null ? "" : request.changeNote()));
        return toViews(List.of(document)).get(0);
    }

    @Transactional
    public KnowledgeDtos.DocumentView updateMeta(UUID documentId, KnowledgeDtos.UpdateMetaRequest request) {
        KbDocument document = requireDocument(documentId);
        if (!Set.of("DRAFT", "OFFLINE").contains(document.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅草稿或已下架知识可修改基本信息");
        }
        validateCategory(request.categoryId());
        document.setTitle(request.title());
        document.setCategoryId(request.categoryId());
        document.setSummary(request.summary());
        documentRepository.save(document);
        auditService.record("KB_DOCUMENT_UPDATE", RESOURCE, document.getDocNo(), Map.of());
        return toViews(List.of(document)).get(0);
    }

    // ------------------------------------------------------------------
    // 审核流：提交、通过、驳回、下架
    // ------------------------------------------------------------------
    @Transactional
    public KnowledgeDtos.DocumentView submit(UUID documentId) {
        KbDocument document = requireDocument(documentId);
        if (!"DRAFT".equals(document.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅草稿知识可提交审核");
        }
        int draftVersion = document.getCurrentVersion() + 1;
        List<KbChunk> chunks = chunkRepository
                .findByDocumentIdAndDocVersionOrderByChunkIndexAsc(documentId, draftVersion);
        if (chunks.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "知识切片为空，无法提交审核");
        }
        assertNoSensitiveData(chunks);
        Instant now = Instant.now();
        if (documentRepository.submitDocument(documentId, now) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "知识状态已变化，提交失败");
        }
        KbDocument refreshed = requireDocument(documentId);
        recordReview(refreshed, "SUBMIT", null);
        auditService.record("KB_DOCUMENT_SUBMIT", RESOURCE, refreshed.getDocNo(),
                Map.of("draftVersion", draftVersion));
        return toViews(List.of(refreshed)).get(0);
    }

    @Transactional
    public KnowledgeDtos.DocumentView approve(UUID documentId, KnowledgeDtos.ReviewActionRequest request) {
        KbDocument document = requireDocument(documentId);
        if (!"REVIEWING".equals(document.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅待审核知识可通过");
        }
        int oldVersion = document.getCurrentVersion();
        int newVersion = oldVersion + 1;
        Instant now = Instant.now();
        if (documentRepository.approveDocument(documentId, oldVersion, newVersion, now) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "知识已被其他审核人处理，请刷新后重试");
        }
        KbDocument published = requireDocument(documentId);
        long chunkCount = chunkRepository.countByDocumentIdAndDocVersion(documentId, newVersion);
        KbVersion version = new KbVersion();
        version.setDocumentId(documentId);
        version.setVersionNo(newVersion);
        version.setTitle(published.getTitle());
        version.setSummary(published.getSummary());
        version.setChunkCount((int) chunkCount);
        version.setChangeNote(request == null ? null : request.comment());
        version.setCreatedBy(currentUserId());
        versionRepository.save(version);
        recordReview(published, "APPROVE", request == null ? null : request.comment());
        auditService.record("KB_DOCUMENT_APPROVE", RESOURCE, published.getDocNo(),
                Map.of("version", newVersion));
        return toViews(List.of(published)).get(0);
    }

    @Transactional
    public KnowledgeDtos.DocumentView reject(UUID documentId, KnowledgeDtos.ReviewActionRequest request) {
        KbDocument document = requireDocument(documentId);
        if (!"REVIEWING".equals(document.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅待审核知识可驳回");
        }
        Instant now = Instant.now();
        if (documentRepository.rejectDocument(documentId, now) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "知识状态已变化，驳回失败");
        }
        KbDocument refreshed = requireDocument(documentId);
        recordReview(refreshed, "REJECT", request == null ? null : request.comment());
        auditService.record("KB_DOCUMENT_REJECT", RESOURCE, refreshed.getDocNo(),
                Map.of("comment", request == null || request.comment() == null ? "" : request.comment()));
        return toViews(List.of(refreshed)).get(0);
    }

    @Transactional
    public KnowledgeDtos.DocumentView offline(UUID documentId, KnowledgeDtos.OfflineRequest request) {
        KbDocument document = requireDocument(documentId);
        if (!"PUBLISHED".equals(document.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅已发布知识可下架");
        }
        Instant now = Instant.now();
        if (documentRepository.offlineDocument(documentId, request.reason(), now) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "知识状态已变化，下架失败");
        }
        KbDocument refreshed = requireDocument(documentId);
        recordReview(refreshed, "OFFLINE", request.reason());
        auditService.record("KB_DOCUMENT_OFFLINE", RESOURCE, refreshed.getDocNo(),
                Map.of("reason", request.reason()));
        return toViews(List.of(refreshed)).get(0);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------
    private KbDocument newDocument(String title, UUID categoryId, String summary,
                                   String sourceType, String fileName, Long fileSize, String fileType) {
        KbDocument document = new KbDocument();
        document.setDocNo(String.format("KB%d", documentRepository.nextDocNo()));
        document.setTitle(title);
        document.setCategoryId(categoryId);
        document.setSummary(summary);
        document.setSourceType(sourceType);
        document.setFileName(fileName);
        document.setFileSize(fileSize);
        document.setFileType(fileType);
        document.setStatus("DRAFT");
        document.setCurrentVersion(0);
        document.setOwnerId(currentUserId());
        return documentRepository.save(document);
    }

    private void saveDraftChunks(KbDocument document, List<String> chunks) {
        int draftVersion = document.getCurrentVersion() + 1;
        chunkRepository.deleteByDocumentIdAndDocVersion(document.getId(), draftVersion);
        List<KbChunk> entities = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            KbChunk chunk = new KbChunk();
            chunk.setDocumentId(document.getId());
            chunk.setDocVersion(draftVersion);
            chunk.setChunkIndex(index + 1);
            chunk.setContent(chunks.get(index));
            chunk.setCharCount(chunks.get(index).length());
            entities.add(chunk);
        }
        chunkRepository.saveAll(entities);
    }

    private void assertNoSensitiveData(List<KbChunk> chunks) {
        for (KbChunk chunk : chunks) {
            String content = chunk.getContent();
            if (ID_NUMBER.matcher(content).find() || MOBILE.matcher(content).find()) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                        "知识内容包含患者敏感标识（证件号/手机号），请脱敏后再提交");
            }
        }
    }

    private void recordReview(KbDocument document, String action, String comment) {
        KbReview review = new KbReview();
        review.setDocumentId(document.getId());
        review.setVersionNo("APPROVE".equals(action)
                ? document.getCurrentVersion()
                : document.getCurrentVersion() + 1);
        review.setAction(action);
        review.setReviewerId(currentUserId());
        review.setComment(comment);
        reviewRepository.save(review);
    }

    private void validateCategory(UUID categoryId) {
        if (categoryId == null) {
            return;
        }
        requireCategory(categoryId);
    }

    private KbCategory requireCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "分类不存在"));
    }

    private KbDocument requireDocument(UUID documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "知识文档不存在"));
    }

    private List<KnowledgeDtos.DocumentView> toViews(List<KbDocument> documents) {
        if (documents.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> categoryNames = new HashMap<>();
        Set<UUID> categoryIds = new HashSet<>();
        documents.forEach(document -> {
            if (document.getCategoryId() != null) {
                categoryIds.add(document.getCategoryId());
            }
        });
        categoryRepository.findAllById(categoryIds)
                .forEach(category -> categoryNames.put(category.getId(), category.getName()));
        Set<UUID> userIds = new HashSet<>();
        documents.forEach(document -> {
            if (document.getOwnerId() != null) {
                userIds.add(document.getOwnerId());
            }
        });
        Map<UUID, String> names = userNames(userIds);
        return documents.stream().map(document -> new KnowledgeDtos.DocumentView(
                document.getId(),
                document.getDocNo(),
                document.getTitle(),
                document.getCategoryId(),
                categoryNames.get(document.getCategoryId()),
                document.getSummary(),
                document.getSourceType(),
                document.getFileName(),
                document.getFileSize(),
                document.getFileType(),
                document.getStatus(),
                document.getCurrentVersion(),
                (int) chunkRepository.countByDocumentIdAndDocVersion(document.getId(),
                        document.getCurrentVersion() + 1),
                document.getOwnerId(),
                names.get(document.getOwnerId()),
                document.getPublishedAt(),
                document.getOfflineAt(),
                document.getOfflineReason(),
                document.getCreatedAt(),
                document.getUpdatedAt())).toList();
    }

    private Map<UUID, String> userNames(Set<UUID> userIds) {
        Map<UUID, String> names = new HashMap<>();
        if (userIds.isEmpty()) {
            return names;
        }
        userRepository.findAllById(userIds).forEach(user -> names.put(user.getId(), displayName(user)));
        return names;
    }

    private String displayName(SysUser user) {
        return user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getUsername() : user.getDisplayName();
    }

    private KnowledgeDtos.CategoryView toCategoryView(KbCategory category) {
        return new KnowledgeDtos.CategoryView(category.getId(), category.getCode(), category.getName(),
                category.getSortOrder(), category.getStatus(), category.getCreatedAt());
    }

    private static String suffixOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private UUID currentUserId() {
        try {
            return CurrentUser.id();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Specification<KbDocument> buildSpecification(String keyword, UUID categoryId, String status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("docNo")), like)));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("categoryId"), categoryId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
