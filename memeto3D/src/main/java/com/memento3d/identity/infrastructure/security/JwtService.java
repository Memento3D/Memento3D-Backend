package com.memento3d.identity.infrastructure.security;

import com.memento3d.identity.domain.model.Role;

import com.memento3d.identity.domain.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
@Slf4j
public class JwtService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms:3600000}")
    private long jwtExpirationMs;

    public String generateToken(User user) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("userId", user.getId().toString());
        extraClaims.put("email", user.getEmail());
        extraClaims.put("role", user.getRole().name());
        return buildToken(extraClaims, user.getEmail(), jwtExpirationMs);
    }

    public String generateToken(Map<String, Object> extraClaims, String subject, long expirationMs) {
        return buildToken(extraClaims, subject, expirationMs);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expirationMs) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public UUID extractUserId(String token) {
        String userIdStr = extractClaim(token, claims -> claims.get("userId", String.class));
        return userIdStr != null ? UUID.fromString(userIdStr) : null;
    }

    public Date extractIssuedAt(String token) {
        return extractClaim(token, Claims::getIssuedAt);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        if (!username.equals(userDetails.getUsername()) || isTokenExpired(token)) {
            return false;
        }

        if (userDetails instanceof CustomUserDetails cud && cud.getUser() != null) {
            java.time.Instant pwdChangedAt = cud.getUser().getPasswordChangedAt();
            if (pwdChangedAt != null) {
                Date issuedAt = extractIssuedAt(token);
                // Allow a 1-second margin for timestamp precision between DB and JWT millis
                if (issuedAt != null && issuedAt.toInstant().plusSeconds(1).isBefore(pwdChangedAt)) {
                    log.warn("Token rejected for user {}: issued at {} before passwordChangedAt {}",
                            username, issuedAt.toInstant(), pwdChangedAt);
                    return false;
                }
            }
        }

        return true;
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public long getExpirationInSeconds() {
        return jwtExpirationMs / 1000;
    }

    public long getJwtExpirationMs() {
        return jwtExpirationMs;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes;
        try {
            // Attempt to decode as base64 first
            keyBytes = Decoders.BASE64.decode(jwtSecret);
            if (keyBytes.length < 32) {
                // If decoded key is too short for HMAC-SHA256 (needs >= 256 bits / 32 bytes)
                keyBytes = sha256(jwtSecret.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            // Fallback: derive 256-bit key from raw string via SHA-256
            keyBytes = sha256(jwtSecret.getBytes(StandardCharsets.UTF_8));
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private byte[] sha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
