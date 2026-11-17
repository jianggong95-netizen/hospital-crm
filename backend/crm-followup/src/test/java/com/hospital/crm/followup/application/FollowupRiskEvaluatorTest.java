package com.hospital.crm.followup.application;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 风险规则评估：高风险优先、其次中风险，未命中为低风险。
 */
class FollowupRiskEvaluatorTest {

    private final Map<String, Object> rules = Map.of(
            "highRisk", List.of(
                    Map.of("questionCode", "condition", "values", List.of("加重")),
                    Map.of("questionCode", "readmission", "values", List.of("是"))),
            "mediumRisk", List.of(
                    Map.of("questionCode", "medication", "values", List.of("否"))));

    @Test
    void returnsHighWhenAnyHighRiskAnswerMatched() {
        assertThat(FollowupRiskEvaluator.evaluate(rules, Map.of("condition", "加重")))
                .isEqualTo("HIGH");
        assertThat(FollowupRiskEvaluator.evaluate(rules, Map.of("readmission", "是")))
                .isEqualTo("HIGH");
    }

    @Test
    void highRiskWinsOverMediumRisk() {
        assertThat(FollowupRiskEvaluator.evaluate(rules,
                Map.of("condition", "加重", "medication", "否")))
                .isEqualTo("HIGH");
    }

    @Test
    void returnsMediumWhenOnlyMediumRiskMatched() {
        assertThat(FollowupRiskEvaluator.evaluate(rules, Map.of("medication", "否")))
                .isEqualTo("MEDIUM");
    }

    @Test
    void returnsLowWhenNoRuleMatched() {
        assertThat(FollowupRiskEvaluator.evaluate(rules,
                Map.of("condition", "良好", "medication", "是")))
                .isEqualTo("LOW");
    }

    @Test
    void returnsLowWhenRulesOrAnswersMissing() {
        assertThat(FollowupRiskEvaluator.evaluate(null, Map.of("condition", "加重")))
                .isEqualTo("LOW");
        assertThat(FollowupRiskEvaluator.evaluate(rules, Map.of()))
                .isEqualTo("LOW");
        assertThat(FollowupRiskEvaluator.evaluate(Map.of(), Map.of("condition", "加重")))
                .isEqualTo("LOW");
    }
}
