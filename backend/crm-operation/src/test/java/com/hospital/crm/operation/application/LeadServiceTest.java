package com.hospital.crm.operation.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.customer.crypto.SensitiveDataCipher;
import com.hospital.crm.customer.crypto.SensitiveDataHasher;
import com.hospital.crm.operation.api.OperationDtos;
import com.hospital.crm.operation.domain.Lead;
import com.hospital.crm.operation.domain.LeadAssignment;
import com.hospital.crm.operation.domain.LeadAssignmentRepository;
import com.hospital.crm.operation.domain.LeadContactRecord;
import com.hospital.crm.operation.domain.LeadContactRecordRepository;
import com.hospital.crm.operation.domain.LeadConversion;
import com.hospital.crm.operation.domain.LeadConversionRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 线索核心规则测试：重复核验、状态机、转客户追溯、合并与分派。
 * 使用虚构线索数据，不包含真实患者信息。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeadServiceTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ORG_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID LEAD_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID TARGET_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private LeadContactRecordRepository contactRepository;
    @Mock
    private LeadAssignmentRepository assignmentRepository;
    @Mock
    private LeadConversionRepository conversionRepository;
    @Mock
    private SourceChannelRepository channelRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private SysUserRepository userRepository;
    @Mock
    private SensitiveDataCipher cipher;
    @Mock
    private SensitiveDataHasher hasher;
    @Mock
    private AuditService auditService;

    private LeadService service;

    @BeforeEach
    void setUp() {
        service = new LeadService(leadRepository, contactRepository, assignmentRepository, conversionRepository,
                channelRepository, customerService, userRepository, cipher, hasher, auditService);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal(), "n/a", principal().getAuthorities()));
        when(cipher.encrypt(anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
        when(hasher.hash(anyString())).thenAnswer(invocation -> "hash:" + invocation.getArgument(0));
        when(channelRepository.existsByChannelCode(anyString())).thenReturn(true);
        when(channelRepository.findAllByOrderByChannelCodeAsc()).thenReturn(List.of(channel("ONLINE")));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, "当前员工")));
        when(userRepository.findAllById(any())).thenReturn(List.of(
                user(USER_ID, "当前员工"), user(TARGET_ID, "目标员工")));
        when(leadRepository.save(any(Lead.class))).thenAnswer(invocation -> {
            Lead saved = invocation.getArgument(0);
            return saved.getId() == null ? withId(saved, UUID.randomUUID()) : saved;
        });
        when(leadRepository.nextLeadNo()).thenReturn(100001L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsDuplicateLeadUnlessForced() {
        Lead existing = lead(LEAD_ID, "L100001", "重复客户", "QUALIFIED");
        when(leadRepository.findByPhoneHashAndStatusIn(anyString(), any()))
                .thenReturn(List.of(existing));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(createRequest(false)));

        assertEquals(ErrorCode.BUSINESS_CONFLICT, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("L100001"));
        verify(leadRepository, never()).save(any(Lead.class));
    }

    @Test
    void createsMaskedLeadWithForcedFlag() {
        when(leadRepository.findByPhoneHashAndStatusIn(anyString(), any())).thenReturn(List.of());

        OperationDtos.LeadSummary summary = service.create(createRequest(true));

        assertEquals("NEW", summary.status());
        assertEquals("138****0001", summary.phoneMasked());
        assertEquals("审核渠道", summary.sourceChannelName());
        verify(leadRepository).save(any(Lead.class));
        verify(auditService).record(org.mockito.ArgumentMatchers.eq("LEAD_CREATE"),
                org.mockito.ArgumentMatchers.eq("LEAD"), anyString(), any());
    }

    @Test
    void convertsLeadToNewCustomerAndKeepsTraceability() {
        Lead lead = lead(LEAD_ID, "L100002", "待转化客户", "QUALIFIED");
        when(leadRepository.findById(LEAD_ID)).thenReturn(Optional.of(lead));
        when(conversionRepository.existsByLeadId(LEAD_ID)).thenReturn(false);
        when(cipher.decrypt("enc:13800000001")).thenReturn("13800000001");
        when(customerService.create(any())).thenReturn(customerSummary());
        when(conversionRepository.save(any(LeadConversion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OperationDtos.ConversionView view = service.convert(LEAD_ID, new OperationDtos.LeadConvertRequest(
                null, true, "电话确认到院"));

        assertEquals("CONVERTED", lead.getStatus());
        assertEquals(customerSummary().id(), lead.getConvertedCustomerId());
        assertNotNull(lead.getConvertedAt());
        assertEquals("NEW_CUSTOMER", view.conversionMode());
        assertEquals("C100001", view.customerNo());

        ArgumentCaptor<LeadConversion> captor = ArgumentCaptor.forClass(LeadConversion.class);
        verify(conversionRepository).save(captor.capture());
        assertEquals(LEAD_ID, captor.getValue().getLeadId());
        assertEquals(customerSummary().id(), captor.getValue().getCustomerId());
    }

    @Test
    void rejectsInvalidStatusTransition() {
        Lead lead = lead(LEAD_ID, "L100003", "状态测试", "NEW");
        when(leadRepository.findById(LEAD_ID)).thenReturn(Optional.of(lead));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changeStatus(LEAD_ID, new OperationDtos.LeadStatusRequest("QUALIFIED", null)));

        assertEquals(ErrorCode.BUSINESS_RULE_FAILED, ex.getErrorCode());
    }

    @Test
    void mergesLeadAndRecordsTarget() {
        Lead source = lead(LEAD_ID, "L100004", "来源线索", "CONTACTING");
        Lead target = lead(TARGET_ID, "L100005", "目标线索", "QUALIFIED");
        when(leadRepository.findById(LEAD_ID)).thenReturn(Optional.of(source));
        when(leadRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));

        OperationDtos.LeadSummary summary = service.merge(LEAD_ID,
                new OperationDtos.LeadMergeRequest(TARGET_ID, "同手机号"));

        assertEquals("MERGED", summary.status());
        assertEquals(TARGET_ID, source.getMergedIntoLeadId());
        verify(auditService).record(org.mockito.ArgumentMatchers.eq("LEAD_MERGE"),
                org.mockito.ArgumentMatchers.eq("LEAD"), anyString(), any());
    }

    @Test
    void assignsLeadToActiveUser() {
        Lead lead = lead(LEAD_ID, "L100006", "分派测试", "CONTACTING");
        SysUser target = user(TARGET_ID, "目标员工");
        when(leadRepository.findById(LEAD_ID)).thenReturn(Optional.of(lead));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(assignmentRepository.save(any(LeadAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OperationDtos.LeadSummary summary = service.assign(LEAD_ID,
                new OperationDtos.LeadAssignRequest(TARGET_ID, "按区域分派"));

        assertEquals(TARGET_ID, summary.ownerUserId());
        assertEquals("目标员工", summary.ownerName());
        verify(assignmentRepository).save(any(LeadAssignment.class));
    }

    @Test
    void addingFirstContactMovesNewLeadToContacting() {
        Lead lead = lead(LEAD_ID, "L100007", "联系测试", "NEW");
        when(leadRepository.findById(LEAD_ID)).thenReturn(Optional.of(lead));
        when(contactRepository.save(any(LeadContactRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.addContact(LEAD_ID, new OperationDtos.ContactRecordCreateRequest(
                "phone", Instant.now(), "已电话沟通", "CONNECTED", null));

        assertEquals("CONTACTING", lead.getStatus());
        verify(auditService).record(org.mockito.ArgumentMatchers.eq("LEAD_CONTACT_CREATE"),
                org.mockito.ArgumentMatchers.eq("LEAD"), anyString(), any());
    }

    @Test
    void reportsDuplicateCandidatesByPhone() {
        Lead existing = lead(LEAD_ID, "L100008", "疑似重复", "NEW");
        when(leadRepository.findByPhoneHashAndStatusIn(anyString(), any()))
                .thenReturn(List.of(existing));

        List<OperationDtos.DuplicateLead> duplicates = service.duplicates("13800000001", null);

        assertEquals(1, duplicates.size());
        assertEquals("L100008", duplicates.get(0).leadNo());
    }

    private OperationDtos.LeadCreateRequest createRequest(boolean force) {
        return new OperationDtos.LeadCreateRequest(
                "新线索", "female", LocalDate.of(1990, 5, 6), "13800000001", null,
                "ONLINE", null, "测试线索", force);
    }

    private Lead lead(UUID id, String leadNo, String name, String status) {
        Lead lead = new Lead();
        lead.setLeadNo(leadNo);
        lead.setName(name);
        lead.setGender("female");
        lead.setSourceChannelCode("ONLINE");
        lead.setStatus(status);
        lead.setOwnerUserId(USER_ID);
        lead.setOrgId(ORG_ID);
        lead.setPhoneEncrypted("enc:13800000001");
        lead.setPhoneHash("hash:13800000001");
        lead.setPhoneMasked("138****0001");
        return withId(lead, id);
    }

    private Lead withId(Lead lead, UUID id) {
        try {
            var field = Lead.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(lead, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return lead;
    }

    private SourceChannel channel(String code) {
        SourceChannel channel = new SourceChannel();
        channel.setChannelCode(code);
        channel.setChannelName("审核渠道");
        channel.setChannelType("online");
        channel.setStatus("active");
        return channel;
    }

    private SysUser user(UUID id, String displayName) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setDisplayName(displayName);
        user.setStatus("active");
        return user;
    }

    private CustomerDtos.CustomerSummary customerSummary() {
        return new CustomerDtos.CustomerSummary(
                UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
                "C100001", "待转化客户", "female", LocalDate.of(1990, 5, 6),
                "138****0001", null, "online", "active", List.of(), Instant.now(), Instant.now());
    }

    private UserPrincipal principal() {
        return new UserPrincipal(USER_ID, "tester", "测试员工", ORG_ID, "ROOT", "/",
                Set.of("SUPER_ADMIN"), Set.of("crm:lead:write"), List.<RoleScope>of());
    }
}
