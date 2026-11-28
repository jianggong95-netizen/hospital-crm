package com.hospital.crm.followup.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowupTaskRepository extends JpaRepository<FollowupTask, UUID>,
        JpaSpecificationExecutor<FollowupTask> {

    @Query(value = "select nextval('crm_followup_task_no_seq')", nativeQuery = true)
    long nextTaskNo();

    Optional<FollowupTask> findByTaskNo(String taskNo);

    boolean existsByPlanIdAndTriggerEventKey(UUID planId, String triggerEventKey);

    long countByStatus(String status);

    long countByStatusAndPlannedAtBefore(String status, Instant time);

    /**
     * 板块/分类统计基础行：任务按 分类 × 状态 聚合计数，
     * 并对待执行任务统计计划时间已过的逾期数（overdue_count 仅在 PENDING 行有意义）。
     * 分类已删除或历史数据缺失板块时 visit_type 归入 UNKNOWN。
     * 返回列顺序：visit_type, category_code, category_name, status, task_count, overdue_count。
     */
    @Query(value = """
            SELECT COALESCE(c.visit_type, 'UNKNOWN') AS visit_type,
                   t.category_code AS category_code,
                   c.category_name AS category_name,
                   t.status AS status,
                   COUNT(*) AS task_count,
                   COUNT(*) FILTER (WHERE t.planned_at < now()) AS overdue_count
            FROM crm_followup_task t
            LEFT JOIN crm_followup_category c ON c.category_code = t.category_code
            GROUP BY COALESCE(c.visit_type, 'UNKNOWN'), t.category_code, c.category_name, t.status
            """, nativeQuery = true)
    List<Object[]> statsRowsByCategoryAndStatus();
}
