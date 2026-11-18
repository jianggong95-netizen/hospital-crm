package com.hospital.crm.analytics.domain;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 指标聚合原生查询仓库：直接查询各业务表（crm_customer/crm_lead/crm_appointment/
 * crm_order/crm_followup_task/crm_complaint/crm_call_record/crm_message_outbox/
 * crm_insurance_case），返回 Map 或标量。
 *
 * <p>组织数据权限：非全院范围时由各服务解析 allowedUserIds/orgIds 后传入，
 * 本类按归属列动态拼接过滤条件；无归属列的表（客户、订单、消息队列）不提供
 * 范围过滤入口，由服务层直接返回不可计算。</p>
 */
@Repository
public class MetricAggRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public MetricAggRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** sys_user 中 org_id 属于允许组织集合的用户 id（用于解析 allowedUserIds）。 */
    public List<UUID> findUserIdsByOrgs(List<UUID> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("orgIds", orgIds);
        return jdbc.queryForList(
                "select id from sys_user where org_id in (:orgIds)", params, UUID.class);
    }

    /** 区间内新增且状态 active 的客户数（客户表无组织归属，仅全院口径调用）。 */
    public long countNewCustomers(OffsetDateTime from, OffsetDateTime to) {
        MapSqlParameterSource params = range(from, to);
        Long value = jdbc.queryForObject("""
                select count(*) from crm_customer
                where status = 'active' and created_at >= :from and created_at < :to
                """, params, Long.class);
        return value == null ? 0 : value;
    }

    /** 截至 to 日终状态 active 的客户全量计数（仅全院口径调用）。 */
    public long countActiveCustomersUntil(OffsetDateTime to) {
        MapSqlParameterSource params = new MapSqlParameterSource("to", to);
        Long value = jdbc.queryForObject("""
                select count(*) from crm_customer
                where status = 'active' and created_at < :to
                """, params, Long.class);
        return value == null ? 0 : value;
    }

    /** 区间线索总数与已转化数（CONVERTED/MERGED），按 owner_user_id 或 org_id 过滤。 */
    public Map<String, Object> leadCounts(OffsetDateTime from, OffsetDateTime to,
                                          List<UUID> allowedUserIds, List<UUID> orgIds) {
        String scope = scopeCondition("l.owner_user_id", "l.org_id", allowedUserIds, orgIds);
        MapSqlParameterSource params = range(from, to);
        putScopeParams(params, allowedUserIds, orgIds);
        return firstRow(jdbc.queryForList("""
                select count(*) as total,
                       count(*) filter (where l.status in ('CONVERTED', 'MERGED')) as converted
                from crm_lead l
                where l.created_at >= :from and l.created_at < :to and """ + scope, params));
    }

    /** 区间预约单量、爽约数与到期数（COMPLETED/NO_SHOW/CANCELLED），按 org_id 过滤。 */
    public Map<String, Object> appointmentCounts(OffsetDateTime from, OffsetDateTime to,
                                                 List<UUID> orgIds) {
        String scope = scopeCondition(null, "a.org_id", null, orgIds);
        MapSqlParameterSource params = range(from, to);
        putScopeParams(params, null, orgIds);
        return firstRow(jdbc.queryForList("""
                select count(*) as total,
                       count(*) filter (where a.status = 'NO_SHOW') as no_show,
                       count(*) filter (where a.status in ('COMPLETED', 'NO_SHOW', 'CANCELLED')) as due
                from crm_appointment a
                where a.created_at >= :from and a.created_at < :to and """ + scope, params));
    }

    /** 区间订单实收金额求和（updated_at 近似支付时间，仅全院口径调用）。 */
    public BigDecimal sumOrderPaidAmount(OffsetDateTime from, OffsetDateTime to) {
        MapSqlParameterSource params = range(from, to);
        BigDecimal value = jdbc.queryForObject("""
                select coalesce(sum(o.paid_amount), 0) from crm_order o
                where o.status in ('PAID', 'COMPLETED', 'PARTIAL_REFUNDED', 'REFUNDED')
                  and o.updated_at >= :from and o.updated_at < :to
                """, params, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 区间到期随访任务数与已完成数，按 assignee_id 过滤。 */
    public Map<String, Object> followupCounts(OffsetDateTime from, OffsetDateTime to,
                                              List<UUID> allowedUserIds) {
        String scope = scopeCondition("t.assignee_id", null, allowedUserIds, null);
        MapSqlParameterSource params = range(from, to);
        putScopeParams(params, allowedUserIds, List.of());
        return firstRow(jdbc.queryForList("""
                select count(*) as due,
                       count(*) filter (where t.status = 'COMPLETED') as completed
                from crm_followup_task t
                where t.planned_at >= :from and t.planned_at < :to and """ + scope, params));
    }

    /** 区间投诉建单数（ticket_type='complaint'），按 created_by 过滤。 */
    public long countComplaints(OffsetDateTime from, OffsetDateTime to,
                                List<UUID> allowedUserIds) {
        String scope = scopeCondition("c.created_by", null, allowedUserIds, null);
        MapSqlParameterSource params = range(from, to);
        putScopeParams(params, allowedUserIds, List.of());
        Long value = jdbc.queryForObject("""
                select count(*) from crm_complaint c
                where c.ticket_type = 'complaint'
                  and c.created_at >= :from and c.created_at < :to and """ + scope, params, Long.class);
        return value == null ? 0 : value;
    }

    /** 区间通话总数与接通数，按 agent_user_id 过滤。 */
    public Map<String, Object> callCounts(OffsetDateTime from, OffsetDateTime to,
                                          List<UUID> allowedUserIds) {
        String scope = scopeCondition("r.agent_user_id", null, allowedUserIds, null);
        MapSqlParameterSource params = range(from, to);
        putScopeParams(params, allowedUserIds, List.of());
        return firstRow(jdbc.queryForList("""
                select count(*) as total,
                       count(*) filter (where r.status = 'connected') as connected
                from crm_call_record r
                where r.started_at >= :from and r.started_at < :to and """ + scope, params));
    }

    /** 区间消息总数与 SENT 数（消息表无归属列，仅全院口径调用）。 */
    public Map<String, Object> smsCounts(OffsetDateTime from, OffsetDateTime to) {
        MapSqlParameterSource params = range(from, to);
        return firstRow(jdbc.queryForList("""
                select count(*) as total,
                       count(*) filter (where m.status = 'SENT') as sent
                from crm_message_outbox m
                where m.created_at >= :from and m.created_at < :to
                """, params));
    }

    /** 区间商保到账金额求和（decided_at 口径），按 handler_user_id 或 created_by 过滤。 */
    public BigDecimal sumInsuranceReceived(OffsetDateTime from, OffsetDateTime to,
                                           List<UUID> allowedUserIds) {
        String scope = scopeCondition(
                "(i.handler_user_id in (:scopeUsers) or i.created_by in (:scopeUsers))",
                null, allowedUserIds, null);
        MapSqlParameterSource params = range(from, to);
        putScopeParams(params, allowedUserIds, List.of());
        BigDecimal value = jdbc.queryForObject("""
                select coalesce(sum(i.actual_received), 0) from crm_insurance_case i
                where i.decided_at is not null
                  and i.decided_at >= :from and i.decided_at < :to and """ + scope, params, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 客户明细（仅全院口径调用）：只 SELECT 已脱敏列，绝不输出 phone_encrypted/id_no_encrypted。
     */
    public List<Map<String, Object>> customerDetail(OffsetDateTime from, OffsetDateTime to) {
        MapSqlParameterSource params = range(from, to);
        return jdbc.queryForList("""
                select c.customer_no, c.name, c.phone_masked, c.id_no_masked,
                       c.source_code, c.status, to_char(c.created_at, 'YYYY-MM-DD HH24:MI:SS') as created_at
                from crm_customer c
                where c.created_at >= :from and c.created_at < :to
                order by c.created_at asc
                """, params);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------
    private MapSqlParameterSource range(OffsetDateTime from, OffsetDateTime to) {
        return new MapSqlParameterSource().addValue("from", from).addValue("to", to);
    }

    private Map<String, Object> firstRow(List<Map<String, Object>> rows) {
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    /**
     * 动态拼接数据范围条件：userCols 为归属用户列（可传多个列的完整表达式），
     * orgCol 为组织列。集合传 null 表示该维度不参与过滤（全院口径）；
     * 传非 null 空集合表示受限且无可见数据（恒假）。
     */
    private String scopeCondition(String userCols, String orgCol,
                                  List<UUID> users, List<UUID> orgs) {
        boolean userApplicable = userCols != null && users != null;
        boolean orgApplicable = orgCol != null && orgs != null;
        List<String> parts = new ArrayList<>();
        if (userApplicable && !users.isEmpty()) {
            if (userCols.contains("in (")) {
                parts.add(userCols);
            } else {
                parts.add(userCols + " in (:scopeUsers)");
            }
        }
        if (orgApplicable && !orgs.isEmpty()) {
            parts.add(orgCol + " in (:scopeOrgs)");
        }
        if (!parts.isEmpty()) {
            return "(" + String.join(" or ", parts) + ")";
        }
        // 带括号返回，避免与文本块行尾（被剥离的）空格拼接产生 "and1 = 0" 语法错误
        return userApplicable || orgApplicable ? "(1 = 0)" : "(1 = 1)";
    }

    private void putScopeParams(MapSqlParameterSource params, List<UUID> users, List<UUID> orgs) {
        if (users != null && !users.isEmpty()) {
            params.addValue("scopeUsers", users);
        }
        if (orgs != null && !orgs.isEmpty()) {
            params.addValue("scopeOrgs", orgs);
        }
    }
}
