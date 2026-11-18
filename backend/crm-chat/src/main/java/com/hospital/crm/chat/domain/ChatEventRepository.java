package com.hospital.crm.chat.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatEventRepository extends JpaRepository<ChatEvent, UUID> {

    List<ChatEvent> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
