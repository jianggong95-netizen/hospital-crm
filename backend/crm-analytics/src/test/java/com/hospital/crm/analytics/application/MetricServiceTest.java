package com.hospital.crm.analytics.application;

import com.hospital.crm.analytics.api.AnalyticsDtos;
import com.hospital.crm.analytics.domain.MetricDefinition;
import com.hospital.crm.analytics.domain.MetricDefinitionRepository;
import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.system.audit.AuditService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 指标定义服务：编码唯一冲突、口径说明必填（验收点）、状态启停校验。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetricServiceTest {

    @Mock
    private MetricDefinitionRepository metricRepository;
    @Mock
    private AuditService auditService;

    private MetricService service;
    private ValidatorFactory validatorFactory;

    @BeforeEach
    void setUp() {
        service = new MetricService(metricRepository, auditService);
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    @Test
    void createRejectsDuplicateMetricCode() {
        when(metricRepository.existsByMetricCode("new_customer_count")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new AnalyticsDtos.CreateMetricRequest(
                "new_customer_count", "新增客户数", "customer", "区间内创建客户计数",
                "crm_customer.created_at", "count", 0, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("指标编码已存在")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_CONFLICT);
        verify(metricRepository, never()).save(any());
    }

    @Test
    void caliberDescriptionIsRequired() {
        Validator validator = validatorFactory.getValidator();
        AnalyticsDtos.CreateMetricRequest request = new AnalyticsDtos.CreateMetricRequest(
                "custom_metric", "自定义指标", "operation", "  ", "crm_xxx.created_at",
                "count", 0, null);

        Set<ConstraintViolation<AnalyticsDtos.CreateMetricRequest>> violations =
                validator.validate(request);

        assertThat(violations).anyMatch(v -> "指标口径说明不能为空".equals(v.getMessage()));
    }

    @Test
    void updateStatusRejectsUnknownValue() {
        assertThatThrownBy(() -> service.updateStatus(java.util.UUID.randomUUID(), "archived"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态取值不合法")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
