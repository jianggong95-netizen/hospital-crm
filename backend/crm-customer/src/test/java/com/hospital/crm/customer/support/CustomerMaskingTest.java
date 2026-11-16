package com.hospital.crm.customer.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CustomerMaskingTest {

    @Test
    void normalizesPhoneWithSeparators() {
        assertEquals("13812348000", CustomerMasking.normalizePhone("138-1234-8000"));
        assertNull(CustomerMasking.normalizePhone(" "));
    }

    @Test
    void masksPhoneKeepingPrefixAndSuffix() {
        assertEquals("138****8000", CustomerMasking.maskPhone("13812348000"));
    }

    @Test
    void normalizesAndMasksIdNo() {
        assertEquals("11010119900101001X", CustomerMasking.normalizeIdNo(" 110101 19900101001x "));
        assertEquals("110101********001X", CustomerMasking.maskIdNo("11010119900101001X"));
    }

    @Test
    void masksShortIdCompletely() {
        assertEquals("********", CustomerMasking.maskIdNo("12345678"));
    }
}
