package com.memento3d.identity.application.validator;

import com.memento3d.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Enterprise validation engine for User Registration and Profile updates.
 * Implements BR-AUTH-003, BR-AUTH-004, and BR-AUTH-005.
 */
@Component
public class UserRegistrationValidator {

    // RFC-5322 compliant email pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    // Vietnamese E.164 phone pattern: 10 digits starting with 03, 05, 07, 08, 09 or +84
    private static final Pattern VIETNAMESE_PHONE_PATTERN = Pattern.compile(
            "^(?:\\+84|0)(3[2-9]|5[2689]|7[06-9]|8[1-9]|9[0-9])[0-9]{7}$"
    );

    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?~`]");

    // Known disposable email domains
    private static final Set<String> DISPOSABLE_EMAIL_DOMAINS = Set.of(
            "mailinator.com", "tempmail.com", "10minutemail.com", "guerrillamail.com",
            "sharklasers.com", "yopmail.com", "throwawaymail.com", "dispostable.com",
            "getairmail.com", "trashmail.com", "mohmal.com", "temp-mail.org",
            "fakeinbox.com", "burnermail.io", "nada.ltd", "crazymailing.com",
            "tempmailaddress.com", "mytemp.email", "generator.email", "emailondeck.com"
    );

    // Top vulnerable / common passwords dictionary
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password", "password123", "123456", "12345678", "123456789",
            "qwertyuiop", "admin123", "memento3d", "letmein123", "iloveyou1",
            "welcome123", "superman1", "football1", "monkey123", "master123"
    );

    /**
     * Validates and normalizes email address.
     *
     * @param rawEmail user input email
     * @return normalized lowercase trimmed email
     */
    public String validateAndNormalizeEmail(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL", "Email không được để trống");
        }

        String normalized = rawEmail.trim().toLowerCase(Locale.ROOT);

        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL_FORMAT", "Định dạng email không hợp lệ: " + rawEmail);
        }

        // Check disposable email domains
        int atIndex = normalized.indexOf('@');
        if (atIndex > 0 && atIndex < normalized.length() - 1) {
            String domain = normalized.substring(atIndex + 1);
            if (DISPOSABLE_EMAIL_DOMAINS.contains(domain)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "DISPOSABLE_EMAIL_REJECTED",
                        "Email tạm thời hoặc dùng một lần (" + domain + ") không được phép đăng ký. Vui lòng sử dụng email thật.");
            }
        }

        return normalized;
    }

    /**
     * Validates password strength, policy constraints, and context safety.
     *
     * @param password plain text password
     * @param email    user email for anti-repetition check
     * @param fullName user full name for anti-repetition check
     */
    public void validatePassword(String password, String email, String fullName) {
        if (password == null || password.length() < 8) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD_LENGTH",
                    "Mật khẩu phải có độ dài tối thiểu 8 ký tự.");
        }

        if (password.length() > 128) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG",
                    "Mật khẩu không được vượt quá 128 ký tự.");
        }

        if (!UPPERCASE_PATTERN.matcher(password).find()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD_NO_UPPERCASE",
                    "Mật khẩu phải chứa ít nhất 1 ký tự chữ hoa (A-Z).");
        }

        if (!LOWERCASE_PATTERN.matcher(password).find()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD_NO_LOWERCASE",
                    "Mật khẩu phải chứa ít nhất 1 ký tự chữ thường (a-z).");
        }

        if (!DIGIT_PATTERN.matcher(password).find()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD_NO_DIGIT",
                    "Mật khẩu phải chứa ít nhất 1 chữ số (0-9).");
        }

        if (!SPECIAL_CHAR_PATTERN.matcher(password).find()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD_NO_SPECIAL_CHAR",
                    "Mật khẩu phải chứa ít nhất 1 ký tự đặc biệt (!@#$%^&*...).");
        }

        String lowerPassword = password.toLowerCase(Locale.ROOT);

        if (COMMON_PASSWORDS.contains(lowerPassword)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COMMON_PASSWORD_REJECTED",
                    "Mật khẩu quá phổ biến hoặc dễ đoán. Vui lòng chọn mật khẩu an toàn hơn.");
        }

        // Prevent password from matching email prefix
        if (email != null && email.contains("@")) {
            String emailPrefix = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT);
            if (emailPrefix.length() >= 4 && lowerPassword.contains(emailPrefix)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "PASSWORD_CONTAINS_EMAIL",
                        "Mật khẩu không được chứa tên tài khoản hoặc phần đầu email.");
            }
        }

        // Prevent password from matching full name
        if (fullName != null && !fullName.isBlank()) {
            String[] parts = fullName.trim().toLowerCase(Locale.ROOT).split("\\s+");
            for (String part : parts) {
                if (part.length() >= 3 && lowerPassword.contains(part)) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "PASSWORD_CONTAINS_NAME",
                            "Mật khẩu không được chứa họ tên của bạn.");
                }
            }
        }
    }

    /**
     * Validates and normalizes Vietnamese phone number into standard 10-digit format (0xxxxxxxxx).
     *
     * @param rawPhone raw phone string
     * @return normalized phone number
     */
    public String validateAndNormalizePhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return null;
        }

        String cleaned = rawPhone.replaceAll("[\\s\\-\\(\\)]", "");

        if (cleaned.startsWith("+84")) {
            cleaned = "0" + cleaned.substring(3);
        }

        if (!VIETNAMESE_PHONE_PATTERN.matcher(cleaned).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PHONE_FORMAT",
                    "Số điện thoại không đúng định dạng mạng viễn thông Việt Nam (Ví dụ: 0912345678 hoặc +84912345678).");
        }

        return cleaned;
    }
}
