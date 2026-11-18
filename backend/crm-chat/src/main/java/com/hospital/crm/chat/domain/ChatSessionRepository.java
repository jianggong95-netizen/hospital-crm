package com.hospital.crm.chat.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatSessionRepository
        extends JpaRepository<ChatSession, UUID>, JpaSpecificationExecutor<ChatSession> {

    Optional<ChatSession> findBySessionNo(String sessionNo);

    @Query(value = "select nextval('crm_chat_session_no_seq')", nativeQuery = true)
    long nextSessionNo();

    /**
     * 仅 AI 接待中的会话可累计转人工请求，避免并发重复计数。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ChatSession s set s.handoffRequestCount = s.handoffRequestCount + 1, s.updatedAt = :now "
            + "where s.id = :id and s.status = 'AI'")
    int incrementHandoffRequest(@Param("id") UUID id, @Param("now") Instant now);

    /**
     * 条件更新入队：只有 AI 接待中的会话可进入人工队列，保证队列状态准确。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ChatSession s set s.status = 'QUEUED', s.currentHandlerType = 'human', "
            + "s.queueEnteredAt = :now, s.priority = :priority, s.aiSummary = :summary, s.updatedAt = :now "
            + "where s.id = :id and s.status = 'AI'")
    int queueSession(@Param("id") UUID id, @Param("now") Instant now,
                     @Param("priority") String priority, @Param("summary") String summary);

    /**
     * 条件接管：只有排队中的会话可被接管，并发接管只有一个成功。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ChatSession s set s.status = 'ACTIVE', s.currentHandlerType = 'human', "
            + "s.assignedStaffId = :staffId, s.claimedAt = :now, s.updatedAt = :now "
            + "where s.id = :id and s.status = 'QUEUED'")
    int claimSession(@Param("id") UUID id, @Param("staffId") UUID staffId, @Param("now") Instant now);

    /**
     * 条件转接：仅当前接待客服可把进行中的会话转出。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ChatSession s set s.assignedStaffId = :toStaffId, s.updatedAt = :now "
            + "where s.id = :id and s.status = 'ACTIVE' and s.assignedStaffId = :fromStaffId")
    int transferSession(@Param("id") UUID id, @Param("fromStaffId") UUID fromStaffId,
                        @Param("toStaffId") UUID toStaffId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ChatSession s set s.status = 'CLOSED', s.endedAt = :now, s.closedBy = :staffId, "
            + "s.updatedAt = :now where s.id = :id and s.status <> 'CLOSED'")
    int closeSession(@Param("id") UUID id, @Param("staffId") UUID staffId, @Param("now") Instant now);

    @Query("select count(s) from ChatSession s where s.status = 'QUEUED'")
    long countQueued();

    /**
     * 队列按优先级（紧急→高→普通→低）与入队时间排序，保证等待最久优先。
     */
    @Query("select s from ChatSession s where s.status = 'QUEUED' "
            + "order by case s.priority when 'urgent' then 0 when 'high' then 1 "
            + "when 'normal' then 2 else 3 end, s.queueEnteredAt asc")
    List<ChatSession> findQueue();
}
