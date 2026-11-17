package com.hospital.crm.order.application.gateway;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StubPaymentGatewayTest {

    private static final String SECRET = "crm_dev_callback_secret";

    private final StubPaymentGateway gateway = new StubPaymentGateway(SECRET);

    @Test
    void validSignaturePassesVerification() {
        String canonical = StubPaymentGateway.canonicalString("P100000", new BigDecimal("60.00"), true, "T1");
        String signature = StubPaymentGateway.sign(canonical, SECRET);
        assertTrue(gateway.verifyCallback("P100000", new BigDecimal("60.00"), true, "T1", signature));
    }

    @Test
    void tamperedAmountFailsVerification() {
        String canonical = StubPaymentGateway.canonicalString("P100000", new BigDecimal("60.00"), true, "T1");
        String signature = StubPaymentGateway.sign(canonical, SECRET);
        assertFalse(gateway.verifyCallback("P100000", new BigDecimal("600.00"), true, "T1", signature));
    }

    @Test
    void wrongSecretFailsVerification() {
        String canonical = StubPaymentGateway.canonicalString("P100000", new BigDecimal("60.00"), true, "T1");
        String signature = StubPaymentGateway.sign(canonical, "other_secret");
        assertFalse(gateway.verifyCallback("P100000", new BigDecimal("60.00"), true, "T1", signature));
    }

    @Test
    void blankSignatureFailsVerification() {
        assertFalse(gateway.verifyCallback("P100000", new BigDecimal("60.00"), true, "T1", "  "));
    }

    @Test
    void initiatePaymentReturnsStubTradeNo() {
        assertEquals("STUB-P100000",
                gateway.initiatePayment("P100000", "wechat", new BigDecimal("60.00")));
    }

    @Test
    void executeRefundStubSucceeds() {
        assertTrue(gateway.executeRefund("R100000", "P100000", "wechat", new BigDecimal("30.00")));
    }

    @Test
    void stubStatementProviderReturnsEmpty() {
        assertTrue(new StubChannelStatementProvider()
                .fetch("wechat", LocalDate.of(2026, 11, 17)).isEmpty());
    }

    @Test
    void stubInvoiceProviderIssuesPlaceholder() {
        InvoiceProvider.InvoiceIssueResult result = new StubInvoiceProvider().issue(
                new InvoiceProvider.InvoiceIssueCommand("INV100000", "O100000", "某某公司",
                        null, new BigDecimal("60.00"), "general"));
        assertTrue(result.success());
        assertEquals("stub-invoice", result.fileRef());
    }
}
