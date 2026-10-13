package com.memento3d.payment.infrastructure.gateway.momo;

import com.memento3d.payment.infrastructure.gateway.momo.dto.MomoIpnRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@Slf4j
public class MomoSignatureService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    public String signCreateRequest(
            String accessKey,
            long amount,
            String extraData,
            String ipnUrl,
            String orderId,
            String orderInfo,
            String partnerCode,
            String redirectUrl,
            String requestId,
            String requestType,
            String secretKey
    ) {
        String rawData = "accessKey=" + accessKey
                + "&amount=" + amount
                + "&extraData=" + (extraData != null ? extraData : "")
                + "&ipnUrl=" + ipnUrl
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + partnerCode
                + "&redirectUrl=" + redirectUrl
                + "&requestId=" + requestId
                + "&requestType=" + requestType;

        return hmacSha256(rawData, secretKey);
    }

    public String signQueryRequest(
            String accessKey,
            String orderId,
            String partnerCode,
            String requestId,
            String secretKey
    ) {
        String rawData = "accessKey=" + accessKey
                + "&orderId=" + orderId
                + "&partnerCode=" + partnerCode
                + "&requestId=" + requestId;

        return hmacSha256(rawData, secretKey);
    }

    public boolean verifyIpnSignature(MomoIpnRequest ipn, String accessKey, String secretKey) {
        if (ipn.getSignature() == null || secretKey == null) {
            return false;
        }

        String rawData = "accessKey=" + accessKey
                + "&amount=" + ipn.getAmount()
                + "&extraData=" + (ipn.getExtraData() != null ? ipn.getExtraData() : "")
                + "&message=" + (ipn.getMessage() != null ? ipn.getMessage() : "")
                + "&orderId=" + ipn.getOrderId()
                + "&orderInfo=" + (ipn.getOrderInfo() != null ? ipn.getOrderInfo() : "")
                + "&orderType=" + (ipn.getOrderType() != null ? ipn.getOrderType() : "")
                + "&partnerCode=" + ipn.getPartnerCode()
                + "&payType=" + (ipn.getPayType() != null ? ipn.getPayType() : "")
                + "&requestId=" + ipn.getRequestId()
                + "&responseTime=" + ipn.getResponseTime()
                + "&resultCode=" + ipn.getResultCode()
                + "&transId=" + ipn.getTransId();

        String expectedSignature = hmacSha256(rawData, secretKey);
        return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                ipn.getSignature().getBytes(StandardCharsets.UTF_8)
        );
    }

    public String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(rawHmac);
        } catch (Exception e) {
            log.error("Error generating HMAC-SHA256 signature", e);
            throw new RuntimeException("Failed to calculate HMAC-SHA256", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
