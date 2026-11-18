package com.hospital.crm.chat.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatHandoffRepository extends JpaRepository<ChatHandoff, UUID> {

    List<ChatHandoff> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    List<ChatHandoff> findBySessionIdInOrderByCreatedAtAsc(Collection<UUID> sessionIds);

    Optional<ChatHandoff> findTop1BySessionIdAndStatusOrderByCreatedAtDesc(UUID sessionId, String status);
}
