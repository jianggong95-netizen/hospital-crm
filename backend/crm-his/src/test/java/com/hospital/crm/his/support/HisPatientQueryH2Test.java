package com.hospital.crm.his.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用 H2 MSSQLServer 模式验证只读适配器的 SQL 可执行性与参数化效果。
 * 该测试不连接任何真实 HIS，也不包含真实患者数据。
 */
class HisPatientQueryH2Test {

    private HisProperties properties;
    private HisConnectionProvider connectionProvider;
    private HisPatientQuery patientQuery;

    @BeforeEach
    void setUp() {
        properties = new HisProperties();
        properties.setEnabled(true);
        properties.setUrl("jdbc:h2:mem:his_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MSSQLServer;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        properties.setUsername("sa");
        properties.setPassword("");
        properties.setDriverClassName("org.h2.Driver");
        properties.setReadOnlyCheck(false);
        properties.getPatientView().setTable("v_crm_patient");
        properties.getVisitView().setTable("v_crm_visit");
        properties.getAdmissionView().setTable("v_crm_admission");

        connectionProvider = new HisConnectionProvider(properties);
        patientQuery = new HisPatientQuery(properties, connectionProvider,
                new HisQueryBuilder(), new HisCircuitBreaker(5, 30_000, 1));

        NamedParameterJdbcTemplate template = connectionProvider.template();
        template.getJdbcOperations().execute("""
                create table v_crm_patient (
                  patient_id varchar(40) primary key,
                  mrn varchar(40),
                  patient_name varchar(100),
                  gender varchar(16),
                  birth_date date,
                  mobile varchar(32)
                )
                """);
        template.getJdbcOperations().execute("""
                create table v_crm_visit (
                  visit_id varchar(40) primary key,
                  patient_id varchar(40),
                  visit_time timestamp,
                  department varchar(100),
                  doctor_name varchar(100),
                  diagnosis varchar(400)
                )
                """);
        template.getJdbcOperations().execute("""
                create table v_crm_admission (
                  admission_id varchar(40) primary key,
                  patient_id varchar(40),
                  admitted_at timestamp,
                  discharged_at timestamp,
                  ward varchar(100),
                  diagnosis varchar(400)
                )
                """);

        template.getJdbcOperations().update(
                "insert into v_crm_patient (patient_id, mrn, patient_name, gender, birth_date, mobile) "
                        + "values (?, ?, ?, ?, ?, ?)",
                "P001", "MRN001", "张三", "male", Date.valueOf("1980-01-02"), "13800001111");
        template.getJdbcOperations().update(
                "insert into v_crm_patient (patient_id, mrn, patient_name, gender, birth_date, mobile) "
                        + "values (?, ?, ?, ?, ?, ?)",
                "P002", "MRN002", "李四", "female", Date.valueOf("1990-05-06"), "13900002222");

        template.getJdbcOperations().update(
                "insert into v_crm_visit (visit_id, patient_id, visit_time, department, doctor_name, diagnosis) "
                        + "values (?, ?, ?, ?, ?, ?)",
                "V001", "P001", Timestamp.from(Instant.parse("2026-01-10T02:00:00Z")),
                "心内科", "王医生", "高血压");
        template.getJdbcOperations().update(
                "insert into v_crm_visit (visit_id, patient_id, visit_time, department, doctor_name, diagnosis) "
                        + "values (?, ?, ?, ?, ?, ?)",
                "V002", "P001", Timestamp.from(Instant.parse("2026-02-11T03:00:00Z")),
                "心内科", "王医生", "复诊");
        template.getJdbcOperations().update(
                "insert into v_crm_admission (admission_id, patient_id, admitted_at, discharged_at, ward, diagnosis) "
                        + "values (?, ?, ?, ?, ?, ?)",
                "A001", "P001", Timestamp.from(Instant.parse("2026-03-01T00:00:00Z")),
                Timestamp.from(Instant.parse("2026-03-05T00:00:00Z")), "心内科病房", "高血压");
    }

    @AfterEach
    void tearDown() {
        connectionProvider.destroy();
    }

    @Test
    void searchesByMobileMrnAndPatientId() {
        HisPatientQuery.SearchResult byMobile = patientQuery.search("mobile", "138-0000-1111");
        assertEquals(1, byMobile.patients().size());
        assertEquals("P001", byMobile.patients().get(0).externalPatientId());

        HisPatientQuery.SearchResult inferred = patientQuery.search(null, "13800001111");
        assertEquals("mobile", inferred.matchType());
        assertEquals(1, inferred.patients().size());

        HisPatientQuery.SearchResult byMrn = patientQuery.search("mrn", "MRN002");
        assertEquals("P002", byMrn.patients().get(0).externalPatientId());

        HisPatientQuery.SearchResult byId = patientQuery.search("his_patient_id", "P001");
        assertEquals(1, byId.patients().size());
    }

    @Test
    void treatsInjectionLikeInputAsPlainParameter() {
        HisPatientQuery.SearchResult result = patientQuery.search("any", "' or 1=1 --");
        assertEquals(0, result.patients().size());
        assertEquals("any", result.matchType());
    }

    @Test
    void aggregatesPatient360WithVisitsAndAdmissions() {
        HisPatientQuery.Patient360Raw raw = patientQuery.load360("P001");

        assertEquals("张三", raw.patient().name());
        assertEquals(2, raw.visits().size());
        assertEquals("V002", raw.visits().get(0).visitId());
        assertEquals(1, raw.admissions().size());
        assertEquals("A001", raw.admissions().get(0).admissionId());
    }

    @Test
    void findsPatientForManualLink() {
        assertTrue(patientQuery.findPatient("P001").isPresent());
        assertTrue(patientQuery.findPatient("NOT_EXISTS").isEmpty());
    }

    @Test
    void failsClosedWhenNotConfigured() {
        HisProperties disabled = new HisProperties();
        HisConnectionProvider provider = new HisConnectionProvider(disabled);
        HisPatientQuery query = new HisPatientQuery(disabled, provider,
                new HisQueryBuilder(), new HisCircuitBreaker(5, 30_000, 1));
        try {
            query.search("mobile", "13800001111");
        } catch (com.hospital.crm.common.exception.BusinessException exception) {
            assertEquals(com.hospital.crm.common.api.ErrorCode.SERVICE_UNAVAILABLE,
                    exception.getErrorCode());
            return;
        } finally {
            provider.destroy();
        }
        throw new AssertionError("HIS 未配置时查询必须失败关闭");
    }
}
