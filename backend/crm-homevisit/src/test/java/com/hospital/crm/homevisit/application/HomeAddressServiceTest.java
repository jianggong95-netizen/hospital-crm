package com.hospital.crm.homevisit.application;

import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.customer.crypto.SensitiveDataCipher;
import com.hospital.crm.customer.crypto.SensitiveDataHasher;
import com.hospital.crm.homevisit.api.HomeVisitDtos;
import com.hospital.crm.homevisit.domain.HomeAddress;
import com.hospital.crm.homevisit.domain.HomeAddressRepository;
import com.hospital.crm.system.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HomeAddressServiceTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID ADDRESS_ID = UUID.randomUUID();

    @Mock
    private HomeAddressRepository addressRepository;

    @Mock
    private CustomerService customerService;

    @Mock
    private SensitiveDataCipher cipher;

    @Mock
    private SensitiveDataHasher hasher;

    @Mock
    private AuditService auditService;

    private HomeAddressService addressService;

    @BeforeEach
    void setUp() {
        addressService = new HomeAddressService(addressRepository, customerService, cipher, hasher, auditService);
        when(addressRepository.save(any(HomeAddress.class))).thenAnswer(invocation ->
                withId(invocation.getArgument(0)));
        when(cipher.encrypt(anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
        when(hasher.hash(anyString())).thenAnswer(invocation -> "hash:" + invocation.getArgument(0));
        when(customerService.brief(CUSTOMER_ID)).thenReturn(
                new CustomerDtos.CustomerBrief(CUSTOMER_ID, "C001", "张三", null, null));
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(address(false)));
    }

    @Test
    void createEncryptsPhoneAndStoresMaskedOnly() {
        HomeVisitDtos.HomeAddressView view = addressService.create(new HomeVisitDtos.HomeAddressCreateRequest(
                CUSTOMER_ID, "上海市浦东新区XX路100号", "李四", "13812348000", false, "放门口"));

        ArgumentCaptor<HomeAddress> captor = ArgumentCaptor.forClass(HomeAddress.class);
        verify(addressRepository).save(captor.capture());
        HomeAddress saved = captor.getValue();
        assertThat(saved.getContactPhoneEncrypted()).isEqualTo("enc:13812348000");
        assertThat(saved.getContactPhoneHash()).isEqualTo("hash:13812348000");
        assertThat(saved.getContactPhoneMasked()).isEqualTo("138****8000");
        assertThat(saved.getAddress()).doesNotContain("13812348000");
        assertThat(view.contactPhoneMasked()).isEqualTo("138****8000");
        verify(auditService).record(eq("HOMEADDR_CREATE"), eq("HOME_ADDRESS"), any(), any());
    }

    @Test
    void setDefaultClearsOtherDefaultAddress() {
        HomeAddress previousDefault = address(true);
        setField(previousDefault, UUID.randomUUID());
        when(addressRepository.findByCustomerIdAndIsDefaultTrueAndStatus(CUSTOMER_ID, "active"))
                .thenReturn(List.of(previousDefault));

        HomeVisitDtos.HomeAddressView view = addressService.create(new HomeVisitDtos.HomeAddressCreateRequest(
                CUSTOMER_ID, "上海市徐汇区YY路1号", "王五", null, true, null));

        assertThat(view.isDefault()).isTrue();
        ArgumentCaptor<HomeAddress> captor = ArgumentCaptor.forClass(HomeAddress.class);
        verify(addressRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).isDefault()).isFalse();
    }

    @Test
    void createUnknownCustomerReturns404() {
        UUID unknown = UUID.randomUUID();
        when(customerService.brief(unknown)).thenThrow(
                new BusinessException(com.hospital.crm.common.api.ErrorCode.RESOURCE_NOT_FOUND, "客户不存在"));

        assertThatThrownBy(() -> addressService.create(new HomeVisitDtos.HomeAddressCreateRequest(
                unknown, "上海市浦东新区XX路100号", "李四", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("客户不存在");
        verify(addressRepository, times(0)).save(any());
    }

    @Test
    void updateSwitchesDefaultAndClearsPrevious() {
        HomeAddress previousDefault = address(true);
        setField(previousDefault, UUID.randomUUID());
        when(addressRepository.findByCustomerIdAndIsDefaultTrueAndStatus(CUSTOMER_ID, "active"))
                .thenReturn(List.of(previousDefault));

        HomeVisitDtos.HomeAddressView view = addressService.update(ADDRESS_ID,
                new HomeVisitDtos.HomeAddressUpdateRequest("新地址", null, null, true, null));

        assertThat(view.isDefault()).isTrue();
        assertThat(view.address()).isEqualTo("新地址");
        assertThat(previousDefault.isDefault()).isFalse();
        verify(auditService).record(eq("HOMEADDR_UPDATE"), eq("HOME_ADDRESS"), any(), any());
    }

    @Test
    void changeStatusDisablesAddressAndReleasesDefault() {
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(address(true)));

        HomeVisitDtos.HomeAddressView view = addressService.changeStatus(ADDRESS_ID,
                new HomeVisitDtos.HomeAddressStatusRequest("disabled"));

        assertThat(view.status()).isEqualTo("disabled");
        assertThat(view.isDefault()).isFalse();
        verify(auditService).record(eq("HOMEADDR_STATUS"), eq("HOME_ADDRESS"), any(), any());
    }

    @Test
    void updateUnknownAddressReturns404() {
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.update(ADDRESS_ID,
                new HomeVisitDtos.HomeAddressUpdateRequest("新地址", null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("服务地址不存在");
    }

    private HomeAddress address(boolean isDefault) {
        HomeAddress address = new HomeAddress();
        address.setCustomerId(CUSTOMER_ID);
        address.setAddress("上海市浦东新区XX路100号");
        address.setContactName("李四");
        address.setContactPhoneMasked("138****8000");
        address.setDefault(isDefault);
        address.setStatus("active");
        return withId(address);
    }

    private <T> T withId(T entity) {
        setField(entity, ADDRESS_ID);
        return entity;
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
