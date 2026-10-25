package com.memento3d.payment.infrastructure.gateway.vnpay;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
@Slf4j
public class VnpaySignatureService {

    private static final String HMAC_SHA512 = "HmacSHA512";

    /**
     * Builds and signs the complete VNPAY redirect URL with HMAC-SHA512 vnp_SecureHash.
     */
    public String buildPaymentUrl(String baseUrl, Map<String, String> params, String hashSecret) {
        if (params == null || params.isEmpty()) {
            throw new IllegalArgumentException("Parameters cannot be empty for VNPAY URL generation");
        }
        if (hashSecret == null || hashSecret.isBlank()) {
            throw new IllegalArgumentException("VNPAY hash secret must be configured");
        }

        List<String> fieldNames = new ArrayList<>(params.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        for (String fieldName : fieldNames) {
            String fieldValue = params.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()
                    && !"vnp_SecureHash".equalsIgnoreCase(fieldName)
                    && !"vnp_SecureHashType".equalsIgnoreCase(fieldName)) {

                if (hashData.length() > 0) {
                    hashData.append('&');
                    query.append('&');
                }

                String encodedKey = URLEncoder.encode(fieldName, StandardCharsets.US_ASCII);
                String encodedVal = URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII);

                hashData.append(fieldName).append('=').append(encodedVal);
                query.append(encodedKey).append('=').append(encodedVal);
            }
        }

        String secureHash = hmacSha512(hashSecret, hashData.toString());
        query.append("&vnp_SecureHash=").append(secureHash);

        return baseUrl + "?" + query.toString();
    }

    /**
     * Computes the HMAC-SHA512 hash over sorted query parameters according to VNPAY 2.1.0 standard.
     */
    public String hashAllFields(Map<String, String> fields, String hashSecret) {
        if (fields == null || hashSecret == null || hashSecret.isBlank()) {
            return "";
        }

        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        for (String fieldName : fieldNames) {
            String fieldValue = fields.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()
                    && !"vnp_SecureHash".equalsIgnoreCase(fieldName)
                    && !"vnp_SecureHashType".equalsIgnoreCase(fieldName)) {

                if (hashData.length() > 0) {
                    hashData.append('&');
                }
                String encodedVal = URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII);
                hashData.append(fieldName).append('=').append(encodedVal);
            }
        }

        return hmacSha512(hashSecret, hashData.toString());
    }

    /**
     * Verifies the incoming vnp_SecureHash using constant-time comparison to prevent timing attacks.
     */
    public boolean verifySignature(Map<String, String> fields, String hashSecret) {
        if (fields == null || hashSecret == null || hashSecret.isBlank()) {
            log.warn("VNPAY signature verification failed: empty fields or missing hashSecret");
            return false;
        }

        String incomingSecureHash = fields.get("vnp_SecureHash");
        if (incomingSecureHash == null || incomingSecureHash.isBlank()) {
            log.warn("VNPAY signature verification failed: incoming vnp_SecureHash is missing");
            return false;
        }

        String expectedSecureHash = hashAllFields(fields, hashSecret);

        boolean matches = MessageDigest.isEqual(
                expectedSecureHash.toLowerCase().getBytes(StandardCharsets.UTF_8),
                incomingSecureHash.toLowerCase().getBytes(StandardCharsets.UTF_8)
        );

        if (!matches) {
            log.warn("VNPAY signature mismatch: incoming hash did not match calculated hash");
        }

        return matches;
    }

    /**
     * Computes HMAC-SHA512 of data using the secret key. Never logs secret key.
     */
    public String hmacSha512(String key, String data) {
        try {
            Mac hmac512 = Mac.getInstance(HMAC_SHA512);
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA512);
            hmac512.init(secretKeySpec);
            byte[] rawHmac = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(rawHmac);
        } catch (Exception e) {
            log.error("Failed to compute HMAC-SHA512: {}", e.getMessage());
            throw new RuntimeException("Error calculating VNPAY secure hash", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
