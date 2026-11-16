package com.hospital.crm.customer.crypto;

import com.hospital.crm.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SensitiveDataCipherTest {

    private static final String KEY = Base64.getEncoder()
            .encodeToString("unit-test-customer-data-key-32by".getBytes(StandardCharsets.UTF_8));

    private final DataEncryptionProperties properties = new DataEncryptionProperties(KEY);
    private final SensitiveDataCipher cipher = new SensitiveDataCipher(properties);
    private final SensitiveDataHasher hasher = new SensitiveDataHasher(properties);

    @Test
    void encryptsAndDecryptsRoundTrip() {
        String encrypted = cipher.encrypt("13812348000");

        assertNotEquals("13812348000", encrypted);
        assertEquals("13812348000", cipher.decrypt(encrypted));
    }

    @Test
    void usesRandomIvSoCipherTextDiffers() {
        assertNotEquals(cipher.encrypt("13812348000"), cipher.encrypt("13812348000"));
    }

    @Test
    void cannotDecryptWithAnotherKey() {
        String encrypted = cipher.encrypt("13812348000");
        SensitiveDataCipher other = new SensitiveDataCipher(new DataEncryptionProperties(
                Base64.getEncoder().encodeToString("another-customer-data-key-32byte"
                        .getBytes(StandardCharsets.UTF_8))));

        assertThrows(BusinessException.class, () -> other.decrypt(encrypted));
    }

    @Test
    void hashesAreStableAndNotReversible() {
        String first = hasher.hash("13812348000");
        String second = hasher.hash("13812348000");

        assertEquals(first, second);
        assertEquals(64, first.length());
        assertFalse(first.contains("13812348000"));
    }

    @Test
    void rejectsInvalidKeyLength() {
        String shortKey = Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalStateException.class,
                () -> new SensitiveDataCipher(new DataEncryptionProperties(shortKey)));
        assertThrows(IllegalStateException.class,
                () -> new SensitiveDataHasher(new DataEncryptionProperties(shortKey)));
    }
}
