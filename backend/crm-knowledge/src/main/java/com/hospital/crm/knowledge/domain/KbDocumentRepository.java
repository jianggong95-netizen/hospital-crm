package com.hospital.crm.knowledge.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface KbDocumentRepository
        extends JpaRepository<KbDocument, UUID>, JpaSpecificationExecutor<KbDocument> {

    @Query(value = "select nextval('crm_kb_doc_no_seq')", nativeQuery = true)
    long nextDocNo();

    /**
     * 条件提交送审：只有草稿可进入审核，防止重复提交。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update KbDocument d set d.status = 'REVIEWING', d.updatedAt = :now "
            + "where d.id = :id and d.status = 'DRAFT'")
    int submitDocument(@Param("id") UUID id, @Param("now") Instant now);

    /**
     * 条件发布：带旧版本号校验，并发审核只有一个能发布成功并递增版本。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update KbDocument d set d.status = 'PUBLISHED', d.currentVersion = :newVersion, "
            + "d.publishedAt = :now, d.offlineAt = null, d.offlineReason = null, d.updatedAt = :now "
            + "where d.id = :id and d.status = 'REVIEWING' and d.currentVersion = :oldVersion")
    int approveDocument(@Param("id") UUID id, @Param("oldVersion") int oldVersion,
                        @Param("newVersion") int newVersion, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update KbDocument d set d.status = 'DRAFT', d.updatedAt = :now "
            + "where d.id = :id and d.status = 'REVIEWING'")
    int rejectDocument(@Param("id") UUID id, @Param("now") Instant now);

    /**
     * 条件下架：只有已发布知识可下架，下架后不再参与 RAG 检索。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update KbDocument d set d.status = 'OFFLINE', d.offlineAt = :now, "
            + "d.offlineReason = :reason, d.updatedAt = :now "
            + "where d.id = :id and d.status = 'PUBLISHED'")
    int offlineDocument(@Param("id") UUID id, @Param("reason") String reason, @Param("now") Instant now);
}
