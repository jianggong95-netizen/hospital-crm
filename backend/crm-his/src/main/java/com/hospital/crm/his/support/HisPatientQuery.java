package com.hospital.crm.his.support;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.his.api.HisDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

/**
 * HIS 只读查询执行器：统一处理熔断、超时异常与上游错误转换。
 * 所有查询均通过 {@link HisQueryBuilder} 构建并经过 SQL 守卫校验。
 */
@Component
public class HisPatientQuery {

    private static final Logger log = LoggerFactory.getLogger(HisPatientQuery.class);
    private static final BigDecimal FETCH_CONFIDENCE = new BigDecimal("0.9900");

    private final HisProperties properties;
    private final HisConnectionProvider connectionProvider;
    private final HisQueryBuilder queryBuilder;
    private final HisCircuitBreaker circuitBreaker;

    public HisPatientQuery(HisProperties properties,
                           HisConnectionProvider connectionProvider,
                           HisQueryBuilder queryBuilder,
                           HisCircuitBreaker circuitBreaker) {
        this.properties = properties;
        this.connectionProvider = connectionProvider;
        this.queryBuilder = queryBuilder;
        this.circuitBreaker = circuitBreaker;
    }

    public record PatientRow(
            String externalPatientId,
            String mrn,
            String name,
            String gender,
            LocalDate birthDate,
            String mobile,
            String idNo
    ) {
    }

    /**
     * 同步用原始患者分页（含明文姓名/手机号，仅限服务内部匹配，禁止直接对外返回）。
     */
    public record PatientRowPage(
            List<PatientRow> items,
            boolean hasMore,
            int total
    ) {
    }

    public record VisitRow(
            String visitId,
            Instant visitTime,
            String department,
            String doctor,
            String diagnosis
    ) {
    }

    public record AdmissionRow(
            String admissionId,
            Instant admittedAt,
            Instant dischargedAt,
            String ward,
            String diagnosis
    ) {
    }

    public record Patient360Raw(
            PatientRow patient,
            List<VisitRow> visits,
            List<AdmissionRow> admissions
    ) {
    }

    public record SearchResult(List<PatientRow> patients, String matchType) {
    }

    public SearchResult search(String identityType, String keyword) {
        return execute("PATIENT_SEARCH", () -> {
            HisQueryBuilder.SqlQuery query = queryBuilder.buildSearch(
                    properties, identityType, keyword, properties.getSearchLimit());
            List<PatientRow> patients = connectionProvider.template()
                    .query(query.sql(), query.parameters(), patientRowMapper());
            return new SearchResult(patients, query.matchType());
        });
    }

    public java.util.Optional<PatientRow> findPatient(String externalPatientId) {
        return execute("PATIENT_LOOKUP", () -> {
            HisQueryBuilder.SqlQuery query = queryBuilder.buildPatientById(properties, externalPatientId);
            return connectionProvider.template()
                    .query(query.sql(), query.parameters(), patientRowMapper())
                    .stream()
                    .findFirst();
        });
    }

    /**
     * 全量患者分页拉取（固定接口契约）：真实模式按 crm.his.sync.patient-view 视图分页查询；
     * 模拟模式返回 {@link SimulatedHisSource} 内置患者。对外只暴露脱敏候选。
     */
    public HisDtos.PatientPage fetchAllPatients(int offset, int limit) {
        PatientRowPage rows = fetchPatientRows(offset, limit);
        Instant fetchedAt = Instant.now();
        List<HisDtos.PatientCandidate> items = rows.items().stream()
                .map(patient -> new HisDtos.PatientCandidate(
                        patient.externalPatientId(),
                        patient.mrn(),
                        HisMasking.maskName(patient.name()),
                        patient.gender(),
                        patient.birthDate(),
                        HisMasking.maskPhone(patient.mobile()),
                        "his_patient_id",
                        FETCH_CONFIDENCE,
                        false,
                        fetchedAt))
                .toList();
        return new HisDtos.PatientPage(items, rows.hasMore(), rows.total());
    }

