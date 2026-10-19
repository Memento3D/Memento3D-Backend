package com.memento3d.identity.application.service.impl;

import com.memento3d.identity.api.request.ConsentRequest;
import com.memento3d.identity.api.request.LoginRequest;
import com.memento3d.identity.api.request.RefreshTokenRequest;
import com.memento3d.identity.api.request.RegisterRequest;
import com.memento3d.identity.api.request.ResendVerificationRequest;
import com.memento3d.identity.api.request.VerifyEmailRequest;
import com.memento3d.identity.api.response.AuthResponse;
import com.memento3d.identity.api.response.LegalDocumentResponse;
import com.memento3d.identity.api.response.LogoutResponse;
import com.memento3d.identity.api.response.RegisterResponse;
import com.memento3d.identity.api.response.UserResponse;
import com.memento3d.identity.api.response.VerifyEmailResponse;
import com.memento3d.identity.domain.model.AuthProvider;
import com.memento3d.identity.domain.model.EmailVerificationToken;
import com.memento3d.identity.domain.model.LegalDocument;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import com.memento3d.identity.domain.model.UserConsent;
import com.memento3d.identity.domain.model.UserIdentity;
import com.memento3d.identity.domain.model.UserStatus;
import com.memento3d.identity.domain.exception.EmailAlreadyExistsException;
import com.memento3d.identity.domain.exception.EmailNotVerifiedException;
import com.memento3d.identity.domain.exception.InvalidTokenException;
import com.memento3d.identity.domain.exception.TermsAgreementRequiredException;
import com.memento3d.identity.infrastructure.persistence.repository.EmailVerificationTokenRepository;
import com.memento3d.identity.infrastructure.persistence.repository.LegalDocumentRepository;
import com.memento3d.identity.infrastructure.persistence.repository.UserConsentRepository;
import com.memento3d.identity.infrastructure.persistence.repository.UserIdentityRepository;
import com.memento3d.identity.infrastructure.persistence.repository.UserRepository;
import com.memento3d.identity.infrastructure.security.JwtService;
import com.memento3d.identity.application.service.AuthService;
import com.memento3d.identity.application.service.RefreshTokenService;
import com.memento3d.identity.application.service.TokenBlacklistService;
import com.memento3d.shared.exception.BusinessException;
import com.memento3d.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final LegalDocumentRepository legalDocumentRepository;
    private final UserConsentRepository userConsentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final TokenBlacklistService tokenBlacklistService;
    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // Enforce Terms of Service agreement
        if (request.getAgreeTerms() == null || !request.getAgreeTerms()) {
            throw new TermsAgreementRequiredException("Bạn phải đọc và đồng ý với Điều khoản sử dụng và Chính sách quyền riêng tư để đăng ký tài khoản.");
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("Registration failed: Email {} is already registered", normalizedEmail);
            throw new EmailAlreadyExistsException("Email is already registered: " + request.getEmail());
        }

        // Public registration always assigns ROLE_USER (Customer)
        User user = User.builder()
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .role(Role.ROLE_USER)
                .provider(AuthProvider.LOCAL)
                .emailVerified(false)
                .status(UserStatus.PENDING_VERIFICATION)
                .build();

        User savedUser = userRepository.save(user);

        // Link LOCAL identity
        UserIdentity localIdentity = UserIdentity.builder()
                .user(savedUser)
                .provider(AuthProvider.LOCAL)
                .providerSubject(normalizedEmail)
                .build();
        userIdentityRepository.save(localIdentity);

        // Record User Consent for TERMS
        String termsVer = (request.getTermsVersion() != null && !request.getTermsVersion().isBlank()) 
                ? request.getTermsVersion() : "1.0";
        LegalDocument currentTerms = legalDocumentRepository
                .findFirstByTypeAndActiveTrueOrderByEffectiveAtDesc("TERMS")
                .orElse(null);

        UserConsent termsConsent = UserConsent.builder()
                .user(savedUser)
                .documentId(currentTerms != null ? currentTerms.getId() : null)
                .documentType("TERMS")
                .documentVersion(termsVer)
                .consentType("TERMS")
                .accepted(true)
                .consentSource("WEB_REGISTER")
                .build();
        userConsentRepository.save(termsConsent);

        // Independent opt-in Marketing Consent
        if (Boolean.TRUE.equals(request.getMarketingConsent())) {
            UserConsent marketingConsent = UserConsent.builder()
                    .user(savedUser)
                    .documentType("MARKETING")
                    .documentVersion("1.0")
                    .consentType("MARKETING")
                    .accepted(true)
                    .consentSource("WEB_REGISTER")
                    .build();
            userConsentRepository.save(marketingConsent);
        }

        // Generate email verification token (24-hour expiration)
        String verificationTokenStr = UUID.randomUUID().toString();
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(savedUser)
                .token(verificationTokenStr)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        emailVerificationTokenRepository.save(verificationToken);

        log.info("Email verification link generated for {}: http://localhost:3000/verify-email/confirm?token={}",
                normalizedEmail, verificationTokenStr);
        log.info("Successfully registered user with id: {} and email: {}", savedUser.getId(), savedUser.getEmail());

        return RegisterResponse.builder()
                .message("Register successfully. Please check your email to verify your account.")
                .user(UserResponse.fromEntity(savedUser))
                .verificationRequired(true)
                .verificationToken(verificationTokenStr)
                .build();
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
        );

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + normalizedEmail));

        // Check account status
        if (user.getStatus() == UserStatus.SUSPENDED) {
            log.warn("Login attempt for suspended account: {}", normalizedEmail);
            throw new AccessDeniedException("Tài khoản đã bị khóa. Vui lòng liên hệ bộ phận hỗ trợ.");
        }

        // Check email verification
        if (Boolean.FALSE.equals(user.getEmailVerified()) || user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            log.warn("Login attempt for unverified email: {}", normalizedEmail);
            throw new EmailNotVerifiedException("Tài khoản chưa được xác thực email. Vui lòng kiểm tra hộp thư hoặc yêu cầu gửi lại liên kết xác thực.", normalizedEmail);
        }

        String token = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user);
        log.info("User {} successfully authenticated with persistent refresh token", normalizedEmail);

        return AuthResponse.builder()
                .accessToken(token)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationInSeconds())
                .user(UserResponse.fromEntity(user))
                .build();
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            throw new InvalidTokenException("Refresh token is required");
        }

        String rawToken = request.getRefreshToken().trim();

        UUID userId = refreshTokenService.validateRefreshToken(rawToken)
                .orElseThrow(() -> new InvalidTokenException("Invalid, expired, or revoked refresh token"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new AccessDeniedException("Tài khoản đã bị khóa.");
        }

        // Revoke the old refresh token and rotate to a new one
        refreshTokenService.revokeRefreshToken(rawToken);

        String newAccessToken = jwtService.generateToken(user);
        String newRefreshToken = refreshTokenService.createRefreshToken(user);

        log.info("Successfully refreshed access token & rotated refresh token for user ID: {}", userId);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationInSeconds())
                .user(UserResponse.fromEntity(user))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new InvalidTokenException("No authenticated user in security context");
        }

        String email;
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            email = userDetails.getUsername();
        } else {
            email = principal.toString();
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        return UserResponse.fromEntity(user);
    }

    @Override
    @Transactional
    public LogoutResponse logout(String bearerToken) {
        return logout(bearerToken, null);
    }

    @Override
    @Transactional
    public LogoutResponse logout(String bearerToken, String refreshTokenStr) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7).trim();
            try {
                Date expiration = jwtService.extractExpiration(token);
                tokenBlacklistService.blacklistToken(token, expiration.toInstant());
                log.info("Access token successfully blacklisted in Redis on logout");
            } catch (Exception e) {
                log.warn("Could not extract expiration from token on logout: {}", e.getMessage());
                tokenBlacklistService.blacklistToken(token, Instant.now().plusSeconds(3600));
            }
        }

        if (refreshTokenStr != null && !refreshTokenStr.isBlank()) {
            refreshTokenService.revokeRefreshToken(refreshTokenStr.trim());
            log.info("Refresh token successfully revoked on logout");
        } else {
            try {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getPrincipal() instanceof UserDetails userDetails) {
                    userRepository.findByEmail(userDetails.getUsername())
                            .ifPresent(u -> refreshTokenService.revokeUserRefreshTokens(u.getId()));
                }
            } catch (Exception e) {
                log.warn("Could not revoke user refresh tokens on logout: {}", e.getMessage());
            }
        }

        SecurityContextHolder.clearContext();

        return LogoutResponse.builder()
                .message("Logout successfully")
                .details("Session revoked and token blacklisted successfully")
                .build();
    }

    @Override
    @Transactional
    public VerifyEmailResponse verifyEmail(VerifyEmailRequest request) {
        if (request == null || request.getToken() == null || request.getToken().isBlank()) {
            throw new InvalidTokenException("Verification token is required");
        }

        String tokenStr = request.getToken().trim();
        EmailVerificationToken token = emailVerificationTokenRepository.findByToken(tokenStr)
                .orElseThrow(() -> new InvalidTokenException("Token xác thực không hợp lệ."));

        if (token.isUsed()) {
            throw new InvalidTokenException("Token xác thực này đã được sử dụng trước đó.");
        }

        if (token.isExpired()) {
            throw new InvalidTokenException("Token xác thực đã hết hạn. Vui lòng yêu cầu gửi lại email xác thực mới.");
        }

        // Mark token as used
        token.setUsedAt(Instant.now());
        emailVerificationTokenRepository.save(token);

        // Activate user
        User user = token.getUser();
        user.setEmailVerified(true);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        log.info("Successfully verified email for user: {}", user.getEmail());

        return VerifyEmailResponse.builder()
                .message("Xác thực email thành công! Bạn có thể đăng nhập ngay bây giờ.")
                .email(user.getEmail())
                .verified(true)
                .build();
    }

    @Override
    @Transactional
    public void resendVerificationEmail(ResendVerificationRequest request) {
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()) {
            return;
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);

        if (user == null || Boolean.TRUE.equals(user.getEmailVerified())) {
            // Silently return to prevent user enumeration attacks
            return;
        }

        // Anti-spam rate limiting: check recent token creation within 60 seconds
        emailVerificationTokenRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId())
                .ifPresent(recentToken -> {
                    long secondsSinceCreated = Duration.between(recentToken.getCreatedAt(), Instant.now()).getSeconds();
                    if (secondsSinceCreated < 60) {
                        log.warn("Rate limit triggered for resending verification email to {}", normalizedEmail);
                        throw new BusinessException(
                                HttpStatus.TOO_MANY_REQUESTS,
                                "RATE_LIMIT_EXCEEDED",
                                "Vui lòng đợi ít nhất " + (60 - secondsSinceCreated) + " giây trước khi yêu cầu gửi lại email xác thực."
                        );
                    }
                });

        // Generate and save new token
        String tokenStr = UUID.randomUUID().toString();
        EmailVerificationToken newToken = EmailVerificationToken.builder()
                .user(user)
                .token(tokenStr)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        emailVerificationTokenRepository.save(newToken);

        log.info("New email verification link resent for {}: http://localhost:3000/verify-email/confirm?token={}",
                normalizedEmail, tokenStr);
    }

    @Override
    @Transactional
    public AuthResponse completeConsents(ConsentRequest request, String ipAddress, String userAgent) {
        if (request == null || request.getAgreeTerms() == null || !request.getAgreeTerms()) {
            throw new TermsAgreementRequiredException("Bạn phải đọc và đồng ý với Điều khoản sử dụng.");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new InvalidTokenException("No authenticated onboarding session found");
        }

        String email;
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            email = userDetails.getUsername();
        } else {
            email = principal.toString();
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        String termsVer = (request.getTermsVersion() != null && !request.getTermsVersion().isBlank()) 
                ? request.getTermsVersion() : "1.0";
        LegalDocument currentTerms = legalDocumentRepository
                .findFirstByTypeAndActiveTrueOrderByEffectiveAtDesc("TERMS")
                .orElse(null);

        UserConsent termsConsent = UserConsent.builder()
                .user(user)
                .documentId(currentTerms != null ? currentTerms.getId() : null)
                .documentType("TERMS")
                .documentVersion(termsVer)
                .consentType("TERMS")
                .accepted(true)
                .consentSource("GOOGLE_ONBOARDING")
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
        userConsentRepository.save(termsConsent);

        if (Boolean.TRUE.equals(request.getMarketingConsent())) {
            UserConsent marketingConsent = UserConsent.builder()
                    .user(user)
                    .documentType("MARKETING")
                    .documentVersion("1.0")
                    .consentType("MARKETING")
                    .accepted(true)
                    .consentSource("GOOGLE_ONBOARDING")
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build();
            userConsentRepository.save(marketingConsent);
        }

        // Issue full session tokens
        String accessToken = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user);

        log.info("User {} successfully completed required agreements via Google onboarding", user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationInSeconds())
                .user(UserResponse.fromEntity(user))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public LegalDocumentResponse getCurrentLegalDocument(String type) {
        String docType = type != null ? type.trim().toUpperCase() : "TERMS";
        LegalDocument doc = legalDocumentRepository
                .findFirstByTypeAndActiveTrueOrderByEffectiveAtDesc(docType)
                .orElseThrow(() -> new ResourceNotFoundException("Legal document not found for type: " + docType));

        return LegalDocumentResponse.fromEntity(doc);
    }
}
