package com.memento3d.payment.infrastructure.gateway.zalopay;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@Slf4j
public class ZaloPaySignatureService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    public String generateCreateMac(
            int appId,
            String appTransId,
            String appUser,
            long amount,
            long appTime,
            String embedData,
            String item,
            String key1
    ) {
        String rawData = appId + "|" + appTransId + "|" + appUser + "|" + amount + "|" + appTime + "|" + embedData + "|" + item;
        return hmacSha256(rawData, key1);
    }

    public String generateQueryMac(int appId, String appTransId, String key1) {
        String rawData = appId + "|" + appTransId + "|" + key1;
        return hmacSha256(rawData, key1);
    }

    public boolean verifyCallbackMac(String data, String mac, String key2) {
        if (data == null || mac == null || key2 == null) {
            return false;
        }
        String expectedMac = hmacSha256(data, key2);
        return MessageDigest.isEqual(
                expectedMac.getBytes(StandardCharsets.UTF_8),
                mac.getBytes(StandardCharsets.UTF_8)
        );
    }

    public String hmacSha256(String data, String key) {
        try {
            Mac hmac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            hmac.init(secretKeySpec);
            byte[] raw = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(raw);
        } catch (Exception e) {
            log.error("Error generating ZaloPay HMAC-SHA256", e);
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
