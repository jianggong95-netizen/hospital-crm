package com.hospital.crm.followup.application;

import java.util.List;
import java.util.Map;

/**
 * 随访风险规则评估。规则结构：
 * {"highRisk":[{"questionCode":"condition","values":["加重"]}],
 *  "mediumRisk":[{"questionCode":"medication","values":["否"]}]}。
 * 命中任一高风险规则即判为 HIGH，其次 MEDIUM，否则 LOW。
 */
public final class FollowupRiskEvaluator {

    public static final String HIGH = "HIGH";
    public static final String MEDIUM = "MEDIUM";
    public static final String LOW = "LOW";

    private FollowupRiskEvaluator() {
    }

    public static String evaluate(Map<String, Object> rules, Map<String, String> answers) {
        if (rules == null || answers == null || answers.isEmpty()) {
            return LOW;
        }
        if (matches(rules.get("highRisk"), answers)) {
            return HIGH;
        }
        if (matches(rules.get("mediumRisk"), answers)) {
            return MEDIUM;
        }
        return LOW;
    }

    private static boolean matches(Object ruleNode, Map<String, String> answers) {
        if (!(ruleNode instanceof List<?> rules)) {
            return false;
        }
        for (Object item : rules) {
            if (!(item instanceof Map<?, ?> rule)) {
                continue;
            }
            Object questionCode = rule.get("questionCode");
            Object allowedValues = rule.get("values");
            if (questionCode == null || !(allowedValues instanceof List<?> allowed)) {
                continue;
            }
            String answer = answers.get(questionCode.toString());
            if (answer == null) {
                continue;
            }
            for (Object value : allowed) {
                if (value != null && value.toString().equals(answer)) {
                    return true;
                }
            }
        }
        return false;
    }
}
