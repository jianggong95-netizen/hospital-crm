package com.hospital.crm.insurance.support;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;

import java.util.Map;
import java.util.Set;

/**
 * 商保案件状态机。仅允许以下迁移，其余一律 422：
 * registered → eligibility_check → quotation → pre_authorization → service_done → claim_submitted
 * → supplement_required → under_review；claim_submitted 亦可直接进入 under_review；
 * under_review → approved | partially_approved | rejected；三种理赔结论均可进入 reconciled。
 * reconciled 为终态。
 */
public final class InsuranceStateMachine {

    public static final String REGISTERED = "registered";
    public static final String ELIGIBILITY_CHECK = "eligibility_check";
    public static final String QUOTATION = "quotation";
    public static final String PRE_AUTHORIZATION = "pre_authorization";
    public static final String SERVICE_DONE = "service_done";
    public static final String CLAIM_SUBMITTED = "claim_submitted";
    public static final String SUPPLEMENT_REQUIRED = "supplement_required";
    public static final String UNDER_REVIEW = "under_review";
    public static final String APPROVED = "approved";
    public static final String PARTIALLY_APPROVED = "partially_approved";
    public static final String REJECTED = "rejected";
    public static final String RECONCILED = "reconciled";

    public static final Set<String> STATES = Set.of(
            REGISTERED, ELIGIBILITY_CHECK, QUOTATION, PRE_AUTHORIZATION, SERVICE_DONE,
            CLAIM_SUBMITTED, SUPPLEMENT_REQUIRED, UNDER_REVIEW, APPROVED,
            PARTIALLY_APPROVED, REJECTED, RECONCILED);

    private static final Map<String, Set<String>> TRANSITIONS = Map.ofEntries(
            Map.entry(REGISTERED, Set.of(ELIGIBILITY_CHECK)),
            Map.entry(ELIGIBILITY_CHECK, Set.of(QUOTATION)),
            Map.entry(QUOTATION, Set.of(PRE_AUTHORIZATION)),
            Map.entry(PRE_AUTHORIZATION, Set.of(SERVICE_DONE)),
            Map.entry(SERVICE_DONE, Set.of(CLAIM_SUBMITTED)),
            Map.entry(CLAIM_SUBMITTED, Set.of(SUPPLEMENT_REQUIRED, UNDER_REVIEW)),
            Map.entry(SUPPLEMENT_REQUIRED, Set.of(UNDER_REVIEW)),
            Map.entry(UNDER_REVIEW, Set.of(APPROVED, PARTIALLY_APPROVED, REJECTED)),
            Map.entry(APPROVED, Set.of(RECONCILED)),
            Map.entry(PARTIALLY_APPROVED, Set.of(RECONCILED)),
            Map.entry(REJECTED, Set.of(RECONCILED))
    );

    private InsuranceStateMachine() {
    }

    public static boolean canTransit(String from, String to) {
        return from != null && to != null
                && TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireTransition(String from, String to) {
        if (!STATES.contains(to) || !canTransit(from, to)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    String.format("不允许从 %s 流转到 %s", from, to));
        }
    }
}
