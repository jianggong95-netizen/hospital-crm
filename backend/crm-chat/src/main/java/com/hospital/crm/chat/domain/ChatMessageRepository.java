package com.hospital.crm.chat.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    Page<ChatMessage> findBySessionId(UUID sessionId, Pageable pageable);

    List<ChatMessage> findTop200BySessionIdOrderByCreatedAtDesc(UUID sessionId);

    List<ChatMessage> findTop10BySessionIdOrderByCreatedAtDesc(UUID sessionId);

    Optional<ChatMessage> findBySessionIdAndClientMessageId(UUID sessionId, String clientMessageId);

    long countBySessionId(UUID sessionId);

    /**
     * 批量获取每个会话的最后一条消息，避免列表页 N+1 查询。
     */
    @Query("select m from ChatMessage m where m.sessionId in :sessionIds "
            + "and m.createdAt = (select max(x.createdAt) from ChatMessage x where x.sessionId = m.sessionId)")
    List<ChatMessage> findLatestBySessionIds(@Param("sessionIds") Collection<UUID> sessionIds);
}
