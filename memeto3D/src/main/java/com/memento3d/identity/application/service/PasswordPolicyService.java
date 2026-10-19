package com.memento3d.identity.application.service;

import com.memento3d.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

/**
 * Password policy for reset / change flows (NIST SP 800-63B oriented):
 * length first (>= 12), passphrases and any characters allowed, no forced composition rules,
 * but reject trivially guessable values (common-password list, containing the account name,
 * very low character variety).
 * <p>
 * The embedded list is a small offline blocklist; plug a breached-password API (e.g. HIBP
 * k-anonymity) behind {@link #validate(String, String)} if desired.
 */
@Service
public class PasswordPolicyService {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password123", "passw0rd", "123456789012", "1234567890123",
            "qwertyuiop12", "qwertyuiopas", "qwerty123456", "iloveyou1234", "admin1234567",
            "administrator", "welcome12345", "letmein12345", "memento3d1234", "memento3d12345",
            "memento3dmemento3d", "abc123456789", "abcdefghijkl", "111111111111", "000000000000",
            "motdaihaibonam", "matkhau123456", "matkhaumatkhau", "vietnam123456", "11111111111111",
            "changeme1234", "p@ssw0rd1234", "password1234", "passwordpassword"
    );

    /**
     * @throws BusinessException 400 WEAK_PASSWORD with a user-facing Vietnamese message.
     */
    public void validate(String password, String email) {
        if (password == null || password.isBlank()) {
            throw weak("Mật khẩu không được để trống.");
        }
        if (password.length() < MIN_LENGTH) {
            throw weak("Mật khẩu phải có ít nhất " + MIN_LENGTH + " ký tự. Bạn có thể dùng một cụm từ dễ nhớ (passphrase).");
        }
        if (password.length() > MAX_LENGTH) {
            throw weak("Mật khẩu không được vượt quá " + MAX_LENGTH + " ký tự.");
        }

        String lower = password.toLowerCase(Locale.ROOT);
        String lettersDigitsOnly = lower.replaceAll("[^\\p{L}\\p{N}]", "");
        if (COMMON.contains(lower) || COMMON.contains(lettersDigitsOnly)) {
            throw weak("Mật khẩu này quá phổ biến và dễ bị đoán. Vui lòng chọn mật khẩu khác.");
        }

        if (lower.chars().distinct().count() < 5) {
            throw weak("Mật khẩu quá đơn giản (lặp lại ít ký tự). Vui lòng chọn mật khẩu khác.");
        }

        if (email != null && !email.isBlank()) {
            String local = email.toLowerCase(Locale.ROOT).split("@")[0];
            if (local.length() >= 4 && lower.contains(local)) {
                throw weak("Mật khẩu không được chứa tên đăng nhập/email của bạn.");
            }
        }
    }

    private BusinessException weak(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD", message);
    }
}
