package com.hospital.crm.his.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * HIS 集成配置。视图与字段由医院现场确认，因此全部可配置；
 * 默认值仅为示例视图，不包含任何真实医院表结构或患者数据。
 */
@ConfigurationProperties(prefix = "crm.his")
public class HisProperties {

    private boolean enabled = false;
    private String url;
    private String username;
    private String password;
    private String driverClassName = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    private String sourceSystem = "HIS";
    private int maximumPoolSize = 5;
    private int minimumIdle = 1;
    private long connectionTimeoutMs = 3000;
    private int queryTimeoutSeconds = 5;
    private int searchLimit = 20;
    private int detailLimit = 20;
    private int snapshotValidMinutes = 30;
    private int snapshotMaxStaleMinutes = 1440;
    private boolean readOnlyCheck = true;
    private CircuitBreaker circuitBreaker = new CircuitBreaker();
    private PatientView patientView = new PatientView();
    private VisitView visitView = new VisitView();
    private AdmissionView admissionView = new AdmissionView();

    public boolean configured() {
        return enabled && url != null && !url.isBlank();
    }

    /**
     * 允许查询的视图白名单，SQL 守卫会拒绝白名单之外的任何表或视图。
     */
    public Set<String> allowedTables() {
        Set<String> tables = new LinkedHashSet<>();
        addIfPresent(tables, patientView.getTable());
        addIfPresent(tables, visitView.getTable());
        addIfPresent(tables, admissionView.getTable());
        return tables;
    }

