package com.hospital.crm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 PostgreSQL 环境下的 Flyway 迁移验证，仅由 CI 环境变量 CRM_CI=true 启用。
 */
@EnabledIfEnvironmentVariable(named = "CRM_CI", matches = "true")
@SpringBootTest
class DatabaseMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void baselineMigrationCreatesCoreTables() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' "
                        + "and table_name in ('crm_customer','crm_family','crm_order','crm_payment','sys_audit_log')",
                Integer.class);
        assertEquals(5, tableCount);
    }

    @Test
    void systemAuthMigrationCreatesRbacTables() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' "
                        + "and table_name in ('sys_org','sys_user','sys_role','sys_permission',"
                        + "'sys_user_role','sys_role_permission','sys_role_org_scope','sys_refresh_token')",
                Integer.class);
        assertEquals(8, tableCount);

        Integer traceIdColumn = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'sys_audit_log' and column_name = 'trace_id'",
                Integer.class);
        assertEquals(1, traceIdColumn);
    }

    @Test
    void seedsRootOrgAndSuperAdminWithAllPermissions() {
        Integer rootOrgCount = jdbcTemplate.queryForObject(
                "select count(*) from sys_org where org_code = 'ROOT'", Integer.class);
        assertEquals(1, rootOrgCount);

        Integer permissionTotal = jdbcTemplate.queryForObject(
                "select count(*) from sys_permission", Integer.class);
        Integer granted = jdbcTemplate.queryForObject(
                "select count(*) from sys_role_permission rp "
                        + "join sys_role r on r.id = rp.role_id where r.role_code = 'SUPER_ADMIN'",
                Integer.class);
        assertTrue(permissionTotal != null && permissionTotal >= 8);
        assertEquals(permissionTotal, granted);

        String dataScope = jdbcTemplate.queryForObject(
                "select data_scope from sys_role where role_code = 'SUPER_ADMIN'", String.class);
        assertEquals("all", dataScope);
    }

    @Test
    void customerCenterMigrationCreatesTagTablesAndPermissions() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' "
                        + "and table_name in ('crm_tag','crm_customer_tag')",
                Integer.class);
        assertEquals(2, tableCount);

        Integer tagSeeds = jdbcTemplate.queryForObject(
                "select count(*) from crm_tag", Integer.class);
        assertTrue(tagSeeds != null && tagSeeds >= 6);

        Integer customerPermissions = jdbcTemplate.queryForObject(
                "select count(*) from sys_permission where permission_code like 'crm:customer:%'",
                Integer.class);
        assertEquals(3, customerPermissions);

        Integer granted = jdbcTemplate.queryForObject(
                "select count(*) from sys_role_permission rp "
                        + "join sys_role r on r.id = rp.role_id "
                        + "join sys_permission p on p.id = rp.permission_id "
                        + "where r.role_code = 'SUPER_ADMIN' and p.permission_code like 'crm:%'",
                Integer.class);
        Integer customerCenterPermissions = jdbcTemplate.queryForObject(
                "select count(*) from sys_permission where permission_code like 'crm:%'",
                Integer.class);
        assertEquals(customerCenterPermissions, granted);
    }

    @Test
    void familyAccountMigrationCreatesAuthorizationTableAndPermissions() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' "
                        + "and table_name in ('crm_family_authorization')",
                Integer.class);
        assertEquals(1, tableCount);

        Integer memberStatusColumn = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'crm_family_member' "
                        + "and column_name = 'status'",
                Integer.class);
        assertEquals(1, memberStatusColumn);

        Integer familyPermissions = jdbcTemplate.queryForObject(
                "select count(*) from sys_permission where permission_code like 'crm:family:%'",
                Integer.class);
        assertEquals(3, familyPermissions);

        Integer granted = jdbcTemplate.queryForObject(
                "select count(*) from sys_role_permission rp "
                        + "join sys_role r on r.id = rp.role_id "
                        + "join sys_permission p on p.id = rp.permission_id "
                        + "where r.role_code = 'SUPER_ADMIN' and p.permission_code like 'crm:%'",
                Integer.class);
        Integer crmPermissions = jdbcTemplate.queryForObject(
                "select count(*) from sys_permission where permission_code like 'crm:%'",
                Integer.class);
        assertEquals(crmPermissions, granted);
    }

    @Test
    void hisIntegrationMigrationCreatesSnapshotTableAndPermissions() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' "
                        + "and table_name in ('crm_patient_snapshot')",
                Integer.class);
        assertEquals(1, tableCount);

        Integer snapshotColumns = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'crm_patient_snapshot' "
                        + "and column_name in ('source_system','external_patient_id','snapshot',"
                        + "'fetched_at','valid_until','match_confidence')",
                Integer.class);
        assertEquals(6, snapshotColumns);

        Integer hisPermissions = jdbcTemplate.queryForObject(
                "select count(*) from sys_permission where permission_code like 'his:%'",
                Integer.class);
        assertEquals(2, hisPermissions);

        Integer granted = jdbcTemplate.queryForObject(
                "select count(*) from sys_role_permission rp "
                        + "join sys_role r on r.id = rp.role_id "
                        + "join sys_permission p on p.id = rp.permission_id "
                        + "where r.role_code = 'SUPER_ADMIN' and p.permission_code like 'his:%'",
                Integer.class);
        assertEquals(hisPermissions, granted);
    }

    @Test
    void patient360MigrationAddsIdentityIndexAndPermission() {
        Integer identityIndex = jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes where schemaname = 'public' "
                        + "and indexname = 'idx_identity_customer'",
                Integer.class);
        assertEquals(1, identityIndex);

        Integer permissionCount = jdbcTemplate.queryForObject(
                "select count(*) from sys_permission where permission_code = 'crm:patient360:read'",
                Integer.class);
        assertEquals(1, permissionCount);

        Integer granted = jdbcTemplate.queryForObject(
                "select count(*) from sys_role_permission rp "
                        + "join sys_role r on r.id = rp.role_id "
                        + "join sys_permission p on p.id = rp.permission_id "
                        + "where r.role_code = 'SUPER_ADMIN' and p.permission_code = 'crm:patient360:read'",
                Integer.class);
        assertEquals(1, granted);
    }

    @Test
    void operationMigrationCreatesLeadInvitationAndActivityTables() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' "
                        + "and table_name in ('crm_source_channel','crm_lead','crm_lead_contact_record',"
                        + "'crm_lead_assignment','crm_lead_conversion','crm_invitation',"
                        + "'crm_activity','crm_activity_participant')",
                Integer.class);
        assertEquals(8, tableCount);

        Integer leadIndexes = jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes where schemaname = 'public' "
                        + "and indexname in ('idx_lead_phone_hash','idx_lead_id_hash',"
                        + "'idx_activity_participant_activity','uq_activity_participant_phone')",
                Integer.class);
        assertEquals(4, leadIndexes);

        Integer channelSeeds = jdbcTemplate.queryForObject(
                "select count(*) from crm_source_channel", Integer.class);
        assertTrue(channelSeeds != null && channelSeeds >= 7);

        Integer operationPermissions = jdbcTemplate.queryForObject(
                "select count(*) from sys_permission where permission_code in "
                        + "('crm:channel:read','crm:channel:write','crm:lead:read','crm:lead:write',"
                        + "'crm:lead:convert','crm:lead:assign','crm:invitation:read',"
                        + "'crm:invitation:write','crm:activity:read','crm:activity:write')",
                Integer.class);
        assertEquals(10, operationPermissions);

        Integer granted = jdbcTemplate.queryForObject(
                "select count(*) from sys_role_permission rp "
                        + "join sys_role r on r.id = rp.role_id "
                        + "join sys_permission p on p.id = rp.permission_id "
                        + "where r.role_code = 'SUPER_ADMIN' and p.permission_code like 'crm:%'",
                Integer.class);
        Integer crmPermissions = jdbcTemplate.queryForObject(
                "select count(*) from sys_permission where permission_code like 'crm:%'",
                Integer.class);
        assertEquals(crmPermissions, granted);
    }

    @Test
    void appointmentMigrationCreatesTypeSlotReminderAndNoShowTables() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' "
                        + "and table_name in ('crm_appointment_type','crm_service_slot','crm_appointment',"
                        + "'crm_appointment_reminder','crm_no_show_record')",
                Integer.class);
        assertEquals(5, tableCount);

        Integer appointmentIndexes = jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes where schemaname = 'public' "
                        + "and indexname in ('uq_slot_type_date_start','uq_appointment_active_slot',"
                        + "'idx_reminder_status_planned','idx_no_show_customer')",
                Integer.class);
        assertEquals(4, appointmentIndexes);

        Integer typeSeeds = jdbcTemplate.queryForObject(
                "select count(*) from crm_appointment_type", Integer.class);
        assertTrue(typeSeeds != null && typeSeeds >= 5);

        Integer appointmentPermissions = jdbcTemplate.queryForObject(
                "select count(*) from sys_permission where permission_code in "
                        + "('crm:appttype:read','crm:appttype:write','crm:appointment:read',"
                        + "'crm:appointment:write','crm:appointment:manage')",
                Integer.class);
        assertEquals(5, appointmentPermissions);

        Integer granted = jdbcTemplate.queryForObject(
                "select count(*) from sys_role_permission rp "
                        + "join sys_role r on r.id = rp.role_id "
                        + "join sys_permission p on p.id = rp.permission_id "
                        + "where r.role_code = 'SUPER_ADMIN' and p.permission_code like 'crm:%'",
                Integer.class);
        Integer crmPermissions = jdbcTemplate.queryForObject(
                "select count(*) from sys_permission where permission_code like 'crm:%'",
                Integer.class);
        assertEquals(crmPermissions, granted);
    }

    @Test
    void escortMigrationCreatesEscortScheduleAndOrderTables() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' "
                        + "and table_name in ('crm_escort','crm_escort_qualification','crm_escort_schedule',"
                        + "'crm_escort_order','crm_escort_review')",
                Integer.class);
        assertEquals(5, tableCount);

        Integer escortIndexes = jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes where schemaname = 'public' "
                        + "and indexname in ('uq_schedule_escort_date_start','uq_review_order')",
                Integer.class);
        assertEquals(2, escortIndexes);

        Integer escortSequences = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.sequences where sequence_schema = 'public' "
                        + "and sequence_name in ('crm_escort_no_seq','crm_escort_order_no_seq')",
                Integer.class);
        assertEquals(2, escortSequences);

        Integer escortPermissions = jdbcTemplate.queryForObject(
                "select count(*) from sys_permission where permission_code in "
                        + "('crm:escort:read','crm:escort:write','crm:escortorder:read',"
                        + "'crm:escortorder:write','crm:escortorder:manage')",
                Integer.class);
        assertEquals(5, escortPermissions);

        Integer granted = jdbcTemplate.queryForObject(
                "select count(*) from sys_role_permission rp "
                        + "join sys_role r on r.id = rp.role_id "
                        + "join sys_permission p on p.id = rp.permission_id "
                        + "where r.role_code = 'SUPER_ADMIN' and p.permission_code like 'crm:%'",
                Integer.class);
        Integer crmPermissions = jdbcTemplate.queryForObject(
                "select count(*) from sys_permission where permission_code like 'crm:%'",
                Integer.class);
        assertEquals(crmPermissions, granted);
    }

    @Test
    void appliedMigrationsIncludeSystemAuthScript() {
        Integer appliedMigrations = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class);
        assertTrue(appliedMigrations != null && appliedMigrations >= 9);
    }
}
