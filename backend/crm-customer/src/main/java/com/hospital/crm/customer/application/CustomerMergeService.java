package com.hospital.crm.customer.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.domain.Customer;
import com.hospital.crm.customer.domain.CustomerRepository;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.CurrentUser;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 客户查重与合并：
 * 1. 查重只统计启用中的客户，手机号相同、证件号相同或姓名+出生日期相同归为一组；
 * 2. 合并把来源客户的全部业务外键改挂到目标客户（唯一约束冲突的源数据先清理），
 *    目标档案缺失的手机号/证件号/性别/生日/备注用来源档案补齐，
 *    来源客户置为 merged 并记录 merged_into 与 crm_customer_merge_log 审计留痕。
 */
@Service
public class CustomerMergeService {

    private static final String RESOURCE_TYPE = "CUSTOMER";

    /** 仅含普通 customer_id 列、可直接改挂的业务表。 */
    private static final List<String> SIMPLE_CUSTOMER_TABLES = List.of(
            "crm_customer_consent",
            "crm_order",
            "crm_service_evaluation",
            "crm_followup_task",
            "crm_chat_session",
            "crm_complaint",
            "crm_patient_snapshot",
            "crm_lead_conversion",
            "crm_activity_participant",
            "crm_invitation",
            "crm_no_show_record",
            "crm_escort_order",
            "crm_home_visit_order",
            "crm_invoice",
            "crm_member_card",
            "crm_insurance_policy",
            "crm_insurance_case",
            "crm_call_record",
            "crm_message_outbox",
            "crm_followup_his_event"
    );

