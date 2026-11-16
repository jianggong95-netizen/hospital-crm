package com.hospital.crm.system.security;

import com.hospital.crm.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

    @Test
    void acceptsLetterAndDigitWithinLength() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("Crm@123456"));
    }

    @Test
    void rejectsTooShortPassword() {
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("a1"));
    }

    @Test
    void rejectsPasswordWithoutDigit() {
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("abcdefgh"));
    }

    @Test
    void rejectsPasswordWithoutLetter() {
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("12345678"));
    }
}
