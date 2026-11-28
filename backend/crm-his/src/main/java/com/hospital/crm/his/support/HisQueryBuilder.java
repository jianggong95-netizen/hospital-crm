package com.hospital.crm.his.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 根据可配置的 HIS 视图映射构建参数化只读 SQL。
 * 所有表名、字段名先经过 {@link HisSqlGuard} 校验，值一律走命名参数绑定。
 */
public class HisQueryBuilder {

    public static final Set<String> IDENTITY_TYPES =
            Set.of("mobile", "id_no", "his_patient_id", "mrn", "any");

    public record SqlQuery(String sql, Map<String, Object> parameters, String matchType) {
    }

    public SqlQuery buildSearch(HisProperties properties, String identityType, String keyword, int limit) {
        HisProperties.PatientView view = properties.getPatientView();
        String table = qualifyTable(view.getTable(), "患者视图");
        String resolvedType = resolveIdentityType(identityType, keyword);
        int safeLimit = clampLimit(limit, properties.getSearchLimit());

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("limit", safeLimit);

        String condition;
        switch (resolvedType) {
            case "mobile" -> {
                requireColumn(view.getMobileColumn(), "患者视图手机号字段");
                condition = alias("p", view.getMobileColumn()) + " = :value";
                parameters.put("value", HisMasking.normalizePhone(keyword));
            }
            case "id_no" -> {
                requireColumn(view.getIdNoColumn(), "患者视图证件号字段");
                condition = alias("p", view.getIdNoColumn()) + " = :value";
                parameters.put("value", HisMasking.normalizeIdNo(keyword));
            }
            case "mrn" -> {
                requireColumn(view.getMrnColumn(), "患者视图病案号字段");
                condition = alias("p", view.getMrnColumn()) + " = :value";
                parameters.put("value", keyword.trim());
            }
            case "his_patient_id" -> {
                condition = alias("p", view.getPatientIdColumn()) + " = :value";
                parameters.put("value", keyword.trim());
            }
            default -> {
                List<String> likeColumns = new ArrayList<>();
                likeColumns.add(alias("p", view.getPatientIdColumn()));
                if (hasText(view.getMrnColumn())) {
                    likeColumns.add(alias("p", view.getMrnColumn()));
                }
                if (hasText(view.getNameColumn())) {
                    likeColumns.add(alias("p", view.getNameColumn()));
                }
                condition = "(" + String.join(" or ", likeColumns.stream()
                        .map(column -> column + " like :keyword escape '\\'")
                        .toList()) + ")";
                parameters.put("keyword", "%" + escapeLike(keyword.trim()) + "%");
            }
        }

        String sql = "select top (:limit) " + patientColumns(view) + " from " + table + " p where "
                + condition + " order by " + alias("p", view.getPatientIdColumn());
        HisSqlGuard.validateSelect(sql, properties.allowedTables());
        return new SqlQuery(sql, parameters, resolvedType);
    }

    public SqlQuery buildPatientById(HisProperties properties, String externalPatientId) {
        HisProperties.PatientView view = properties.getPatientView();
        String table = qualifyTable(view.getTable(), "患者视图");
        String sql = "select top 1 " + patientColumns(view) + " from " + table + " p where "
                + alias("p", view.getPatientIdColumn()) + " = :patientId";
        HisSqlGuard.validateSelect(sql, properties.allowedTables());
        Map<String, Object> parameters = Map.of("patientId", externalPatientId);
        return new SqlQuery(sql, parameters, "his_patient_id");
    }

    /**
     * 全量患者列表查询：供 HIS 患者同步任务使用，只读、限定白名单视图并限制返回行数。
     */
    public SqlQuery buildPatientList(HisProperties properties, int limit) {
        HisProperties.PatientView view = properties.getPatientView();
        String table = qualifyTable(view.getTable(), "患者视图");
        int safeLimit = Math.min(Math.max(limit, 1), 1000);
        String sql = "select top (:limit) " + patientColumns(view) + " from " + table
                + " p order by " + alias("p", view.getPatientIdColumn());
        HisSqlGuard.validateSelect(sql, properties.allowedTables());
        return new SqlQuery(sql, Map.of("limit", safeLimit), "his_patient_id");
    }

    /**
     * 全量患者分页查询：真实模式下同步任务按 crm.his.sync.patient-view 视图分页读取，
     * SQL Server 2012+ 的 OFFSET/FETCH 语法，只读且经过 SQL 守卫校验。
     */
    public SqlQuery buildPatientPage(HisProperties properties, int offset, int limit) {
        HisProperties.PatientView view = properties.getPatientView();
        String table = qualifyTable(properties.getSync().getPatientView(), "同步患者视图");
        int safeOffset = Math.max(offset, 0);
        int safeLimit = Math.min(Math.max(limit, 1), 1000);
        String sql = "select " + patientColumns(view) + " from " + table + " p order by "
                + alias("p", view.getPatientIdColumn()) + " offset :offset row fetch next :limit row only";
        HisSqlGuard.validateSelect(sql, properties.allowedTables());
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("offset", safeOffset);
        parameters.put("limit", safeLimit);
        return new SqlQuery(sql, parameters, "his_patient_id");
    }

    /**
     * 全量患者总数查询：与 {@link #buildPatientPage} 使用同一视图，供分页 hasMore 判定。
     */
    public SqlQuery buildPatientCount(HisProperties properties) {
        String table = qualifyTable(properties.getSync().getPatientView(), "同步患者视图");
        String sql = "select count(*) as [total] from " + table + " p";
        HisSqlGuard.validateSelect(sql, properties.allowedTables());
        return new SqlQuery(sql, Map.of(), "his_patient_id");
    }

