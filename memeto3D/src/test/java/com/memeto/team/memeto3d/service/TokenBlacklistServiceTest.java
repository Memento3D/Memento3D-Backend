package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.entity.BlacklistedToken;
import com.memeto.team.memeto3d.repository.BlacklistedTokenRepository;
import com.memeto.team.memeto3d.service.impl.DatabaseTokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @InjectMocks
    private DatabaseTokenBlacklistService tokenBlacklistService;

    private final String sampleToken = "sample.jwt.token";

    @Test
    @DisplayName("Blacklist token - Save token to database")
    void testBlacklistToken() {
        when(blacklistedTokenRepository.existsByToken(sampleToken)).thenReturn(false);

        tokenBlacklistService.blacklistToken(sampleToken, Instant.now().plusSeconds(3600));

        verify(blacklistedTokenRepository, times(1)).save(any(BlacklistedToken.class));
    }

    @Test
    @DisplayName("Check if token is blacklisted - returns true when blacklisted")
    void testIsBlacklistedTrue() {
        when(blacklistedTokenRepository.existsByToken(sampleToken)).thenReturn(true);

        boolean result = tokenBlacklistService.isBlacklisted(sampleToken);

        assertTrue(result);
    }

    @Test
    @DisplayName("Check if token is blacklisted - returns false when not blacklisted")
    void testIsBlacklistedFalse() {
        when(blacklistedTokenRepository.existsByToken(sampleToken)).thenReturn(false);

        boolean result = tokenBlacklistService.isBlacklisted(sampleToken);

        assertFalse(result);
    }
}
