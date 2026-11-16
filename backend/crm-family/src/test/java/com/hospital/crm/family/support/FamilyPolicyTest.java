package com.hospital.crm.family.support;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FamilyPolicyTest {

    @Test
    void acceptsSupportedRelationshipCodes() {
        assertEquals("spouse", FamilyPolicy.requireRelationshipCode("SPOUSE"));
        assertEquals("配偶", FamilyPolicy.relationshipLabel("spouse"));
    }

    @Test
    void rejectsUnknownRelationshipCode() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> FamilyPolicy.requireRelationshipCode("neighbour"));
        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
    }

    @Test
    void acceptsNonClinicalDelegationScopes() {
        assertEquals("appointment", FamilyPolicy.requireDelegationScope("Appointment"));
        assertEquals("代支付", FamilyPolicy.delegationScopeLabel("payment"));
    }

    @Test
    void rejectsMedicalDelegationScope() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> FamilyPolicy.requireDelegationScope("medical_record"));
        assertEquals(ErrorCode.BUSINESS_RULE_FAILED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("不构成病历访问授权"));
    }

    @Test
    void rejectsUnknownDelegationScope() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> FamilyPolicy.requireDelegationScope("vip_transfer"));
        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
    }

    @Test
    void evaluatesValidityWindowAndStatus() {
        Instant now = Instant.now();
        Instant past = now.minus(1, ChronoUnit.DAYS);
        Instant future = now.plus(1, ChronoUnit.DAYS);

        assertTrue(FamilyPolicy.isEffective("active", past, future, now));
        assertTrue(FamilyPolicy.isEffective("active", past, null, now));
        assertFalse(FamilyPolicy.isEffective("revoked", past, future, now));
        assertFalse(FamilyPolicy.isEffective("expired", past, future, now));
        assertFalse(FamilyPolicy.isEffective("active", future, null, now));
        assertFalse(FamilyPolicy.isEffective("active", past, past, now));
    }
}
