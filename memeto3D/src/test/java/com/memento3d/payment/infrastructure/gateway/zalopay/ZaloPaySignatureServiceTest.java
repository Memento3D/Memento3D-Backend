package com.memento3d.payment.infrastructure.gateway.zalopay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ZaloPaySignatureServiceTest {

    private ZaloPaySignatureService signatureService;
    private final String key1 = "PcY4iZIKFCIdgZvA6ueMcMHHUbRLYjPL";
    private final String key2 = "kLtgPl8HHhfvMuD2wKfgccM4nwqiEdtF";

    @BeforeEach
    void setUp() {
        signatureService = new ZaloPaySignatureService();
    }

    @Test
    @DisplayName("Generate MAC for ZaloPay Create Order")
    void testGenerateCreateMac() {
        String mac = signatureService.generateCreateMac(
                2553,
                "261013_123456",
                "user_123",
                50000L,
                1728829100000L,
                "{}",
                "[]",
                key1
        );

        assertNotNull(mac);
        assertEquals(64, mac.length());
    }

    @Test
    @DisplayName("Verify valid ZaloPay Callback MAC with key2")
    void testVerifyCallbackMacSuccess() {
        String data = "{\"app_id\":2553,\"app_trans_id\":\"261013_123456\",\"amount\":50000}";
        String validMac = signatureService.hmacSha256(data, key2);

        assertTrue(signatureService.verifyCallbackMac(data, validMac, key2));
    }

    @Test
    @DisplayName("Reject invalid or tampered ZaloPay Callback MAC")
    void testVerifyCallbackMacTampered() {
        String data = "{\"app_id\":2553,\"app_trans_id\":\"261013_123456\",\"amount\":50000}";
        String invalidMac = "deadbeef12345678deadbeef12345678deadbeef12345678deadbeef12345678";

        assertFalse(signatureService.verifyCallbackMac(data, invalidMac, key2));
    }

    @Test
    @DisplayName("Generate Query MAC for ZaloPay")
    void testGenerateQueryMac() {
        String queryMac = signatureService.generateQueryMac(2553, "261013_123456", key1);
        assertNotNull(queryMac);
        assertEquals(64, queryMac.length());
    }
}