    private void addIfPresent(Set<String> tables, String table) {
        if (table != null && !table.isBlank()) {
            tables.add(table.trim());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public void setMaximumPoolSize(int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
    }

    public int getMinimumIdle() {
        return minimumIdle;
    }

    public void setMinimumIdle(int minimumIdle) {
        this.minimumIdle = minimumIdle;
    }

    public long getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public void setConnectionTimeoutMs(long connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    public int getQueryTimeoutSeconds() {
        return queryTimeoutSeconds;
    }

    public void setQueryTimeoutSeconds(int queryTimeoutSeconds) {
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    public int getSearchLimit() {
        return searchLimit;
    }

    public void setSearchLimit(int searchLimit) {
        this.searchLimit = searchLimit;
    }

    public int getDetailLimit() {
        return detailLimit;
    }

    public void setDetailLimit(int detailLimit) {
        this.detailLimit = detailLimit;
    }

    public int getSnapshotValidMinutes() {
        return snapshotValidMinutes;
    }

    public void setSnapshotValidMinutes(int snapshotValidMinutes) {
        this.snapshotValidMinutes = snapshotValidMinutes;
    }

    public int getSnapshotMaxStaleMinutes() {
        return snapshotMaxStaleMinutes;
    }

    public void setSnapshotMaxStaleMinutes(int snapshotMaxStaleMinutes) {
        this.snapshotMaxStaleMinutes = snapshotMaxStaleMinutes;
    }

    public boolean isReadOnlyCheck() {
        return readOnlyCheck;
    }

    public void setReadOnlyCheck(boolean readOnlyCheck) {
        this.readOnlyCheck = readOnlyCheck;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public void setCircuitBreaker(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public PatientView getPatientView() {
        return patientView;
    }

    public void setPatientView(PatientView patientView) {
        this.patientView = patientView;
    }

    public VisitView getVisitView() {
        return visitView;
    }

    public void setVisitView(VisitView visitView) {
        this.visitView = visitView;
    }

    public AdmissionView getAdmissionView() {
        return admissionView;
    }

    public void setAdmissionView(AdmissionView admissionView) {
        this.admissionView = admissionView;
    }

    public static class CircuitBreaker {
        private int failureThreshold = 5;
        private int openSeconds = 30;
        private int halfOpenProbes = 1;

        public int getFailureThreshold() {
            return failureThreshold;
        }

        public void setFailureThreshold(int failureThreshold) {
            this.failureThreshold = failureThreshold;
        }

        public int getOpenSeconds() {
            return openSeconds;
        }

        public void setOpenSeconds(int openSeconds) {
            this.openSeconds = openSeconds;
        }

        public int getHalfOpenProbes() {
            return halfOpenProbes;
        }

        public void setHalfOpenProbes(int halfOpenProbes) {
            this.halfOpenProbes = halfOpenProbes;
        }
    }

    public static class PatientView {
        private String table = "dbo.v_crm_patient";
        private String patientIdColumn = "patient_id";
        private String mrnColumn = "mrn";
        private String nameColumn = "patient_name";
        private String genderColumn = "gender";
        private String birthDateColumn = "birth_date";
        private String mobileColumn = "mobile";
        private String idNoColumn = "id_no";

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public String getPatientIdColumn() {
            return patientIdColumn;
        }

        public void setPatientIdColumn(String patientIdColumn) {
            this.patientIdColumn = patientIdColumn;
        }

        public String getMrnColumn() {
            return mrnColumn;
        }

        public void setMrnColumn(String mrnColumn) {
            this.mrnColumn = mrnColumn;
        }

        public String getNameColumn() {
            return nameColumn;
        }

        public void setNameColumn(String nameColumn) {
            this.nameColumn = nameColumn;
        }

        public String getGenderColumn() {
            return genderColumn;
        }

        public void setGenderColumn(String genderColumn) {
            this.genderColumn = genderColumn;
        }

        public String getBirthDateColumn() {
            return birthDateColumn;
        }

        public void setBirthDateColumn(String birthDateColumn) {
            this.birthDateColumn = birthDateColumn;
        }

        public String getMobileColumn() {
            return mobileColumn;
        }

        public void setMobileColumn(String mobileColumn) {
            this.mobileColumn = mobileColumn;
        }

        public String getIdNoColumn() {
            return idNoColumn;
        }

        public void setIdNoColumn(String idNoColumn) {
            this.idNoColumn = idNoColumn;
        }
    }

    public static class VisitView {
        private String table = "dbo.v_crm_visit";
        private String patientIdColumn = "patient_id";
        private String visitIdColumn = "visit_id";
        private String visitTimeColumn = "visit_time";
        private String departmentColumn = "department";
        private String doctorColumn = "doctor_name";
        private String diagnosisColumn = "diagnosis";

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public String getPatientIdColumn() {
            return patientIdColumn;
        }

        public void setPatientIdColumn(String patientIdColumn) {
            this.patientIdColumn = patientIdColumn;
        }

        public String getVisitIdColumn() {
            return visitIdColumn;
        }

        public void setVisitIdColumn(String visitIdColumn) {
            this.visitIdColumn = visitIdColumn;
        }

        public String getVisitTimeColumn() {
            return visitTimeColumn;
        }

        public void setVisitTimeColumn(String visitTimeColumn) {
            this.visitTimeColumn = visitTimeColumn;
        }

        public String getDepartmentColumn() {
            return departmentColumn;
        }

        public void setDepartmentColumn(String departmentColumn) {
            this.departmentColumn = departmentColumn;
        }

        public String getDoctorColumn() {
            return doctorColumn;
        }

        public void setDoctorColumn(String doctorColumn) {
            this.doctorColumn = doctorColumn;
        }

        public String getDiagnosisColumn() {
            return diagnosisColumn;
        }

        public void setDiagnosisColumn(String diagnosisColumn) {
            this.diagnosisColumn = diagnosisColumn;
        }
    }

    public static class AdmissionView {
        private String table = "dbo.v_crm_admission";
        private String patientIdColumn = "patient_id";
        private String admissionIdColumn = "admission_id";
        private String admittedAtColumn = "admitted_at";
        private String dischargedAtColumn = "discharged_at";
        private String wardColumn = "ward";
        private String diagnosisColumn = "diagnosis";

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public String getPatientIdColumn() {
            return patientIdColumn;
        }

        public void setPatientIdColumn(String patientIdColumn) {
            this.patientIdColumn = patientIdColumn;
        }

        public String getAdmissionIdColumn() {
            return admissionIdColumn;
        }

        public void setAdmissionIdColumn(String admissionIdColumn) {
            this.admissionIdColumn = admissionIdColumn;
        }

        public String getAdmittedAtColumn() {
            return admittedAtColumn;
        }

        public void setAdmittedAtColumn(String admittedAtColumn) {
            this.admittedAtColumn = admittedAtColumn;
        }

        public String getDischargedAtColumn() {
            return dischargedAtColumn;
        }

        public void setDischargedAtColumn(String dischargedAtColumn) {
            this.dischargedAtColumn = dischargedAtColumn;
        }

        public String getWardColumn() {
            return wardColumn;
        }

        public void setWardColumn(String wardColumn) {
            this.wardColumn = wardColumn;
        }

        public String getDiagnosisColumn() {
            return diagnosisColumn;
        }

        public void setDiagnosisColumn(String diagnosisColumn) {
            this.diagnosisColumn = diagnosisColumn;
        }
    }
}
