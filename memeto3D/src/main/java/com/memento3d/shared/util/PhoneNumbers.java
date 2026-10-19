package com.memento3d.shared.util;

import com.memento3d.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.regex.Pattern;

/**
 * Normalises and validates Vietnamese mobile phone numbers.
 * Accepts {@code 0912345678}, {@code +84912345678}, {@code 84 912 345 678}, {@code 091-234-5678}...
 * and always stores the canonical national form {@code 0912345678}.
 */
public final class PhoneNumbers {

    private static final Pattern NATIONAL = Pattern.compile("^0(3|5|7|8|9)\\d{8}$");

    private PhoneNumbers() {
    }

    /**
     * @return canonical phone, or {@code null} when the input is blank.
     * @throws BusinessException 400 INVALID_PHONE if the number is not a valid VN mobile number.
     */
    public static String normalizeOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("[\\s.\\-()]", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.startsWith("+84")) {
            cleaned = "0" + cleaned.substring(3);
        } else if (cleaned.startsWith("84") && cleaned.length() == 11) {
            cleaned = "0" + cleaned.substring(2);
        }
        if (!NATIONAL.matcher(cleaned).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PHONE",
                    "Số điện thoại không hợp lệ. Vui lòng nhập số di động Việt Nam (ví dụ 0912345678).");
        }
        return cleaned;
    }

    /** Same as {@link #normalizeOrNull(String)} but rejects blank input. */
    public static String normalizeRequired(String raw) {
        String normalized = normalizeOrNull(raw);
        if (normalized == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PHONE", "Số điện thoại là bắt buộc.");
        }
        return normalized;
    }
}
