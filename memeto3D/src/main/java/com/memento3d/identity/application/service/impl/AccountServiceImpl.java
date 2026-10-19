package com.memento3d.identity.application.service.impl;

import com.memento3d.assets.infrastructure.storage.CloudinaryStorageService;
import com.memento3d.identity.api.request.ChangePasswordRequest;
import com.memento3d.identity.api.response.UserResponse;
import com.memento3d.identity.application.service.AccountService;
import com.memento3d.identity.application.service.CredentialService;
import com.memento3d.identity.application.service.PasswordPolicyService;
import com.memento3d.identity.application.service.TokenBlacklistService;
import com.memento3d.identity.application.service.UserCacheService;
import com.memento3d.identity.domain.model.User;
import com.memento3d.identity.infrastructure.persistence.repository.UserRepository;
import com.memento3d.identity.infrastructure.security.JwtService;
import com.memento3d.notification.service.EmailService;
import com.memento3d.shared.application.service.AuditLogService;
import com.memento3d.shared.exception.BusinessException;
import com.memento3d.shared.exception.ResourceNotFoundException;
import com.memento3d.shared.security.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountServiceImpl implements AccountService {

    static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024; // 5 MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicy;
    private final CredentialService credentialService;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final RateLimiterService rateLimiter;
    private final CloudinaryStorageService cloudinaryStorageService;
    private final UserCacheService userCacheService;
    private final AuditLogService auditLogService;

    // -----------------------------------------------------------------------------------
    // Change password
    // -----------------------------------------------------------------------------------
    @Override
    public void changePassword(UUID userId, ChangePasswordRequest request, String accessToken) {
        // Brute-force protection on the current-password check (per account, regardless of IP).
        if (!rateLimiter.tryAcquire("rl:pwchange:user:" + userId, 5, Duration.ofMinutes(15))) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                    "Bạn đã thử đổi mật khẩu quá nhiều lần. Vui lòng thử lại sau 15 phút.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "NO_LOCAL_PASSWORD",
                    "Tài khoản đang đăng nhập bằng Google và chưa có mật khẩu riêng. "
                            + "Hãy quản lý bảo mật tại tài khoản Google của bạn.");
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            auditLogService.log(userId, "PASSWORD_CHANGE_FAILED", "USER", userId.toString(), null,
                    "{\"reason\":\"INVALID_CURRENT_PASSWORD\"}");
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD",
                    "Mật khẩu hiện tại không chính xác.");
        }

        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PASSWORD_REUSE_NOT_ALLOWED",
                    "Mật khẩu mới không được trùng với mật khẩu hiện tại.");
        }

        passwordPolicy.validate(request.getNewPassword(), user.getEmail());

        credentialService.updatePassword(userId, request.getNewPassword(), "PASSWORD_CHANGED");
        rateLimiter.reset("rl:pwchange:user:" + userId);

        // Current access token dies immediately (older ones die via passwordChangedAt check in the JWT filter).
        blacklistQuietly(accessToken);

        try {
            emailService.sendPasswordChangedNotification(user.getEmail(), user.getFullName());
        } catch (Exception e) {
            log.warn("Password changed notification failed for user {}: {}", userId, e.getClass().getSimpleName());
        }
    }

    private void blacklistQuietly(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        try {
            Date expiration = jwtService.extractExpiration(accessToken);
            tokenBlacklistService.blacklistToken(accessToken, expiration.toInstant());
        } catch (Exception e) {
            tokenBlacklistService.blacklistToken(accessToken, Instant.now().plusSeconds(3600));
        }
    }

    // -----------------------------------------------------------------------------------
    // Avatar
    // -----------------------------------------------------------------------------------
    @Override
    @Transactional
    public UserResponse uploadAvatar(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badAvatar("Vui lòng chọn một ảnh đại diện.");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw badAvatar("Ảnh đại diện không được vượt quá 5 MB.");
        }
        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED_CONTENT_TYPES.contains(declared)) {
            throw badAvatar("Chỉ hỗ trợ ảnh JPG, PNG hoặc WebP.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw badAvatar("Không thể đọc tệp ảnh đã tải lên.");
        }
        if (bytes.length > MAX_AVATAR_BYTES) {
            throw badAvatar("Ảnh đại diện không được vượt quá 5 MB.");
        }

        // Never trust the client: verify real file signature and that it matches the declared type.
        String detected = detectImageType(bytes);
        if (detected == null || !detected.equals(declared)) {
            throw badAvatar("Nội dung tệp không phải ảnh JPG, PNG hoặc WebP hợp lệ.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        String url;
        try {
            Map<String, Object> result = cloudinaryStorageService.uploadAvatar(bytes, userId);
            Object secureUrl = result.get("secure_url");
            if (secureUrl == null) {
                throw new IOException("Missing secure_url in storage response");
            }
            url = secureUrl.toString();
        } catch (IOException | RuntimeException e) {
            log.error("Avatar upload failed for user {}: {}", userId, e.getMessage());
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "AVATAR_UPLOAD_FAILED",
                    "Không thể tải ảnh lên lúc này. Vui lòng thử lại sau.");
        }

        user.setAvatarUrl(url);
        User saved = userRepository.save(user);
        userCacheService.evictUser(userId);
        auditLogService.log(userId, "AVATAR_UPDATED", "USER", userId.toString(), null, "{\"avatar\":\"updated\"}");
        return UserResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public UserResponse removeAvatar(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        boolean hadAvatar = user.getAvatarUrl() != null;
        user.setAvatarUrl(null);
        User saved = userRepository.save(user);
        userCacheService.evictUser(userId);

        if (hadAvatar) {
            try {
                cloudinaryStorageService.deleteAvatar(userId);
            } catch (Exception e) {
                // Best effort: DB reference is already cleared, the asset is overwritten on next upload.
                log.warn("Could not delete stored avatar for user {}: {}", userId, e.getMessage());
            }
            auditLogService.log(userId, "AVATAR_REMOVED", "USER", userId.toString(), null, null);
        }
        return UserResponse.fromEntity(saved);
    }

    /** @return canonical MIME type based on magic bytes, or null when not JPEG/PNG/WebP. */
    static String detectImageType(byte[] b) {
        if (b == null || b.length < 12) {
            return null;
        }
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if ((b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private BusinessException badAvatar(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_AVATAR", message);
    }
}
