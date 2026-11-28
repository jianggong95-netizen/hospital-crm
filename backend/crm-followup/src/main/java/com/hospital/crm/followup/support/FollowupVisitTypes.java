package com.hospital.crm.followup.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 五大回访板块常量：编码、中文标签与展示顺序（V21 迁移定义的 visit_type 取值）。
 * UNKNOWN 为统计兜底：任务分类被删除或历史数据缺失板块时归入"未分类"。
 */
public final class FollowupVisitTypes {

    public static final String OUTPATIENT = "OUTPATIENT";
    public static final String INPATIENT = "INPATIENT";
    public static final String SURGERY = "SURGERY";
    public static final String EXAM = "EXAM";
    public static final String MARKETING = "MARKETING";
    public static final String UNKNOWN = "UNKNOWN";

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put(OUTPATIENT, "门诊回访");
        LABELS.put(INPATIENT, "住院回访");
        LABELS.put(SURGERY, "手术回访");
        LABELS.put(EXAM, "体检回访");
        LABELS.put(MARKETING, "营销回访");
        LABELS.put(UNKNOWN, "未分类");
    }

    private FollowupVisitTypes() {
    }

    /** 板块展示顺序（未识别编码排在最后）。 */
    public static List<String> orderedCodes() {
        return List.copyOf(LABELS.keySet());
    }

    public static boolean isValid(String code) {
        return code != null && LABELS.containsKey(code) && !UNKNOWN.equals(code);
    }

    public static String label(String code) {
        return code == null ? LABELS.get(UNKNOWN) : LABELS.getOrDefault(code, code);
    }
}
