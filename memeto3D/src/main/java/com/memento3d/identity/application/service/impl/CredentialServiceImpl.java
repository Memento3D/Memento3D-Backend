package com.memento3d.identity.application.service.impl;

import com.memento3d.identity.application.service.CredentialService;
import com.memento3d.identity.application.service.RefreshTokenService;
import com.memento3d.identity.application.service.UserCacheService;
import com.memento3d.identity.domain.model.User;
import com.memento3d.identity.infrastructure.persistence.repository.UserRepository;
import com.memento3d.shared.application.service.AuditLogService;
import com.memento3d.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CredentialServiceImpl implements CredentialService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final UserCacheService userCacheService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public void updatePassword(UUID userId, String rawPassword, String auditAction) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);

        // Kill every persistent session; access tokens older than passwordChangedAt are rejected by the JWT filter.
        refreshTokenService.revokeUserRefreshTokens(userId);
        userCacheService.evictUser(userId);

        // Audit entry intentionally carries no password / token material.
        auditLogService.log(userId, auditAction, "USER", userId.toString(), null,
                "{\"sessionsRevoked\":true}");
        log.info("Password updated for user {} (action={}); sessions revoked", userId, auditAction);
    }
}
