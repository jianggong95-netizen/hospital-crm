package com.hospital.crm.operation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceChannelRepository extends JpaRepository<SourceChannel, UUID> {

    Optional<SourceChannel> findByChannelCode(String channelCode);

    boolean existsByChannelCode(String channelCode);

    List<SourceChannel> findAllByOrderByChannelCodeAsc();

    List<SourceChannel> findAllByStatusOrderByChannelCodeAsc(String status);
}
