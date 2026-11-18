package com.hospital.crm.knowledge.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * AI 知识库与数字人接口契约。
 */
public final class KnowledgeDtos {

    private KnowledgeDtos() {
    }

    public record CategoryView(UUID id, String code, String name, int sortOrder, String status, Instant createdAt) {
    }

    public record CategoryRequest(
            @Pattern(regexp = "^[A-Za-z0-9_-]{2,64}$", message = "分类编码只能包含字母、数字、下划线、短横线，长度 2-64")
            String code,
            @NotBlank(message = "分类名称不能为空")
            @Size(max = 120, message = "分类名称不能超过 120 字") String name,
            Integer sortOrder,
            @Pattern(regexp = "active|disabled", message = "分类状态不合法") String status) {
    }

    public record DocumentView(
            UUID id,
            String docNo,
            String title,
            UUID categoryId,
            String categoryName,
            String summary,
            String sourceType,
            String fileName,
            Long fileSize,
            String fileType,
            String status,
            int currentVersion,
            int draftChunkCount,
            UUID ownerId,
            String ownerName,
            Instant publishedAt,
            Instant offlineAt,
            String offlineReason,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ChunkView(UUID id, int docVersion, int chunkIndex, String content, int charCount, Instant createdAt) {
    }

    public record VersionView(UUID id, int versionNo, String title, String summary, int chunkCount,
                              String changeNote, UUID createdBy, String createdByName, Instant createdAt) {
    }

    public record ReviewView(UUID id, int versionNo, String action, UUID reviewerId,
                             String reviewerName, String comment, Instant createdAt) {
    }

    public record DocumentDetail(DocumentView document, List<ChunkView> chunks,
                                 List<VersionView> versions, List<ReviewView> reviews) {
    }

    public record CreateDocumentRequest(
            @NotBlank(message = "标题不能为空") @Size(max = 200) String title,
            UUID categoryId,
            @Size(max = 500) String summary,
            @NotBlank(message = "知识内容不能为空") @Size(max = 200000) String content) {
    }

    public record UpdateMetaRequest(
            @NotBlank(message = "标题不能为空") @Size(max = 200) String title,
            UUID categoryId,
            @Size(max = 500) String summary) {
    }

    public record UpdateContentRequest(
            @NotBlank(message = "知识内容不能为空") @Size(max = 200000) String content,
            @Size(max = 500) String changeNote) {
    }

    public record ReviewActionRequest(@Size(max = 500) String comment) {
    }

    public record OfflineRequest(
            @NotBlank(message = "下架原因不能为空") @Size(max = 500) String reason) {
    }

    public record AskRequest(
            @NotBlank(message = "问题不能为空") @Size(max = 500) String question,
            Integer topK,
            @Pattern(regexp = "chat|followup", message = "场景仅支持 chat/followup") String scene,
            UUID sessionId) {
    }

    public record RefView(UUID docId, String docNo, String title, int version,
                          UUID chunkId, int chunkIndex, BigDecimal score) {
    }

    public record AskResult(UUID logId, String answer, BigDecimal confidence, List<RefView> refs,
                            boolean needsHandoff, String handoffMessage) {
    }

    public record QueryLogView(UUID id, String question, String answer, BigDecimal confidence,
                               List<String> refs, String scene, UUID sessionId, boolean needsHandoff,
                               UUID askedBy, String askedByName, Instant createdAt) {
    }
}
