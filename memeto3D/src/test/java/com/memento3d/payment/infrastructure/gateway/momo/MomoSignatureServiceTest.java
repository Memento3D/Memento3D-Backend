package com.memento3d.payment.infrastructure.gateway.momo;

import com.memento3d.payment.infrastructure.gateway.momo.dto.MomoIpnRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MomoSignatureServiceTest {

    private MomoSignatureService signatureService;
    private final String accessKey = "F8BBA842ECF85";
    private final String secretKey = "K951B6PE1wa8ngfBW/gahOD0viQDuMmq";

    @BeforeEach
    void setUp() {
        signatureService = new MomoSignatureService();
    }

    @Test
    @DisplayName("Generate deterministic HMAC-SHA256 signature for Create Payment")
    void testSignCreateRequest() {
        String signature = signatureService.signCreateRequest(
                accessKey,
                50000L,
                "",
                "http://localhost:8080/api/payments/webhooks/momo",
                "MM_12345678",
                "Order payment",
                "MOMO",
                "http://localhost:3000/result",
                "REQ_123456",
                "captureWallet",
                secretKey
        );

        assertNotNull(signature);
        assertFalse(signature.isBlank());
        assertEquals(64, signature.length()); // SHA256 hex is 64 chars
    }

    @Test
    @DisplayName("Verify valid MoMo IPN signature successfully")
    void testVerifyIpnSignatureSuccess() {
        MomoIpnRequest ipn = MomoIpnRequest.builder()
                .partnerCode("MOMO")
                .orderId("MM_12345678")
                .requestId("REQ_123456")
                .amount(50000L)
                .orderInfo("Order payment")
                .orderType("momo_wallet")
                .transId(1234567890L)
                .resultCode(0)
                .message("Successful.")
                .payType("qr")
                .responseTime(1728829100000L)
                .extraData("")
                .build();

        String rawData = "accessKey=" + accessKey
                + "&amount=" + ipn.getAmount()
                + "&extraData=" + ipn.getExtraData()
                + "&message=" + ipn.getMessage()
                + "&orderId=" + ipn.getOrderId()
                + "&orderInfo=" + ipn.getOrderInfo()
                + "&orderType=" + ipn.getOrderType()
                + "&partnerCode=" + ipn.getPartnerCode()
                + "&payType=" + ipn.getPayType()
                + "&requestId=" + ipn.getRequestId()
                + "&responseTime=" + ipn.getResponseTime()
                + "&resultCode=" + ipn.getResultCode()
                + "&transId=" + ipn.getTransId();

        String validSignature = signatureService.hmacSha256(rawData, secretKey);
        ipn.setSignature(validSignature);

        assertTrue(signatureService.verifyIpnSignature(ipn, accessKey, secretKey));
    }

    @Test
    @DisplayName("Reject tampered MoMo IPN payload")
    void testVerifyIpnSignatureTampered() {
        MomoIpnRequest ipn = MomoIpnRequest.builder()
                .partnerCode("MOMO")
                .orderId("MM_12345678")
                .requestId("REQ_123456")
                .amount(50000L)
                .orderInfo("Order payment")
                .orderType("momo_wallet")
                .transId(1234567890L)
                .resultCode(0)
                .message("Successful.")
                .payType("qr")
                .responseTime(1728829100000L)
                .extraData("")
                .signature("invalid_hex_signature_tampered_value")
                .build();

        assertFalse(signatureService.verifyIpnSignature(ipn, accessKey, secretKey));
    }

    @Test
    @DisplayName("Generate deterministic HMAC-SHA256 signature for Query Request")
    void testSignQueryRequest() {
        String signature = signatureService.signQueryRequest(
                accessKey,
                "MM_12345678",
                "MOMO",
                "REQ_123456",
                secretKey
        );

        assertNotNull(signature);
        assertEquals(64, signature.length());
    }
}