    public SqlQuery buildVisits(HisProperties properties, String externalPatientId, int limit) {
        HisProperties.VisitView view = properties.getVisitView();
        String table = qualifyTable(view.getTable(), "就诊视图");
        int safeLimit = clampLimit(limit, properties.getDetailLimit());

        List<String> columns = new ArrayList<>();
        columns.add(alias("v", view.getVisitIdColumn()) + " as [visit_id]");
        columns.add(alias("v", view.getVisitTimeColumn()) + " as [visit_time]");
        columns.add(optionalColumn("v", view.getDepartmentColumn(), "[department]", "varchar(400)"));
        columns.add(optionalColumn("v", view.getDoctorColumn(), "[doctor_name]", "varchar(400)"));
        columns.add(optionalColumn("v", view.getDiagnosisColumn(), "[diagnosis]", "varchar(400)"));

        String sql = "select top (:limit) " + String.join(", ", columns) + " from " + table + " v where "
                + alias("v", view.getPatientIdColumn()) + " = :patientId order by "
                + alias("v", view.getVisitTimeColumn()) + " desc";
        HisSqlGuard.validateSelect(sql, properties.allowedTables());
        return new SqlQuery(sql, Map.of("limit", safeLimit, "patientId", externalPatientId), "his_patient_id");
    }

    public SqlQuery buildAdmissions(HisProperties properties, String externalPatientId, int limit) {
        HisProperties.AdmissionView view = properties.getAdmissionView();
        String table = qualifyTable(view.getTable(), "住院视图");
        int safeLimit = clampLimit(limit, properties.getDetailLimit());

        List<String> columns = new ArrayList<>();
        columns.add(alias("a", view.getAdmissionIdColumn()) + " as [admission_id]");
        columns.add(alias("a", view.getAdmittedAtColumn()) + " as [admitted_at]");
        columns.add(optionalColumn("a", view.getDischargedAtColumn(), "[discharged_at]", "datetime"));
        columns.add(optionalColumn("a", view.getWardColumn(), "[ward]", "varchar(400)"));
        columns.add(optionalColumn("a", view.getDiagnosisColumn(), "[diagnosis]", "varchar(400)"));

        String sql = "select top (:limit) " + String.join(", ", columns) + " from " + table + " a where "
                + alias("a", view.getPatientIdColumn()) + " = :patientId order by "
                + alias("a", view.getAdmittedAtColumn()) + " desc";
        HisSqlGuard.validateSelect(sql, properties.allowedTables());
        return new SqlQuery(sql, Map.of("limit", safeLimit, "patientId", externalPatientId), "his_patient_id");
    }

    public String resolveIdentityType(String requested, String keyword) {
        String type = requested == null ? "" : requested.trim().toLowerCase(Locale.ROOT);
        if (!type.isEmpty()) {
            if (!IDENTITY_TYPES.contains(type)) {
                throw new com.hospital.crm.common.exception.BusinessException(
                        com.hospital.crm.common.api.ErrorCode.VALIDATION_ERROR,
                        "identityType 取值不合法，支持：mobile、id_no、his_patient_id、mrn");
            }
            return type;
        }
        if (HisMasking.looksLikeMobile(keyword)) {
            return "mobile";
        }
        if (HisMasking.looksLikeIdNo(keyword)) {
            return "id_no";
        }
        return "any";
    }

    private String patientColumns(HisProperties.PatientView view) {
        List<String> columns = new ArrayList<>();
        columns.add(alias("p", view.getPatientIdColumn()) + " as [patient_id]");
        columns.add(optionalColumn("p", view.getMrnColumn(), "[mrn]", "varchar(400)"));
        columns.add(alias("p", view.getNameColumn()) + " as [patient_name]");
        columns.add(optionalColumn("p", view.getGenderColumn(), "[gender]", "varchar(400)"));
        columns.add(optionalColumn("p", view.getBirthDateColumn(), "[birth_date]", "date"));
        columns.add(optionalColumn("p", view.getMobileColumn(), "[mobile]", "varchar(400)"));
        columns.add(optionalColumn("p", view.getIdNoColumn(), "[id_no]", "varchar(400)"));
        return String.join(", ", columns);
    }

    private String optionalColumn(String tableAlias, String column, String outputAlias, String nullType) {
        if (!hasText(column)) {
            return "cast(null as " + nullType + ") as " + outputAlias;
        }
        return alias(tableAlias, column) + " as " + outputAlias;
    }

    private String alias(String tableAlias, String column) {
        return tableAlias + "." + quote(column);
    }

    private String qualifyTable(String table, String label) {
        if (!hasText(table)) {
            throw new com.hospital.crm.common.exception.BusinessException(
                    com.hospital.crm.common.api.ErrorCode.SERVICE_UNAVAILABLE, "HIS " + label + "未配置");
        }
        HisSqlGuard.requireIdentifier(table, label);
        return java.util.Arrays.stream(table.split("\\."))
                .map(String::trim)
                .map(part -> HisSqlGuard.requireIdentifier(part, label))
                .map(this::quote)
                .collect(java.util.stream.Collectors.joining("."));
    }

    private String quote(String identifier) {
        return "[" + HisSqlGuard.requireIdentifier(identifier, "字段名") + "]";
    }

    private void requireColumn(String column, String label) {
        if (!hasText(column)) {
            throw new com.hospital.crm.common.exception.BusinessException(
                    com.hospital.crm.common.api.ErrorCode.SERVICE_UNAVAILABLE, "HIS " + label + "未配置");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int clampLimit(int limit, int fallback) {
        int value = limit <= 0 ? fallback : limit;
        return Math.min(Math.max(value, 1), 100);
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