    private final CustomerRepository customerRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public CustomerMergeService(CustomerRepository customerRepository,
                                NamedParameterJdbcTemplate jdbc,
                                AuditService auditService,
                                ObjectMapper objectMapper) {
        this.customerRepository = customerRepository;
        this.jdbc = jdbc;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<CustomerDtos.DuplicateGroup> findDuplicates() {
        List<CustomerDtos.DuplicateGroup> groups = new ArrayList<>();

        List<DuplicateRow> phoneRows = jdbc.query("""
                select c.id, c.customer_no, c.name, c.phone_masked, c.birth_date, c.created_at,
                       c.phone_hash as group_key
                from crm_customer c
                where c.status = 'active' and c.phone_hash is not null
                  and c.phone_hash in (
                    select phone_hash from crm_customer
                    where status = 'active' and phone_hash is not null
                    group by phone_hash having count(*) > 1)
                order by c.phone_hash, c.created_at
                """, new MapSqlParameterSource(), duplicateRowMapper());
        groupByReason(groups, "phone", phoneRows);

        List<DuplicateRow> idNoRows = jdbc.query("""
                select c.id, c.customer_no, c.name, c.phone_masked, c.birth_date, c.created_at,
                       c.id_no_hash as group_key
                from crm_customer c
                where c.status = 'active' and c.id_no_hash is not null
                  and c.id_no_hash in (
                    select id_no_hash from crm_customer
                    where status = 'active' and id_no_hash is not null
                    group by id_no_hash having count(*) > 1)
                order by c.id_no_hash, c.created_at
                """, new MapSqlParameterSource(), duplicateRowMapper());
        groupByReason(groups, "id_no", idNoRows);

        List<DuplicateRow> nameBirthRows = jdbc.query("""
                select c.id, c.customer_no, c.name, c.phone_masked, c.birth_date, c.created_at,
                       c.name || '|' || c.birth_date::text as group_key
                from crm_customer c
                where c.status = 'active' and c.birth_date is not null
                  and (c.name, c.birth_date) in (
                    select name, birth_date from crm_customer
                    where status = 'active' and birth_date is not null
                    group by name, birth_date having count(*) > 1)
                order by c.name, c.birth_date, c.created_at
                """, new MapSqlParameterSource(), duplicateRowMapper());
        groupByReason(groups, "name_birth", nameBirthRows);

        return groups;
    }

    @Transactional
    public CustomerDtos.MergeResponse merge(UUID targetId, UUID sourceId) {
        if (targetId.equals(sourceId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不能将客户与自己合并");
        }
        Customer target = load(targetId);
        Customer source = load(sourceId);
        if (!"active".equals(target.getStatus()) || !"active".equals(source.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅启用中的客户允许合并");
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("target", targetId)
                .addValue("source", sourceId);
        Map<String, Integer> mergedTables = new LinkedHashMap<>();
        Map<String, Integer> removedRows = new LinkedHashMap<>();

        // 标签关系：目标已有同标签时删除源行，避免主键冲突
        removedRows.put("crm_customer_tag", jdbc.update("""
                delete from crm_customer_tag
                where customer_id = :source and tag_id in (
                  select tag_id from crm_customer_tag where customer_id = :target)
                """, params));
        mergedTables.put("crm_customer_tag", reassign("crm_customer_tag", params));

        // 外部身份：同哈希身份与目标重复时删除源行，其余改挂
        removedRows.put("crm_customer_identity", jdbc.update("""
                delete from crm_customer_identity s
                where s.customer_id = :source and exists (
                  select 1 from crm_customer_identity t
                  where t.customer_id = :target
                    and t.source_system = s.source_system
                    and t.identity_type = s.identity_type
                    and t.identity_value_hash = s.identity_value_hash)
                """, params));
        mergedTables.put("crm_customer_identity", reassign("crm_customer_identity", params));

        // 家庭成员：同家庭先删源成员行（及其代办授权），不同家庭直接改挂
        removedRows.put("crm_family_authorization", jdbc.update("""
                delete from crm_family_authorization
                where member_id in (
                  select m.id from crm_family_member m
                  where m.customer_id = :source and m.family_id in (
                    select family_id from crm_family_member where customer_id = :target))
                """, params));
        removedRows.put("crm_family_member", jdbc.update("""
                delete from crm_family_member
                where customer_id = :source and family_id in (
                  select family_id from crm_family_member where customer_id = :target)
                """, params));
        mergedTables.put("crm_family_member", reassign("crm_family_member", params));

        // 家庭代办授权：授权人/被授权人两列改挂
        mergedTables.put("crm_family_authorization",
                jdbc.update("update crm_family_authorization set grantor_customer_id = :target "
                        + "where grantor_customer_id = :source", params)
                        + jdbc.update("update crm_family_authorization set grantee_customer_id = :target "
                        + "where grantee_customer_id = :source", params));

        // 会员账户：目标已有账户时把余额/积分并入目标后删除源账户（账本一并清理），否则改挂
        Long targetAccounts = jdbc.queryForObject(
                "select count(*) from crm_member_account where customer_id = :target", params, Long.class);
        if (targetAccounts != null && targetAccounts > 0) {
            jdbc.update("""
                    update crm_member_account t
                    set balance = t.balance + s.balance,
                        points = t.points + s.points,
                        frozen_balance = t.frozen_balance + s.frozen_balance
                    from crm_member_account s
                    where t.customer_id = :target and s.customer_id = :source
                    """, params);
            removedRows.put("crm_member_ledger", jdbc.update("""
                    delete from crm_member_ledger
                    where account_id in (select id from crm_member_account where customer_id = :source)
                    """, params));
            removedRows.put("crm_member_account", jdbc.update(
                    "delete from crm_member_account where customer_id = :source", params));
        } else {
            mergedTables.put("crm_member_account", reassign("crm_member_account", params));
        }

        // 会员共享：改挂后会出现"自己共享给自己"的行先删除，其余两列改挂
        removedRows.put("crm_member_share", jdbc.update("""
                delete from crm_member_share
                where (owner_customer_id = :source and beneficiary_customer_id = :target)
                   or (owner_customer_id = :target and beneficiary_customer_id = :source)
                """, params));
        mergedTables.put("crm_member_share",
                jdbc.update("update crm_member_share set owner_customer_id = :target "
                        + "where owner_customer_id = :source", params)
                        + jdbc.update("update crm_member_share set beneficiary_customer_id = :target "
                        + "where beneficiary_customer_id = :source", params));

        // 预约：同一时段的在途预约改挂会撞唯一索引，源侧冲突行先删除（提醒随级联清理）
        removedRows.put("crm_appointment", jdbc.update("""
                delete from crm_appointment s
                where s.customer_id = :source and s.status in ('BOOKED','CONFIRMED')
                  and exists (select 1 from crm_appointment t
                      where t.customer_id = :target and t.slot_id = s.slot_id
                        and t.status in ('BOOKED','CONFIRMED'))
                """, params));
        mergedTables.put("crm_appointment", reassign("crm_appointment", params));

        // 上门地址：目标已有启用默认地址时先取消源的默认标记，避免部分唯一索引冲突
        jdbc.update("""
                update crm_home_address s set is_default = false
                where s.customer_id = :source and s.is_default and s.status = 'active'
                  and exists (select 1 from crm_home_address t
                      where t.customer_id = :target and t.is_default and t.status = 'active')
                """, params);
        mergedTables.put("crm_home_address", reassign("crm_home_address", params));

        // 其余仅含 customer_id 列的业务表统一改挂
        for (String table : SIMPLE_CUSTOMER_TABLES) {
            mergedTables.put(table, reassign(table, params));
        }

        // 线索转化指向的客户
        mergedTables.put("crm_lead", jdbc.update(
                "update crm_lead set converted_customer_id = :target where converted_customer_id = :source",
                params));

        // 目标档案缺失的基本信息用来源档案补齐（如目标无手机号而来源有）
        List<String> filledFields = fillMissingProfile(target, source);

        // 来源客户归档（审计日志中的 patient_customer_id 保持原样，不改写历史审计）
        source.setStatus("merged");
        source.setMergedInto(targetId);
        customerRepository.save(source);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("targetCustomerNo", target.getCustomerNo());
        detail.put("sourceCustomerNo", source.getCustomerNo());
        detail.put("mergedTables", mergedTables);
        detail.put("removedRows", removedRows);
        detail.put("filledFields", filledFields);
        jdbc.update("""
                insert into crm_customer_merge_log
                    (target_customer_id, source_customer_id, merged_by, detail)
                values (:target, :source, cast(:mergedBy as uuid), cast(:detail as jsonb))
                """, params
                .addValue("mergedBy", currentUserIdOrNull())
                .addValue("detail", toJson(detail)));

        auditService.record("CUSTOMER_MERGE", RESOURCE_TYPE, targetId.toString(), detail);
        return new CustomerDtos.MergeResponse(targetId, sourceId, mergedTables, removedRows, filledFields);
    }

    /**
     * 目标档案缺失字段用来源档案补齐：手机号/证件号整组密文+哈希+脱敏值一起迁移，
     * 性别仅当目标为 unknown 时取来源值，生日与备注仅当目标为空时补齐。
     */
    private List<String> fillMissingProfile(Customer target, Customer source) {
        List<String> filled = new ArrayList<>();
        if (target.getPhoneHash() == null && source.getPhoneHash() != null) {
            target.setPhoneEncrypted(source.getPhoneEncrypted());
            target.setPhoneHash(source.getPhoneHash());
            target.setPhoneMasked(source.getPhoneMasked());
            filled.add("phone");
        }
        if (target.getIdNoHash() == null && source.getIdNoHash() != null) {
            target.setIdNoEncrypted(source.getIdNoEncrypted());
            target.setIdNoHash(source.getIdNoHash());
            target.setIdNoMasked(source.getIdNoMasked());
            filled.add("idNo");
        }
        if ((target.getGender() == null || "unknown".equals(target.getGender()))
                && source.getGender() != null && !"unknown".equals(source.getGender())) {
            target.setGender(source.getGender());
            filled.add("gender");
        }
        if (target.getBirthDate() == null && source.getBirthDate() != null) {
            target.setBirthDate(source.getBirthDate());
            filled.add("birthDate");
        }
        if (target.getNotes() == null && source.getNotes() != null) {
            target.setNotes(source.getNotes());
            filled.add("notes");
        }
        if (!filled.isEmpty()) {
            customerRepository.save(target);
        }
        return filled;
    }

    private int reassign(String table, MapSqlParameterSource params) {
        return jdbc.update("update " + table + " set customer_id = :target where customer_id = :source",
                params);
    }

    private void groupByReason(List<CustomerDtos.DuplicateGroup> groups, String reason,
                               List<DuplicateRow> rows) {
        Map<String, List<CustomerDtos.DuplicateItem>> byKey = new LinkedHashMap<>();
        for (DuplicateRow row : rows) {
            byKey.computeIfAbsent(row.groupKey(), k -> new ArrayList<>()).add(row.toItem());
        }
        byKey.forEach((key, items) -> groups.add(new CustomerDtos.DuplicateGroup(reason, items)));
    }

    private RowMapper<DuplicateRow> duplicateRowMapper() {
        return (rs, rowNum) -> new DuplicateRow(
                rs.getObject("id", UUID.class),
                rs.getString("customer_no"),
                rs.getString("name"),
                rs.getString("phone_masked"),
                toLocalDate(rs.getDate("birth_date")),
                toInstant(rs.getTimestamp("created_at")),
                rs.getString("group_key"));
    }

    private record DuplicateRow(UUID id, String customerNo, String name, String phoneMasked,
                                LocalDate birthDate, Instant createdAt, String groupKey) {

        CustomerDtos.DuplicateItem toItem() {
            return new CustomerDtos.DuplicateItem(id, customerNo, name, phoneMasked, birthDate, createdAt);
        }
    }

    private Customer load(UUID customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "客户档案不存在"));
    }

    private String currentUserIdOrNull() {
        try {
            return CurrentUser.id().toString();
        } catch (BusinessException ex) {
            return null;
        }
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "合并明细序列化失败");
        }
    }

    private LocalDate toLocalDate(Date value) {
        return value == null ? null : value.toLocalDate();
    }

    private Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
