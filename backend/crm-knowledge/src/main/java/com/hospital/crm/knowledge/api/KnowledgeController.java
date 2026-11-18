package com.hospital.crm.knowledge.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.knowledge.application.AiAnswerService;
import com.hospital.crm.knowledge.application.KnowledgeService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * AI 知识库与数字人接口：分类、文档、上传解析、审核、版本、下架、RAG 检索与问答追溯。
 */
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final AiAnswerService aiAnswerService;

    public KnowledgeController(KnowledgeService knowledgeService, AiAnswerService aiAnswerService) {
        this.knowledgeService = knowledgeService;
        this.aiAnswerService = aiAnswerService;
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('crm:knowledge:read')")
    public ApiResponse<List<KnowledgeDtos.CategoryView>> categories() {
        return ApiResponse.ok(knowledgeService.listCategories());
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('crm:knowledge:write')")
    public ApiResponse<KnowledgeDtos.CategoryView> createCategory(
            @Valid @RequestBody KnowledgeDtos.CategoryRequest request) {
        return ApiResponse.ok(knowledgeService.createCategory(request));
    }

    @PutMapping("/categories/{categoryId}")
    @PreAuthorize("hasAuthority('crm:knowledge:write')")
    public ApiResponse<KnowledgeDtos.CategoryView> updateCategory(
            @PathVariable UUID categoryId,
            @Valid @RequestBody KnowledgeDtos.CategoryRequest request) {
        return ApiResponse.ok(knowledgeService.updateCategory(categoryId, request));
    }

    @GetMapping("/documents")
    @PreAuthorize("hasAuthority('crm:knowledge:read')")
    public ApiResponse<PageResponse<KnowledgeDtos.DocumentView>> listDocuments(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(knowledgeService.listDocuments(keyword, categoryId, status, page, pageSize));
    }

    @GetMapping("/documents/{documentId}")
    @PreAuthorize("hasAuthority('crm:knowledge:read')")
    public ApiResponse<KnowledgeDtos.DocumentDetail> detail(@PathVariable UUID documentId) {
        return ApiResponse.ok(knowledgeService.detail(documentId));
    }

    @PostMapping("/documents")
    @PreAuthorize("hasAuthority('crm:knowledge:write')")
    public ApiResponse<KnowledgeDtos.DocumentView> createDocument(
            @Valid @RequestBody KnowledgeDtos.CreateDocumentRequest request) {
        return ApiResponse.ok(knowledgeService.createDocument(request));
    }

    @PostMapping("/documents/upload")
    @PreAuthorize("hasAuthority('crm:knowledge:write')")
    public ApiResponse<KnowledgeDtos.DocumentView> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String summary) {
        return ApiResponse.ok(knowledgeService.upload(file, title, categoryId, summary));
    }

    @PutMapping("/documents/{documentId}")
    @PreAuthorize("hasAuthority('crm:knowledge:write')")
    public ApiResponse<KnowledgeDtos.DocumentView> updateMeta(
            @PathVariable UUID documentId,
            @Valid @RequestBody KnowledgeDtos.UpdateMetaRequest request) {
        return ApiResponse.ok(knowledgeService.updateMeta(documentId, request));
    }

    @PutMapping("/documents/{documentId}/content")
    @PreAuthorize("hasAuthority('crm:knowledge:write')")
    public ApiResponse<KnowledgeDtos.DocumentView> updateContent(
            @PathVariable UUID documentId,
            @Valid @RequestBody KnowledgeDtos.UpdateContentRequest request) {
        return ApiResponse.ok(knowledgeService.updateContent(documentId, request));
    }

    @PostMapping("/documents/{documentId}/submit")
    @PreAuthorize("hasAuthority('crm:knowledge:write')")
    public ApiResponse<KnowledgeDtos.DocumentView> submit(@PathVariable UUID documentId) {
        return ApiResponse.ok(knowledgeService.submit(documentId));
    }

    @PostMapping("/documents/{documentId}/approve")
    @PreAuthorize("hasAuthority('crm:knowledge:review')")
    public ApiResponse<KnowledgeDtos.DocumentView> approve(
            @PathVariable UUID documentId,
            @Valid @RequestBody(required = false) KnowledgeDtos.ReviewActionRequest request) {
        return ApiResponse.ok(knowledgeService.approve(documentId,
                request == null ? new KnowledgeDtos.ReviewActionRequest(null) : request));
    }

    @PostMapping("/documents/{documentId}/reject")
    @PreAuthorize("hasAuthority('crm:knowledge:review')")
    public ApiResponse<KnowledgeDtos.DocumentView> reject(
            @PathVariable UUID documentId,
            @Valid @RequestBody KnowledgeDtos.ReviewActionRequest request) {
        return ApiResponse.ok(knowledgeService.reject(documentId, request));
    }

    @PostMapping("/documents/{documentId}/offline")
    @PreAuthorize("hasAuthority('crm:knowledge:review')")
    public ApiResponse<KnowledgeDtos.DocumentView> offline(
            @PathVariable UUID documentId,
            @Valid @RequestBody KnowledgeDtos.OfflineRequest request) {
        return ApiResponse.ok(knowledgeService.offline(documentId, request));
    }

    @PostMapping("/rag/ask")
    @PreAuthorize("hasAuthority('crm:knowledge:read')")
    public ApiResponse<KnowledgeDtos.AskResult> ask(@Valid @RequestBody KnowledgeDtos.AskRequest request) {
        return ApiResponse.ok(aiAnswerService.ask(request));
    }

    @GetMapping("/rag/logs")
    @PreAuthorize("hasAuthority('crm:knowledge:read')")
    public ApiResponse<PageResponse<KnowledgeDtos.QueryLogView>> logs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(aiAnswerService.listLogs(page, pageSize));
    }
}
