package com.hospital.crm.his.support;

import java.time.LocalDate;
import java.util.List;

/**
 * 模拟 HIS 患者源：crm.his.enabled=false（未接真实 HIS）时为同步/演示提供内置数据。
 * 接口契约与真实视图一致（主索引号、病案号、姓名、性别、出生日期、手机号、证件号），
 * 将来接入真实 HIS 后仅需把 {@link HisPatientQuery} 的模拟分支换成 JDBC 查询即可。
 * 全部为虚构演示数据，不含任何真实患者信息。
 *
 * 数据构成（共 15 人）：
 * 1. 前 8 人与 CRM 现有演示客户重合——王建国/李秀兰等按手机号 13900000001~13900000007 对上，
 *    张伟按姓名+出生日期对上，同步时应"匹配绑定"而非新建；
 * 2. 后 7 人手机号统一使用 137 段（CRM 未建档），同步时新建客户。
 */
public final class SimulatedHisSource {

    private static final List<HisPatientQuery.PatientRow> PATIENTS = List.of(
            // ---- 与 CRM 演示客户重合（8 人）----
            new HisPatientQuery.PatientRow("HIS-P-10001", "MRN10001", "王建国",
                    "male", LocalDate.of(1962, 3, 15), "13900000001", "110101196203150011"),
            new HisPatientQuery.PatientRow("HIS-P-10002", "MRN10002", "李秀兰",
                    "female", LocalDate.of(1975, 7, 22), "13900000002", "110101197507220022"),
            new HisPatientQuery.PatientRow("HIS-P-10003", "MRN10003", "张伟",
                    "male", LocalDate.of(1988, 11, 2), "13700000001", "310101198811020033"),
            new HisPatientQuery.PatientRow("HIS-P-10004", "MRN10004", "陈玉兰",
                    "female", LocalDate.of(1968, 5, 9), "13900000003", "320101196805090044"),
            new HisPatientQuery.PatientRow("HIS-P-10005", "MRN10005", "刘志强",
                    "male", LocalDate.of(1955, 8, 27), "13900000004", "440101195508270055"),
            new HisPatientQuery.PatientRow("HIS-P-10006", "MRN10006", "赵文华",
                    "female", LocalDate.of(1982, 1, 13), "13900000005", "510101198201130066"),
            new HisPatientQuery.PatientRow("HIS-P-10007", "MRN10007", "孙建军",
                    "male", LocalDate.of(1990, 10, 5), "13900000006", "330101199010050077"),
            new HisPatientQuery.PatientRow("HIS-P-10008", "MRN10008", "周秀英",
                    "female", LocalDate.of(1948, 12, 19), "13900000007", "120101194812190088"),
            // ---- CRM 未建档，同步时新建（7 人）----
            new HisPatientQuery.PatientRow("HIS-P-10009", "MRN10009", "周慧敏",
                    "female", LocalDate.of(1985, 2, 18), "13711120001", "350101198502180099"),
            new HisPatientQuery.PatientRow("HIS-P-10010", "MRN10010", "孙志强",
                    "male", LocalDate.of(1972, 9, 30), "13711120002", "610101197209300100"),
            new HisPatientQuery.PatientRow("HIS-P-10011", "MRN10011", "吴桂香",
                    "female", LocalDate.of(1958, 12, 5), "13711120003", "220101195812050111"),
            new HisPatientQuery.PatientRow("HIS-P-10012", "MRN10012", "郑一诺",
                    "female", LocalDate.of(2010, 6, 21), "13711120004", "420101201006210122"),
            new HisPatientQuery.PatientRow("HIS-P-10013", "MRN10013", "冯建华",
                    "male", LocalDate.of(1969, 4, 12), "13711120005", "370101196904120133"),
            new HisPatientQuery.PatientRow("HIS-P-10014", "MRN10014", "祝美玲",
                    "female", LocalDate.of(1993, 7, 8), "13711120006", "450101199307080144"),
            new HisPatientQuery.PatientRow("HIS-P-10015", "MRN10015", "何敬仲",
                    "male", LocalDate.of(1945, 3, 30), "13711120007", "140101194503300155")
    );

    private SimulatedHisSource() {
    }

    public static int total() {
        return PATIENTS.size();
    }

    /**
     * 按 offset/limit 返回一页模拟患者（原始值，供同步匹配使用；对外暴露前必须脱敏）。
     */
    public static List<HisPatientQuery.PatientRow> page(int offset, int limit) {
        if (offset < 0 || offset >= PATIENTS.size() || limit <= 0) {
            return List.of();
        }
        int from = offset;
        int to = Math.min(from + limit, PATIENTS.size());
        return List.copyOf(PATIENTS.subList(from, to));
    }
}
