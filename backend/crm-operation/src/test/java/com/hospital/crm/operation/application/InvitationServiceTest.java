package com.hospital.crm.operation.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.operation.api.OperationDtos;
import com.hospital.crm.operation.domain.Invitation;
import com.hospital.crm.operation.domain.InvitationRepository;
import com.hospital.crm.operation.domain.Lead;
import com.hospital.crm.operation.domain.LeadRepository;
import com.hospital.crm.operation.domain.SourceChannel;
import com.hospital.crm.operation.domain.SourceChannelRepository;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.RoleScope;
import com.hospital.crm.system.security.UserPrincipal;
import com.hospital.crm.system.user.SysUser;
import com.hospital.crm.system.user.SysUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvitationServiceTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ORG_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID LEAD_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock
    private InvitationRepository invitationRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private SourceChannelRepository channelRepository;
    @Mock
    private SysUserRepository userRepository;
    @Mock
    private AuditService auditService;

    private InvitationService service;

    @BeforeEach
    void setUp() {
        service = new InvitationService(invitationRepository, leadRepository, customerService,
                channelRepository, userRepository, auditService);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal(), "n/a", principal().getAuthorities()));
        when(channelRepository.existsByChannelCode(anyString())).thenReturn(true);
        when(channelRepository.findAllByOrderByChannelCodeAsc()).thenReturn(List.of(channel()));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(userRepository.findAllById(any())).thenReturn(List.of(user()));
        when(invitationRepository.save(any(Invitation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requiresLeadOrCustomer() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(
                new OperationDtos.InvitationCreateRequest(null, null, "PHONE", "邀约到院",
                        Instant.now().plusSeconds(3600), null, null)));

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    void acceptingInvitationMovesLeadToInvited() {
        Invitation invitation = invitation("CONTACTED");
        Lead lead = lead("QUALIFIED");
        when(invitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(leadRepository.findById(LEAD_ID)).thenReturn(Optional.of(lead));
        when(leadRepository.findByIdIn(any())).thenReturn(List.of(lead));

        OperationDtos.InvitationView view = service.changeStatus(invitation.getId(),
                new OperationDtos.InvitationStatusRequest("ACCEPTED", "已确认到院", null));

        assertEquals("ACCEPTED", view.status());
        assertEquals("INVITED", lead.getStatus());
    }

    @Test
    void rejectsIllegalTransition() {
        Invitation invitation = invitation("PENDING");
        when(invitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.changeStatus(
                invitation.getId(), new OperationDtos.InvitationStatusRequest("ACCEPTED", null, null)));

        assertEquals(ErrorCode.BUSINESS_RULE_FAILED, ex.getErrorCode());
    }

    private Invitation invitation(String status) {
        Invitation invitation = new Invitation();
        invitation.setInvitationNo("I100001");
        invitation.setLeadId(LEAD_ID);
        invitation.setChannelCode("PHONE");
        invitation.setTitle("邀约到院");
        invitation.setPlannedAt(Instant.now().plusSeconds(3600));
        invitation.setOwnerUserId(USER_ID);
        invitation.setOrgId(ORG_ID);
        invitation.setStatus(status);
        setInvitationId(invitation);
        return invitation;
    }

    private void setInvitationId(Invitation invitation) {
        try {
            var field = Invitation.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(invitation, UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"));
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Lead lead(String status) {
        Lead lead = new Lead();
        lead.setLeadNo("L100001");
        lead.setName("邀约客户");
        lead.setStatus(status);
        lead.setOwnerUserId(USER_ID);
        return lead;
    }

    private SourceChannel channel() {
        SourceChannel channel = new SourceChannel();
        channel.setChannelCode("PHONE");
        channel.setChannelName("电话邀约");
        channel.setChannelType("offline");
        channel.setStatus("active");
        return channel;
    }

    private SysUser user() {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setDisplayName("邀约专员");
        user.setStatus("active");
        return user;
    }

    private UserPrincipal principal() {
        return new UserPrincipal(USER_ID, "tester", "测试员工", ORG_ID, "ROOT", "/",
                Set.of("SUPER_ADMIN"), Set.of("crm:invitation:write"), List.<RoleScope>of());
    }
}
