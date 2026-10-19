package com.memento3d.identity.infrastructure.security.oauth2;

import com.memento3d.identity.domain.model.AuthProvider;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import com.memento3d.identity.domain.model.UserIdentity;
import com.memento3d.identity.domain.model.UserStatus;
import com.memento3d.identity.infrastructure.persistence.repository.UserIdentityRepository;
import com.memento3d.identity.infrastructure.persistence.repository.UserRepository;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest oAuth2UserRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(oAuth2UserRequest);

        try {
            return processOAuth2User(oAuth2UserRequest, oAuth2User);
        } catch (OAuth2AuthenticationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error occurred while processing OAuth2 user", ex);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("oauth2_processing_error", "Failed to process OAuth2 user: " + ex.getMessage(), null)
            );
        }
    }

    public User processUserFromOAuth2(OAuth2User oAuth2User, String registrationId) {
        String regId = registrationId != null ? registrationId : "google";
        OAuth2UserInfo oAuth2UserInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(regId, oAuth2User.getAttributes());

        if (!StringUtils.hasText(oAuth2UserInfo.getEmail())) {
            log.error("Email not found in OAuth2 provider response for registrationId: {}", regId);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_not_found", "Email not found from OAuth2 provider", null)
            );
        }

        // Verify that the email is verified by Google
        Object emailVerifiedClaim = oAuth2User.getAttributes().get("email_verified");
        if (emailVerifiedClaim instanceof Boolean verified && !verified) {
            log.error("Google account email is not verified by Google");
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_not_verified", "Google email is not verified by identity provider", null)
            );
        }

        String email = oAuth2UserInfo.getEmail().trim().toLowerCase();
        String sub = oAuth2UserInfo.getId();
        AuthProvider provider = AuthProvider.valueOf(regId.toUpperCase());

        // 1. Look for linked identity by provider and providerSubject (Google sub)
        Optional<UserIdentity> identityOptional = userIdentityRepository.findByProviderAndProviderSubject(provider, sub);

        User user;
        if (identityOptional.isPresent()) {
            // Existing linked Google user
            user = updateExistingUser(identityOptional.get().getUser(), oAuth2UserInfo);
        } else {
            // Identity not yet linked. Check if user with this email already exists
            Optional<User> existingEmailUser = userRepository.findByEmail(email);

            if (existingEmailUser.isPresent()) {
                // Rule: Do NOT automatically link Google account to existing local password account
                // User must log in with existing credentials to link accounts securely
                log.warn("OAuth2 login failed: Account with email {} already exists with password. Manual link required.", email);
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("account_exists_link_required",
                                "Tài khoản với email này đã tồn tại bằng mật khẩu. Vui lòng đăng nhập bằng mật khẩu trước để liên kết tài khoản Google.",
                                null)
                );
            } else {
                // Brand new user registration via Google
                user = registerNewUser(oAuth2UserInfo, provider, sub);
            }
        }

        return user;
    }

    private OAuth2User processOAuth2User(OAuth2UserRequest oAuth2UserRequest, OAuth2User oAuth2User) {
        String registrationId = oAuth2UserRequest.getClientRegistration().getRegistrationId();
        User user = processUserFromOAuth2(oAuth2User, registrationId);
        return new CustomUserDetails(user, oAuth2User.getAttributes());
    }

    private User registerNewUser(OAuth2UserInfo oAuth2UserInfo, AuthProvider provider, String sub) {
        log.info("Registering new Google OAuth2 customer with email: {}", oAuth2UserInfo.getEmail());

        String fullName = StringUtils.hasText(oAuth2UserInfo.getName())
                ? oAuth2UserInfo.getName().trim()
                : oAuth2UserInfo.getEmail().split("@")[0];

        User user = User.builder()
                .email(oAuth2UserInfo.getEmail().trim().toLowerCase())
                .fullName(fullName)
                .provider(provider)
                .providerId(sub)
                .avatarUrl(oAuth2UserInfo.getImageUrl())
                .role(Role.ROLE_USER)
                .emailVerified(true) // Google verified
                .status(UserStatus.ACTIVE)
                .storageUsedBytes(0L)
                .storageQuotaBytes(10737418240L) // 10 GB default
                .build();

        User savedUser = userRepository.save(user);

        // Record UserIdentity
        UserIdentity identity = UserIdentity.builder()
                .user(savedUser)
                .provider(provider)
                .providerSubject(sub)
                .build();
        userIdentityRepository.save(identity);

        return savedUser;
    }

    private User updateExistingUser(User existingUser, OAuth2UserInfo oAuth2UserInfo) {
        log.info("Updating profile for returning Google user: {}", existingUser.getEmail());

        boolean updated = false;

        if (StringUtils.hasText(oAuth2UserInfo.getName()) && !oAuth2UserInfo.getName().equals(existingUser.getFullName())) {
            existingUser.setFullName(oAuth2UserInfo.getName().trim());
            updated = true;
        }

        if (StringUtils.hasText(oAuth2UserInfo.getImageUrl()) && !oAuth2UserInfo.getImageUrl().equals(existingUser.getAvatarUrl())) {
            existingUser.setAvatarUrl(oAuth2UserInfo.getImageUrl());
            updated = true;
        }

        if (Boolean.FALSE.equals(existingUser.getEmailVerified())) {
            existingUser.setEmailVerified(true);
            updated = true;
        }

        if (updated) {
            return userRepository.save(existingUser);
        }

        return existingUser;
    }
}
