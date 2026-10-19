package com.memento3d.identity.application.service.impl;

import com.memento3d.identity.application.service.CredentialService;
import com.memento3d.identity.application.service.PasswordPolicyService;
import com.memento3d.identity.application.service.PasswordResetService;
import com.memento3d.identity.domain.model.User;
import com.memento3d.identity.domain.model.UserStatus;
import com.memento3d.identity.infrastructure.persistence.repository.UserRepository;
import com.memento3d.notification.service.EmailService;
import com.memento3d.shared.exception.BusinessException;
import com.memento3d.shared.security.RateLimiterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis layout:
 * <pre>
 *   auth:pwreset:token:{sha256(token)} -> userId      (TTL = token-ttl-minutes)
 *   auth:pwreset:user:{userId}         -> sha256(token) (only the newest token per user stays valid)
 * </pre>
 * The plaintext token exists only in the email link; it is never stored or logged.
 */
@Service
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

    static final String TOKEN_KEY_PREFIX = "auth:pwreset:token:";
    static final String USER_KEY_PREFIX = "auth:pwreset:user:";
    private static final int TOKEN_BYTES = 32; // 256-bit entropy
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final RateLimiterService rateLimiter;
    private final PasswordPolicyService passwordPolicy;
    private final CredentialService credentialService;
    private final EmailService emailService;
    private final String frontendBaseUrl;
    private final int tokenTtlMinutes;

    public PasswordResetServiceImpl(
            UserRepository userRepository,
            StringRedisTemplate redisTemplate,
            RateLimiterService rateLimiter,
            PasswordPolicyService passwordPolicy,
            CredentialService credentialService,
            EmailService emailService,
            @Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl,
            @Value("${app.security.password-reset.token-ttl-minutes:15}") int tokenTtlMinutes
    ) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.rateLimiter = rateLimiter;
        this.passwordPolicy = passwordPolicy;
        this.credentialService = credentialService;
        this.emailService = emailService;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
        this.tokenTtlMinutes = tokenTtlMinutes;
    }

    // ------------------------------------------------------------------------------------
    // Forgot password
    // ------------------------------------------------------------------------------------
    @Override
    public void requestPasswordReset(String email, String clientIp) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();

        // Limits are applied BEFORE the account lookup and regardless of existence -> no enumeration signal.
        if (!rateLimiter.tryAcquire("rl:pwforgot:ip:" + clientIp, 10, Duration.ofHours(1))
                || !rateLimiter.tryAcquire("rl:pwforgot:email:" + sha256Hex(normalizedEmail), 3, Duration.ofHours(1))) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                    "Bạn đã yêu cầu quá nhiều lần. Vui lòng thử lại sau.");
        }

        Optional<User> maybeUser = userRepository.findByEmail(normalizedEmail);
        if (maybeUser.isEmpty()) {
            log.debug("Password reset requested for unknown email (silently ignored)");
            return;
        }

        User user = maybeUser.get();
        if (user.getStatus() != UserStatus.ACTIVE || Boolean.FALSE.equals(user.getEmailVerified())) {
            log.info("Password reset ignored for user {}: status={}, emailVerified={}",
                    user.getId(), user.getStatus(), user.getEmailVerified());
            return;
        }

        try {
            if (user.getPassword() == null || user.getPassword().isBlank()) {
                // Google-only account: never mint a password through this flow.
                emailService.sendGoogleAccountNotice(user.getEmail(), user.getFullName());
                return;
            }

            String token = generateToken();
            storeToken(user.getId(), token);
            String link = frontendBaseUrl + "/reset-password?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
            emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), link, tokenTtlMinutes);
            log.info("Password reset email queued for user {}", user.getId());
        } catch (Exception e) {
            // Must not alter the HTTP response (would reveal that the account exists).
            log.error("Password reset request failed for user {}: {}", user.getId(), e.getClass().getSimpleName());
        }
    }

    // ------------------------------------------------------------------------------------
    // Reset password
    // ------------------------------------------------------------------------------------
    @Override
    public void resetPassword(String token, String newPassword, String clientIp) {
        if (!rateLimiter.tryAcquire("rl:pwreset:ip:" + clientIp, 10, Duration.ofMinutes(15))) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                    "Bạn thao tác quá nhiều lần. Vui lòng thử lại sau.");
        }
        if (token == null || token.isBlank()) {
            throw invalidToken();
        }

        String tokenHash = sha256Hex(token.trim());
        String tokenKey = TOKEN_KEY_PREFIX + tokenHash;

        // 1) Peek (non destructive) so a weak password does not burn the user's only link.
        String userIdStr = readRedis(() -> redisTemplate.opsForValue().get(tokenKey));
        if (userIdStr == null) {
            throw invalidToken();
        }
        User user = loadEligibleUser(userIdStr);
        passwordPolicy.validate(newPassword, user.getEmail());

        // 2) Consume atomically (GETDEL): exactly one concurrent request wins.
        Long remainingTtl = readRedis(() -> redisTemplate.getExpire(tokenKey));
        String consumed = readRedis(() -> redisTemplate.opsForValue().getAndDelete(tokenKey));
        if (consumed == null) {
            throw invalidToken();
        }
        deleteQuietly(USER_KEY_PREFIX + consumed);

        // 3) Apply. If persisting fails, give the token back so the user can retry.
        try {
            credentialService.updatePassword(user.getId(), newPassword, "PASSWORD_RESET");
        } catch (RuntimeException e) {
            restoreToken(tokenKey, user.getId(), tokenHash, remainingTtl);
            throw e;
        }

        try {
            emailService.sendPasswordChangedNotification(user.getEmail(), user.getFullName());
        } catch (Exception e) {
            log.warn("Password changed notification failed for user {}: {}", user.getId(), e.getClass().getSimpleName());
        }
    }

    // ------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------
    private User loadEligibleUser(String userIdStr) {
        try {
            User user = userRepository.findById(UUID.fromString(userIdStr)).orElseThrow(this::invalidToken);
            if (user.getStatus() != UserStatus.ACTIVE) {
                throw invalidToken();
            }
            return user;
        } catch (IllegalArgumentException e) {
            throw invalidToken();
        }
    }

    private void storeToken(UUID userId, String token) {
        String hash = sha256Hex(token);
        Duration ttl = Duration.ofMinutes(tokenTtlMinutes);
        String userKey = USER_KEY_PREFIX + userId;

        // Invalidate any previous outstanding link for this user.
        String previousHash = redisTemplate.opsForValue().get(userKey);
        if (previousHash != null) {
            redisTemplate.delete(TOKEN_KEY_PREFIX + previousHash);
        }
        redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + hash, userId.toString(), ttl);
        redisTemplate.opsForValue().set(userKey, hash, ttl);
    }

    private void restoreToken(String tokenKey, UUID userId, String tokenHash, Long remainingSeconds) {
        try {
            long seconds = remainingSeconds != null && remainingSeconds > 0 ? remainingSeconds : 60;
            redisTemplate.opsForValue().set(tokenKey, userId.toString(), Duration.ofSeconds(seconds));
            redisTemplate.opsForValue().set(USER_KEY_PREFIX + userId, tokenHash, Duration.ofSeconds(seconds));
        } catch (Exception e) {
            log.error("Could not restore reset token after failed update for user {}", userId);
        }
    }

    private void deleteQuietly(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.debug("Could not delete key {}", key);
        }
    }

    private <T> T readRedis(java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.error("Redis unavailable during password reset: {}", e.getClass().getSimpleName());
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "RESET_SERVICE_UNAVAILABLE",
                    "Hệ thống tạm thời chưa thể xử lý yêu cầu. Vui lòng thử lại sau ít phút.");
        }
    }

    private BusinessException invalidToken() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN",
                "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn. Vui lòng yêu cầu liên kết mới.");
    }

    static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
