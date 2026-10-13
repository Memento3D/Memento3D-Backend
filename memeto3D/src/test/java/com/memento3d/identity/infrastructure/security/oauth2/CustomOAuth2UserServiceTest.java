package com.memento3d.identity.infrastructure.security.oauth2;

import com.memento3d.identity.domain.model.AuthProvider;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import com.memento3d.identity.infrastructure.persistence.repository.UserRepository;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomOAuth2UserService customOAuth2UserService;

    private ClientRegistration clientRegistration;
    private OAuth2UserRequest userRequest;

    @BeforeEach
    void setUp() {
        clientRegistration = ClientRegistration.withRegistrationId("google")
                .clientId("test-client-id")
                .clientSecret("test-client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .clientName("Google")
                .build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "mock-access-token",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );

        userRequest = new OAuth2UserRequest(clientRegistration, accessToken);
    }

    @Test
    @DisplayName("Google OAuth2 User Info extracts correct attributes")
    void testGoogleOAuth2UserInfo() {
        Map<String, Object> attributes = Map.of(
                "sub", "google-12345",
                "name", "Google User",
                "email", "google.user@example.com",
                "picture", "https://example.com/avatar.jpg"
        );

        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo("google", attributes);

        assertEquals("google-12345", userInfo.getId());
        assertEquals("Google User", userInfo.getName());
        assertEquals("google.user@example.com", userInfo.getEmail());
        assertEquals("https://example.com/avatar.jpg", userInfo.getImageUrl());
    }

    @Test
    @DisplayName("Unsupported provider throws OAuth2AuthenticationException")
    void testUnsupportedProviderThrowsException() {
        Map<String, Object> attributes = Map.of("email", "test@facebook.com");
        assertThrows(OAuth2AuthenticationException.class, () -> 
                OAuth2UserInfoFactory.getOAuth2UserInfo("facebook", attributes));
    }
}
