package com.hospital.crm.system.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MaskingUtilsTest {

    @Test
    void masksMiddleDigitsOfMobile() {
        assertEquals("138****8000", MaskingUtils.maskMobile("13812348000"));
    }

    @Test
    void masksWholeShortValue() {
        assertEquals("*******", MaskingUtils.maskMobile("1234567"));
    }

    @Test
    void returnsNullForBlankMobile() {
        assertNull(MaskingUtils.maskMobile("  "));
    }

    @Test
    void normalizesUsernameAndEmail() {
        assertEquals("admin", MaskingUtils.normalizeUsername("  Admin "));
        assertEquals("a@b.com", MaskingUtils.normalizeEmail(" A@B.com "));
        assertNull(MaskingUtils.normalizeEmail(""));
    }
}
