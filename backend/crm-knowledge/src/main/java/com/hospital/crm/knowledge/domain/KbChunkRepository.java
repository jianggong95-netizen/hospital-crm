package com.hospital.crm.knowledge.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface KbChunkRepository extends JpaRepository<KbChunk, UUID> {

    List<KbChunk> findByDocumentIdAndDocVersionOrderByChunkIndexAsc(UUID documentId, int docVersion);

    long countByDocumentIdAndDocVersion(UUID documentId, int docVersion);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    void deleteByDocumentIdAndDocVersion(UUID documentId, int docVersion);

    /**
     * 检索候选：仅取已发布文档的当前版本切片，下架或草稿知识不参与 RAG。
     */
    @Query("select c from KbChunk c where c.docVersion = "
            + "(select d.currentVersion from KbDocument d where d.id = c.documentId) "
            + "and exists (select 1 from KbDocument d where d.id = c.documentId and d.status = 'PUBLISHED')")
    List<KbChunk> findPublishedChunks();
}
