package com.hospital.crm.escort.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EscortReviewRepository extends JpaRepository<EscortReview, UUID> {

    boolean existsByOrderId(UUID orderId);

    Optional<EscortReview> findByOrderId(UUID orderId);

    Page<EscortReview> findByEscortIdOrderByCreatedAtDesc(UUID escortId, Pageable pageable);

    Page<EscortReview> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 陪诊师评分聚合：返回 [escortId, 平均分, 评价数]，仅包含有评价的陪诊师。
     */
    @Query("select r.escortId, avg(r.rating), count(r) from EscortReview r "
            + "where r.escortId in :ids group by r.escortId")
    List<Object[]> ratingSummary(@Param("ids") Collection<UUID> escortIds);
}
