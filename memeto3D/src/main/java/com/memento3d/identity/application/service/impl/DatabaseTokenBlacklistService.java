package com.memento3d.identity.application.service.impl;

import com.memento3d.identity.domain.model.BlacklistedToken;
import com.memento3d.identity.infrastructure.persistence.repository.BlacklistedTokenRepository;
import com.memento3d.identity.application.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseTokenBlacklistService implements TokenBlacklistService {

    private final BlacklistedTokenRepository blacklistedTokenRepository;

    @Override
    @Transactional
    public void blacklistToken(String token, Instant expiryDate) {
        if (token == null || token.isBlank()) {
            return;
        }
        if (!blacklistedTokenRepository.existsByToken(token)) {
            BlacklistedToken blacklistedToken = BlacklistedToken.builder()
                    .token(token)
                    .expiryDate(expiryDate != null ? expiryDate : Instant.now().plusSeconds(3600))
                    .build();
            blacklistedTokenRepository.save(blacklistedToken);
            log.info("Token successfully added to blacklist until {}", expiryDate);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return blacklistedTokenRepository.existsByToken(token);
    }

    /**
     * Purges expired tokens from the blacklist database every hour
     * to keep table size lean and performant.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void purgeExpiredTokens() {
        log.info("Purging expired blacklisted tokens...");
        blacklistedTokenRepository.deleteExpiredTokens(Instant.now());
    }
}
