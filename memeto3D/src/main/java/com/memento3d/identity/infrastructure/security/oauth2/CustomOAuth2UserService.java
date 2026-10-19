package com.memento3d.identity.infrastructure.security.oauth2;

import com.memento3d.identity.domain.model.AuthProvider;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
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

        String email = oAuth2UserInfo.getEmail().trim().toLowerCase();
        Optional<User> userOptional = userRepository.findByEmail(email);

        User user;
        if (userOptional.isPresent()) {
            user = updateExistingUser(userOptional.get(), oAuth2UserInfo, regId);
        } else {
            user = registerNewUser(oAuth2UserInfo, regId);
        }

        return user;
    }

    private OAuth2User processOAuth2User(OAuth2UserRequest oAuth2UserRequest, OAuth2User oAuth2User) {
        String registrationId = oAuth2UserRequest.getClientRegistration().getRegistrationId();
        User user = processUserFromOAuth2(oAuth2User, registrationId);
        return new CustomUserDetails(user, oAuth2User.getAttributes());
    }

    private User registerNewUser(OAuth2UserInfo oAuth2UserInfo, String registrationId) {
        log.info("Registering new OAuth2 user with email: {} via provider: {}", oAuth2UserInfo.getEmail(), registrationId);

        String fullName = StringUtils.hasText(oAuth2UserInfo.getName()) 
                ? oAuth2UserInfo.getName().trim() 
                : oAuth2UserInfo.getEmail().split("@")[0];

        AuthProvider provider = AuthProvider.valueOf(registrationId.toUpperCase());

        User user = User.builder()
                .email(oAuth2UserInfo.getEmail().trim().toLowerCase())
                .fullName(fullName)
                .provider(provider)
                .providerId(oAuth2UserInfo.getId())
                .avatarUrl(oAuth2UserInfo.getImageUrl())
                .role(Role.ROLE_USER)
                .storageUsedBytes(0L)
                .storageQuotaBytes(10737418240L) // 10 GB default
                .build();

        return userRepository.save(user);
    }

    private User updateExistingUser(User existingUser, OAuth2UserInfo oAuth2UserInfo, String registrationId) {
        log.info("Updating existing user profile for email: {} with provider: {}", existingUser.getEmail(), registrationId);

        boolean updated = false;

        if (StringUtils.hasText(oAuth2UserInfo.getName()) && !oAuth2UserInfo.getName().equals(existingUser.getFullName())) {
            existingUser.setFullName(oAuth2UserInfo.getName().trim());
            updated = true;
        }

        if (StringUtils.hasText(oAuth2UserInfo.getImageUrl()) && !oAuth2UserInfo.getImageUrl().equals(existingUser.getAvatarUrl())) {
            existingUser.setAvatarUrl(oAuth2UserInfo.getImageUrl());
            updated = true;
        }

        // Link OAuth2 provider information if previously local or providerId missing
        if (existingUser.getProviderId() == null || existingUser.getProviderId().isBlank()) {
            existingUser.setProviderId(oAuth2UserInfo.getId());
            if (existingUser.getProvider() == AuthProvider.LOCAL) {
                existingUser.setProvider(AuthProvider.valueOf(registrationId.toUpperCase()));
            }
            updated = true;
        }

        if (updated) {
            return userRepository.save(existingUser);
        }

        return existingUser;
    }
}
