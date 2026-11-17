package com.hospital.crm.homevisit.application;

import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.homevisit.api.HomeVisitDtos;
import com.hospital.crm.homevisit.domain.HomeService;
import com.hospital.crm.homevisit.domain.HomeServiceRepository;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HomeServiceCatalogServiceTest {

    @Mock
    private HomeServiceRepository serviceRepository;

    @Mock
    private AuditService auditService;

    private HomeServiceCatalogService catalogService;

    @BeforeEach
    void setUp() {
        catalogService = new HomeServiceCatalogService(serviceRepository, auditService);
        UserPrincipal principal = new UserPrincipal(java.util.UUID.randomUUID(), "reviewer", "审核员",
                java.util.UUID.randomUUID(), "ROOT", "/", java.util.Set.of("SUPER_ADMIN"),
                java.util.Set.of("crm:homevisit:manage"), java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, "n/a", principal.getAuthorities()));
        when(serviceRepository.save(any(HomeService.class))).thenAnswer(invocation ->
                withId(invocation.getArgument(0)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createPersistsDraftAndAudits() {
        HomeVisitDtos.HomeServiceView view = catalogService.create(new HomeVisitDtos.HomeServiceCreateRequest(
                "HOME_NURSE", "居家护理", "medical", "nurse", 60, new BigDecimal("200.00"), "说明"));

        assertThat(view.serviceCode()).isEqualTo("HOME_NURSE");
        assertThat(view.status()).isEqualTo("draft");
        assertThat(view.price()).isEqualTo("200.00");
        verify(auditService).record(org.mockito.ArgumentMatchers.eq("HOMESVC_CREATE"),
                org.mockito.ArgumentMatchers.eq("HOME_SERVICE"), any(), any());
    }

    @Test
    void createDuplicateCodeReturns409() {
        when(serviceRepository.existsByServiceCode("HOME_NURSE")).thenReturn(true);

        assertThatThrownBy(() -> catalogService.create(new HomeVisitDtos.HomeServiceCreateRequest(
                "HOME_NURSE", "居家护理", "medical", "nurse", 60, BigDecimal.ONE, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("服务编码已存在");
    }

    @Test
    void createMedicalWithoutQualTypeReturns422() {
        assertThatThrownBy(() -> catalogService.create(new HomeVisitDtos.HomeServiceCreateRequest(
                "HOME_NURSE", "居家护理", "medical", null, 60, BigDecimal.ONE, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("医疗服务项目必须登记资质要求");
        verify(serviceRepository, never()).save(any());
    }

    @Test
    void submitMovesDraftToPendingReview() {
        when(serviceRepository.findByServiceCode("HOME_NURSE"))
                .thenReturn(Optional.of(service("HOME_NURSE", "draft", "nurse")));

        HomeVisitDtos.HomeServiceView view = catalogService.submit("HOME_NURSE");

        assertThat(view.status()).isEqualTo("pending_review");
        verify(auditService).record(org.mockito.ArgumentMatchers.eq("HOMESVC_SUBMIT"),
                org.mockito.ArgumentMatchers.eq("HOME_SERVICE"), any(), any());
    }

    @Test
    void submitFromPublishedReturns422() {
        when(serviceRepository.findByServiceCode("HOME_NURSE"))
                .thenReturn(Optional.of(service("HOME_NURSE", "published", "nurse")));

        assertThatThrownBy(() -> catalogService.submit("HOME_NURSE"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅草稿或已驳回的服务可提交审核");
    }

    @Test
    void reviewApprovePublishesAndRecordsReviewer() {
        when(serviceRepository.findByServiceCode("HOME_NURSE"))
                .thenReturn(Optional.of(service("HOME_NURSE", "pending_review", "nurse")));

        HomeVisitDtos.HomeServiceView view =
                catalogService.review("HOME_NURSE", new HomeVisitDtos.HomeServiceReviewRequest(true, null));

        assertThat(view.status()).isEqualTo("published");
        assertThat(view.reviewedBy()).isEqualTo("reviewer");
        assertThat(view.reviewedAt()).isNotNull();
    }

    @Test
    void reviewRejectRequiresReason() {
        when(serviceRepository.findByServiceCode("HOME_NURSE"))
                .thenReturn(Optional.of(service("HOME_NURSE", "pending_review", "nurse")));

        assertThatThrownBy(() -> catalogService.review("HOME_NURSE",
                new HomeVisitDtos.HomeServiceReviewRequest(false, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("驳回服务必须填写原因");

        HomeVisitDtos.HomeServiceView view = catalogService.review("HOME_NURSE",
                new HomeVisitDtos.HomeServiceReviewRequest(false, "定价不合理"));
        assertThat(view.status()).isEqualTo("rejected");
        assertThat(view.reviewReason()).isEqualTo("定价不合理");
    }

    @Test
    void reviewFromDraftReturns422() {
        when(serviceRepository.findByServiceCode("HOME_NURSE"))
                .thenReturn(Optional.of(service("HOME_NURSE", "draft", "nurse")));

        assertThatThrownBy(() -> catalogService.review("HOME_NURSE",
                new HomeVisitDtos.HomeServiceReviewRequest(true, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅待审核的服务可以审核");
    }

    @Test
    void updatePublishedCoreFieldReturns422() {
        when(serviceRepository.findByServiceCode("HOME_NURSE"))
                .thenReturn(Optional.of(service("HOME_NURSE", "published", "nurse")));

        assertThatThrownBy(() -> catalogService.update("HOME_NURSE",
                new HomeVisitDtos.HomeServiceUpdateRequest("改名", null, null, 90, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("已发布服务不可修改核心字段，请停用后新建");
    }

    @Test
    void updatePublishedDescriptionOnlyAllowed() {
        when(serviceRepository.findByServiceCode("HOME_NURSE"))
                .thenReturn(Optional.of(service("HOME_NURSE", "published", "nurse")));

        HomeVisitDtos.HomeServiceView view = catalogService.update("HOME_NURSE",
                new HomeVisitDtos.HomeServiceUpdateRequest(null, null, null, null, null, "仅更新说明"));

        assertThat(view.description()).isEqualTo("仅更新说明");
        assertThat(view.serviceName()).isEqualTo("居家护理");
    }

    @Test
    void changeStatusPublishedToDisabled() {
        when(serviceRepository.findByServiceCode("HOME_NURSE"))
                .thenReturn(Optional.of(service("HOME_NURSE", "published", "nurse")));

        HomeVisitDtos.HomeServiceView view = catalogService.changeStatus("HOME_NURSE",
                new HomeVisitDtos.HomeServiceStatusRequest("disabled"));

        assertThat(view.status()).isEqualTo("disabled");
        verify(auditService).record(org.mockito.ArgumentMatchers.eq("HOMESVC_STATUS"),
                org.mockito.ArgumentMatchers.eq("HOME_SERVICE"), any(), any());
    }

    @Test
    void listReturnsPagedViews() {
        when(serviceRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(service("HOME_NURSE", "published", "nurse"))));

        PageResponse<HomeVisitDtos.HomeServiceView> page =
                catalogService.list("护理", "medical", "published", 1, 20);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).serviceCode()).isEqualTo("HOME_NURSE");
    }

    private HomeService service(String code, String status, String requiredQualType) {
        HomeService service = new HomeService();
        service.setServiceCode(code);
        service.setServiceName("居家护理");
        service.setCategory("medical");
        service.setRequiredQualType(requiredQualType);
        service.setDurationMinutes(60);
        service.setPrice(new BigDecimal("200.00"));
        service.setStatus(status);
        return withId(service);
    }

    private <T> T withId(T entity) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            if (field.get(entity) == null) {
                field.set(entity, java.util.UUID.randomUUID());
            }
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
