package com.memento3d.identity.infrastructure.security.oauth2;

import com.memento3d.identity.application.service.RefreshTokenService;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import com.memento3d.identity.infrastructure.persistence.repository.UserRepository;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.identity.infrastructure.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;

    @Mock
    private CustomOAuth2UserService customOAuth2UserService;

    @Mock
    private com.memento3d.identity.infrastructure.persistence.repository.UserConsentRepository userConsentRepository;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private OAuth2AuthenticationSuccessHandler successHandler;

    private User testUser;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(successHandler, "defaultRedirectUri", "http://localhost:3000/oauth2/redirect");

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test.oauth@example.com")
                .fullName("OAuth User")
                .role(Role.ROLE_USER)
                .build();

        userDetails = new CustomUserDetails(testUser);
    }

    @Test
    @DisplayName("determineTargetUrl builds redirect URL with token and refreshToken query parameters")
    void testDetermineTargetUrl() {
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(jwtService.generateToken(testUser)).thenReturn("mock-access-jwt");
        when(refreshTokenService.createRefreshToken(testUser)).thenReturn("mock-refresh-token");
        when(userConsentRepository.existsByUserIdAndConsentTypeAndDocumentVersionAndAcceptedTrue(any(), any(), any())).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String targetUrl = successHandler.determineTargetUrl(request, response, authentication);

        assertNotNull(targetUrl);
        assertTrue(targetUrl.startsWith("http://localhost:3000/oauth2/redirect"));
        assertTrue(targetUrl.contains("token=mock-access-jwt"));
        assertTrue(targetUrl.contains("refreshToken=mock-refresh-token"));
        assertFalse(targetUrl.contains("onboarding=true"));
    }

    @Test
    @DisplayName("determineTargetUrl handles OAuth2User/OidcUser principal without ClassCastException and appends onboarding when consent missing")
    void testDetermineTargetUrlWithOAuth2User() {
        org.springframework.security.oauth2.core.user.OAuth2User genericOAuth2User = mock(org.springframework.security.oauth2.core.user.OAuth2User.class);
        when(authentication.getPrincipal()).thenReturn(genericOAuth2User);
        when(customOAuth2UserService.processUserFromOAuth2(genericOAuth2User, "google")).thenReturn(testUser);
        when(jwtService.generateToken(testUser)).thenReturn("mock-access-jwt");
        when(refreshTokenService.createRefreshToken(testUser)).thenReturn("mock-refresh-token");
        when(userConsentRepository.existsByUserIdAndConsentTypeAndDocumentVersionAndAcceptedTrue(any(), any(), any())).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String targetUrl = successHandler.determineTargetUrl(request, response, authentication);

        assertNotNull(targetUrl);
        assertTrue(targetUrl.startsWith("http://localhost:3000/oauth2/redirect"));
        assertTrue(targetUrl.contains("token=mock-access-jwt"));
        assertTrue(targetUrl.contains("refreshToken=mock-refresh-token"));
        assertTrue(targetUrl.contains("onboarding=true"));
        verify(customOAuth2UserService).processUserFromOAuth2(genericOAuth2User, "google");
    }
}
