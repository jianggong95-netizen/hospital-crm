package com.hospital.crm.member.application;

import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.member.api.MemberDtos;
import com.hospital.crm.member.domain.MemberShare;
import com.hospital.crm.member.domain.MemberShareRepository;
import com.hospital.crm.system.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 共享授权：不能授权给自己、重复授权 409、撤销后不可重复撤销。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberShareServiceTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID BENEFICIARY_ID = UUID.randomUUID();
    private static final UUID SHARE_ID = UUID.randomUUID();

    @Mock
    private MemberShareRepository shareRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private AuditService auditService;

    private MemberShareService service;

    @BeforeEach
    void setUp() {
        service = new MemberShareService(shareRepository, customerService, auditService);
        when(shareRepository.save(any(MemberShare.class)))
                .thenAnswer(invocation -> withShareId(invocation.getArgument(0)));
        when(customerService.brief(any(UUID.class)))
                .thenReturn(new CustomerDtos.CustomerBrief(BENEFICIARY_ID, "C002", "李四", null, null));
    }

    @Test
    void createActivatesShare() {
        when(shareRepository.existsByOwnerCustomerIdAndBeneficiaryCustomerIdAndResourceTypeAndStatus(
                OWNER_ID, BENEFICIARY_ID, "BALANCE", "ACTIVE")).thenReturn(false);

        MemberDtos.ShareView view = service.create(new MemberDtos.ShareCreateRequest(
                OWNER_ID, BENEFICIARY_ID, "BALANCE", null, Instant.now().plusSeconds(86400), true));

        assertThat(view.status()).isEqualTo("ACTIVE");
        assertThat(view.canConsume()).isTrue();
        assertThat(view.ownerCustomerId()).isEqualTo(OWNER_ID);
        assertThat(view.beneficiaryCustomerId()).isEqualTo(BENEFICIARY_ID);
        verify(auditService).record(eq("MEMBER_SHARE_CREATE"), any(), any(), any());
    }

    @Test
    void createRejectsSelfAuthorization() {
        assertThatThrownBy(() -> service.create(new MemberDtos.ShareCreateRequest(
                OWNER_ID, OWNER_ID, "BALANCE", null, null, true)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能将权益授权给自己");
        verify(shareRepository, never()).save(any());
    }

    @Test
    void createRejectsDuplicateShare() {
        when(shareRepository.existsByOwnerCustomerIdAndBeneficiaryCustomerIdAndResourceTypeAndStatus(
                OWNER_ID, BENEFICIARY_ID, "CARD", "ACTIVE")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new MemberDtos.ShareCreateRequest(
                OWNER_ID, BENEFICIARY_ID, "CARD", null, null, true)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该资源共享授权已存在");
    }

    @Test
    void createRejectsExpiredValidity() {
        when(shareRepository.existsByOwnerCustomerIdAndBeneficiaryCustomerIdAndResourceTypeAndStatus(
                OWNER_ID, BENEFICIARY_ID, "POINTS", "ACTIVE")).thenReturn(false);

        assertThatThrownBy(() -> service.create(new MemberDtos.ShareCreateRequest(
                OWNER_ID, BENEFICIARY_ID, "POINTS", null, Instant.now().minusSeconds(60), true)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("授权失效时间必须晚于当前时间");
    }

    @Test
    void revokeMarksShareRevoked() {
        when(shareRepository.findById(SHARE_ID)).thenReturn(Optional.of(share("ACTIVE")));

        MemberDtos.ShareView view = service.revoke(SHARE_ID);

        assertThat(view.status()).isEqualTo("REVOKED");
        verify(auditService).record(eq("MEMBER_SHARE_REVOKE"), any(), any(), any());
    }

    @Test
    void revokeRejectsAlreadyRevokedShare() {
        when(shareRepository.findById(SHARE_ID)).thenReturn(Optional.of(share("REVOKED")));

        assertThatThrownBy(() -> service.revoke(SHARE_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该共享授权已撤销，请勿重复操作");
    }

    @Test
    void listRequiresOwnerOrBeneficiary() {
        assertThatThrownBy(() -> service.list(null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请指定授权人或使用人");
    }

    private MemberShare share(String status) {
        MemberShare share = new MemberShare();
        share.setOwnerCustomerId(OWNER_ID);
        share.setBeneficiaryCustomerId(BENEFICIARY_ID);
        share.setResourceType("BALANCE");
        share.setCanConsume(true);
        share.setValidFrom(Instant.now().minusSeconds(60));
        share.setStatus(status);
        setField(share, SHARE_ID);
        return share;
    }

    private MemberShare withShareId(MemberShare share) {
        setField(share, SHARE_ID);
        return share;
    }

    private void setField(Object entity, UUID id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