    /**
     * 全量患者分页拉取（原始值）：供 HIS 同步任务做手机号/证件号等精确匹配，
     * 分支逻辑与 {@link #fetchAllPatients} 一致。
     */
    public PatientRowPage fetchPatientRows(int offset, int limit) {
        int safeOffset = Math.max(offset, 0);
        int safeLimit = Math.min(Math.max(limit, 1), 1000);
        if (!properties.configured()) {
            List<PatientRow> items = SimulatedHisSource.page(safeOffset, safeLimit);
            int total = SimulatedHisSource.total();
            return new PatientRowPage(items, safeOffset + items.size() < total, total);
        }
        return execute("PATIENT_PAGE", () -> {
            HisQueryBuilder.SqlQuery query = queryBuilder.buildPatientPage(properties, safeOffset, safeLimit);
            List<PatientRow> items = connectionProvider.template()
                    .query(query.sql(), query.parameters(), patientRowMapper());
            HisQueryBuilder.SqlQuery countQuery = queryBuilder.buildPatientCount(properties);
            Integer total = connectionProvider.template()
                    .queryForObject(countQuery.sql(), countQuery.parameters(), Integer.class);
            int safeTotal = total == null ? items.size() : total;
            return new PatientRowPage(items, safeOffset + items.size() < safeTotal, safeTotal);
        });
    }

    public Patient360Raw load360(String externalPatientId) {
        return execute("PATIENT_360", () -> {
            NamedParameterJdbcTemplate template = connectionProvider.template();

            HisQueryBuilder.SqlQuery patientQuery = queryBuilder.buildPatientById(properties, externalPatientId);
            List<PatientRow> patients = template.query(
                    patientQuery.sql(), patientQuery.parameters(), patientRowMapper());
            if (patients.isEmpty()) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "HIS 中未找到该患者主索引记录");
            }

            HisQueryBuilder.SqlQuery visitQuery = queryBuilder.buildVisits(
                    properties, externalPatientId, properties.getDetailLimit());
            List<VisitRow> visits = template.query(visitQuery.sql(), visitQuery.parameters(), visitRowMapper());

            HisQueryBuilder.SqlQuery admissionQuery = queryBuilder.buildAdmissions(
                    properties, externalPatientId, properties.getDetailLimit());
            List<AdmissionRow> admissions = template.query(
                    admissionQuery.sql(), admissionQuery.parameters(), admissionRowMapper());

            return new Patient360Raw(patients.get(0), visits, admissions);
        });
    }

    private <T> T execute(String operation, Supplier<T> action) {
        if (!circuitBreaker.tryAcquire()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "HIS 服务连续异常，已进入熔断保护，请稍后重试");
        }
        try {
            T result = action.get();
            circuitBreaker.recordSuccess();
            return result;
        } catch (DataAccessException ex) {
            circuitBreaker.recordFailure();
            log.warn("HIS 查询失败，operation={}, state={}", operation, circuitBreaker.state(), ex);
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "HIS 查询失败或超时，请稍后重试");
        } catch (BusinessException ex) {
            if (circuitBreaker.state() == HisCircuitBreaker.State.HALF_OPEN) {
                circuitBreaker.recordFailure();
            }
            throw ex;
        } catch (RuntimeException ex) {
            circuitBreaker.recordFailure();
            log.warn("HIS 查询异常，operation={}, state={}", operation, circuitBreaker.state(), ex);
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "HIS 查询异常，请稍后重试");
        }
    }

    private RowMapper<PatientRow> patientRowMapper() {
        return (resultSet, rowNum) -> new PatientRow(
                resultSet.getString("patient_id"),
                resultSet.getString("mrn"),
                resultSet.getString("patient_name"),
                resultSet.getString("gender"),
                toLocalDate(resultSet.getDate("birth_date")),
                resultSet.getString("mobile"),
                resultSet.getString("id_no"));
    }

    private RowMapper<VisitRow> visitRowMapper() {
        return (resultSet, rowNum) -> new VisitRow(
                resultSet.getString("visit_id"),
                toInstant(resultSet.getTimestamp("visit_time")),
                resultSet.getString("department"),
                resultSet.getString("doctor_name"),
                resultSet.getString("diagnosis"));
    }

    private RowMapper<AdmissionRow> admissionRowMapper() {
        return (resultSet, rowNum) -> new AdmissionRow(
                resultSet.getString("admission_id"),
                toInstant(resultSet.getTimestamp("admitted_at")),
                toInstant(resultSet.getTimestamp("discharged_at")),
                resultSet.getString("ward"),
                resultSet.getString("diagnosis"));
    }

    private LocalDate toLocalDate(Date value) {
        return value == null ? null : value.toLocalDate();
    }

    private Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
