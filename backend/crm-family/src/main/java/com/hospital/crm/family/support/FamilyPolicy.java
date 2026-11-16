package com.hospital.crm.family.support;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 家庭账户领域规则：关系类型、代办范围与有效期判断。
 * 代办范围仅限非临床事务，病历与临床数据不允许纳入家庭授权。
 */
public final class FamilyPolicy {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_ENDED = "ended";

    private static final Map<String, String> RELATIONSHIP_LABELS = new LinkedHashMap<>();
    private static final Map<String, String> DELEGATION_SCOPE_LABELS = new LinkedHashMap<>();
    private static final Set<String> MEDICAL_KEYWORDS = Set.of(
            "medical", "clinical", "record", "diagnosis", "treatment", "病历", "临床", "诊断", "就诊", "检验", "影像");

    static {
        RELATIONSHIP_LABELS.put("self", "本人");
        RELATIONSHIP_LABELS.put("spouse", "配偶");
        RELATIONSHIP_LABELS.put("parent", "父母");
        RELATIONSHIP_LABELS.put("child", "子女");
        RELATIONSHIP_LABELS.put("sibling", "兄弟姐妹");
        RELATIONSHIP_LABELS.put("grandparent", "祖父母/外祖父母");
        RELATIONSHIP_LABELS.put("grandchild", "孙子女/外孙子女");
        RELATIONSHIP_LABELS.put("other", "其他亲属");

        DELEGATION_SCOPE_LABELS.put("appointment", "代预约");
        DELEGATION_SCOPE_LABELS.put("payment", "代支付");
        DELEGATION_SCOPE_LABELS.put("package_share", "共享套餐");
        DELEGATION_SCOPE_LABELS.put("profile_update", "资料维护");
    }

    private FamilyPolicy() {
    }

    public static Set<String> relationshipCodes() {
        return RELATIONSHIP_LABELS.keySet();
    }

    public static Set<String> delegationScopes() {
        return DELEGATION_SCOPE_LABELS.keySet();
    }

    public static String relationshipLabel(String code) {
        return code == null ? "—" : RELATIONSHIP_LABELS.getOrDefault(code, code);
    }

    public static String delegationScopeLabel(String code) {
        return code == null ? "—" : DELEGATION_SCOPE_LABELS.getOrDefault(code, code);
    }

    public static String requireRelationshipCode(String raw) {
        String code = raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
        if (code == null || code.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "家庭关系类型不能为空");
        }
        if (!RELATIONSHIP_LABELS.containsKey(code)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "家庭关系类型不合法，支持：" + String.join("、", RELATIONSHIP_LABELS.keySet()));
        }
        return code;
    }

    /**
     * 校验代办范围。病历、临床数据等医疗信息永不允许作为家庭代办范围。
     */
    public static String requireDelegationScope(String raw) {
        String code = raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
        if (code == null || code.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "代办范围不能为空");
        }
        if (containsMedicalKeyword(code)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    "家庭关系不构成病历访问授权，禁止将病历、诊断、检验等临床数据纳入代办范围");
        }
        if (!DELEGATION_SCOPE_LABELS.containsKey(code)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "代办范围不合法，支持：" + String.join("、", DELEGATION_SCOPE_LABELS.keySet()));
        }
        return code;
    }

    public static boolean isEffective(String status, Instant validFrom, Instant validTo, Instant now) {
        if (!STATUS_ACTIVE.equals(status)) {
            return false;
        }
        if (validFrom != null && now.isBefore(validFrom)) {
            return false;
        }
        return validTo == null || now.isBefore(validTo);
    }

    private static boolean containsMedicalKeyword(String code) {
        return MEDICAL_KEYWORDS.stream().anyMatch(code::contains);
    }
}
