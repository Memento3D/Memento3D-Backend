package com.memento3d.payment.infrastructure.gateway.vnpay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VnpaySignatureServiceTest {

    private VnpaySignatureService signatureService;
    private final String secretKey = "VNPAYTESTSECRETKEYFORUNITTESTINGMEMENTO3DPROJECT";

    @BeforeEach
    void setUp() {
        signatureService = new VnpaySignatureService();
    }

    @Test
    @DisplayName("Should generate consistent HMAC-SHA512 secure hash")
    void testGenerateSecureHash() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", "TMNTEST01");
        params.put("vnp_Amount", "15000000"); // 150,000 VND * 100
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", "M3D-ORD-20261025-001");
        params.put("vnp_OrderInfo", "Thanh toan don hang in 3D");

        String hash = signatureService.hashAllFields(params, secretKey);
        assertThat(hash).isNotBlank();
        assertThat(hash).hasSize(128); // SHA-512 hex is 128 characters
    }

    @Test
    @DisplayName("Should build complete VNPAY payment URL with vnp_SecureHash")
    void testBuildPaymentUrl() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", "TMNTEST01");
        params.put("vnp_Amount", "55000000"); // 550,000 VND * 100
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", "M3D-ORD-1234");
        params.put("vnp_OrderInfo", "Thanh toan don hang Memento3D");
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", "http://localhost:5173/payment/vnpay/return");
        params.put("vnp_IpAddr", "127.0.0.1");
        params.put("vnp_CreateDate", "20261025140000");
        params.put("vnp_ExpireDate", "20261025141500");

        String baseUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
        String url = signatureService.buildPaymentUrl(baseUrl, params, secretKey);

        assertThat(url).startsWith(baseUrl + "?");
        assertThat(url).contains("vnp_SecureHash=");
        assertThat(url).contains("vnp_Amount=55000000");
        assertThat(url).contains("vnp_TmnCode=TMNTEST01");
    }

    @Test
    @DisplayName("Should verify valid incoming VNPAY signature successfully")
    void testVerifyValidSignature() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "25000000");
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionNo", "14589230");
        params.put("vnp_TxnRef", "M3D-ORD-001-172883");

        String secureHash = signatureService.hashAllFields(params, secretKey);
        params.put("vnp_SecureHash", secureHash);

        boolean isValid = signatureService.verifySignature(params, secretKey);
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Should reject tampered or modified parameters")
    void testRejectTamperedSignature() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "25000000");
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TxnRef", "M3D-ORD-001-172883");

        String secureHash = signatureService.hashAllFields(params, secretKey);
        params.put("vnp_SecureHash", secureHash);

        // Tamper amount
        params.put("vnp_Amount", "10000000");

        boolean isValid = signatureService.verifySignature(params, secretKey);
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should safely handle missing or blank hash secret")
    void testMissingSecretReturnsFalse() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_SecureHash", "abc");
        assertThat(signatureService.verifySignature(params, null)).isFalse();
        assertThat(signatureService.verifySignature(params, "")).isFalse();
    }
}
